package com.ptmoc.core;

import com.ptmoc.model.Entity;
import com.ptmoc.model.KeyPair;
import com.ptmoc.util.CryptoUtil;
import com.ptmoc.util.MathUtil;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EvalModule {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("x(\\d+)(?:\\^(\\d+))?");

    private BaseModule baseModule;

    public EvalModule(BaseModule baseModule) {
        this.baseModule = baseModule;
    }

    // PTMOC.Eval - 服务器与 CSP 协同完成密文域多项式评估
    public Map<String, BigInteger> evaluate(
        String serverId,
        String cspId,
        Map<String, Object> ciphertexts,
        String functionDefinition
    ) {
        try {
            KeyPair serverKeyPair = baseModule.getKeyPair(serverId);
            KeyPair cspKeyPair = baseModule.getKeyPair(cspId);
            KeyPair receiverKeyPair = baseModule.getKeyPair(Entity.RECEIVER);

            if (serverKeyPair == null || !serverKeyPair.getEntity().equals(Entity.SERVER)) {
                throw new RuntimeException("Invalid server identifier: " + serverId);
            }
            if (cspKeyPair == null || !cspKeyPair.getEntity().equals(Entity.CSP)) {
                throw new RuntimeException("Invalid CSP identifier: " + cspId);
            }
            if (receiverKeyPair == null) {
                throw new RuntimeException("Receiver key pair is required before evaluation");
            }

            BigInteger p0 = (BigInteger) ciphertexts.get("p_0_i");
            int threshold = readThreshold(ciphertexts.get("thresholdK"));

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> serverMetadata =
                (Map<String, Map<String, Object>>) ciphertexts.get("serverMetadata");
            @SuppressWarnings("unchecked")
            Map<Integer, Map<String, BigInteger>> encryptedMessages =
                (Map<Integer, Map<String, BigInteger>>) ciphertexts.get("encryptedMessages");
            @SuppressWarnings("unchecked")
            Map<Integer, Map<String, Map<String, String>>> auxiliaryShares =
                (Map<Integer, Map<String, Map<String, String>>>) ciphertexts.get("auxiliaryShares");

            List<PolynomialTerm> terms = parseFunction(functionDefinition);
            int degree = 0;
            for (PolynomialTerm term : terms) {
                degree = Math.max(degree, term.degree());
            }

            validateVariables(terms, encryptedMessages);

            Map<Integer, List<PolynomialTerm>> assignments = assignTerms(terms, threshold);
            Map<String, BigInteger> evaluationResult = new LinkedHashMap<>();
            evaluationResult.put("p_0_i", p0);
            evaluationResult.put("thresholdK", BigInteger.valueOf(threshold));
            evaluationResult.put("degree", BigInteger.valueOf(degree));

            SecureRandom random = new SecureRandom();

            for (int l = 1; l <= threshold; l++) {
                Map<String, Object> metadata = serverMetadata.get("server_" + l);
                if (metadata == null) {
                    throw new RuntimeException("Missing metadata for server_" + l);
                }

                BigInteger r = CryptoUtil.trapdoorDecryptBigInteger(
                    serverKeyPair.getPrivateKey(),
                    (BigInteger) metadata.get("C_i_l_1")
                );
                BigInteger[] pq = CryptoUtil.trapdoorDecryptBigIntegerPair(
                    cspKeyPair.getPrivateKey(),
                    (BigInteger) metadata.get("C_i_l_2")
                );
                BigInteger p = pq[0];
                BigInteger q = pq[1];

                BigInteger n = p.multiply(q);
                BigInteger metadataN = (BigInteger) metadata.get("N_i_l");
                BigInteger metadataT = (BigInteger) metadata.get("T_i_l");

                if (!n.equals(metadataN)) {
                    throw new RuntimeException("Metadata integrity check failed for server_" + l);
                }

                Map<Integer, BigInteger> encryptedSecrets = reconstructEncryptedSecrets(
                    l,
                    threshold,
                    p0,
                    r,
                    n,
                    encryptedMessages,
                    auxiliaryShares,
                    serverKeyPair
                );

                BigInteger partialCipher = BigInteger.ZERO;
                for (PolynomialTerm term : assignments.getOrDefault(l, Collections.emptyList())) {
                    BigInteger encryptedTerm = MathUtil.normalizeMod(term.coefficient, p0);

                    for (Map.Entry<Integer, Integer> exponentEntry : term.exponents.entrySet()) {
                        BigInteger encryptedSecret = encryptedSecrets.get(exponentEntry.getKey());
                        encryptedTerm = encryptedTerm.multiply(
                            encryptedSecret.modPow(BigInteger.valueOf(exponentEntry.getValue()), p0)
                        ).mod(p0);
                    }

                    int paddingDegree = degree - term.degree();
                    if (paddingDegree > 0) {
                        encryptedTerm = encryptedTerm.multiply(
                            r.modPow(BigInteger.valueOf(paddingDegree), p0)
                        ).mod(p0);
                    }

                    partialCipher = partialCipher.add(encryptedTerm).mod(p0);
                }

                BigInteger rPrime = randomNonZero(random, p0);
                BigInteger cspWrappedCipher = partialCipher.multiply(rPrime).mod(p0);

                evaluationResult.put(
                    "C_ser_rec_" + l,
                    CryptoUtil.trapdoorEncryptBigInteger(receiverKeyPair.getPublicKey(), r)
                );
                evaluationResult.put(
                    "C_csp_rec_" + l,
                    CryptoUtil.trapdoorEncryptBigInteger(receiverKeyPair.getPublicKey(), rPrime)
                );
                evaluationResult.put("C_F_" + l, cspWrappedCipher);
            }

            return evaluationResult;

        } catch (Exception e) {
            throw new RuntimeException("Evaluation failed", e);
        }
    }

    private Map<Integer, BigInteger> reconstructEncryptedSecrets(
        int selectedServer,
        int threshold,
        BigInteger p0,
        BigInteger r,
        BigInteger n,
        Map<Integer, Map<String, BigInteger>> encryptedMessages,
        Map<Integer, Map<String, Map<String, String>>> auxiliaryShares,
        KeyPair serverKeyPair
    ) {
        Map<Integer, BigInteger> encryptedSecrets = new HashMap<>();

        for (Map.Entry<Integer, Map<String, BigInteger>> entry : encryptedMessages.entrySet()) {
            int messageIndex = entry.getKey();
            BigInteger normalizedSelectedShare = entry.getValue()
                .get("C_i_i_prime_l_" + selectedServer)
                .mod(n)
                .mod(p0);

            BigInteger encryptedSecret = normalizedSelectedShare.multiply(
                MathUtil.lagrangeCoefficientAtZero(selectedServer, threshold, p0)
            ).mod(p0);

            Map<String, String> visibleShares = auxiliaryShares.get(messageIndex).get("server_" + selectedServer);
            for (int lPrime = 1; lPrime <= threshold; lPrime++) {
                if (lPrime == selectedServer) {
                    continue;
                }

                BigInteger shareValue = new BigInteger(
                    CryptoUtil.symmetricDecryptFromBase64(
                        serverKeyPair.getSymmetricKey(),
                        visibleShares.get("share_" + lPrime)
                    )
                );
                BigInteger coefficient = MathUtil.lagrangeCoefficientAtZero(lPrime, threshold, p0);
                encryptedSecret = encryptedSecret.add(
                    r.multiply(coefficient).multiply(shareValue)
                ).mod(p0);
            }

            encryptedSecrets.put(messageIndex, MathUtil.normalizeMod(encryptedSecret, p0));
        }

        return encryptedSecrets;
    }

    private List<PolynomialTerm> parseFunction(String functionDefinition) {
        if (functionDefinition == null || functionDefinition.trim().isEmpty()) {
            throw new IllegalArgumentException("Function definition cannot be empty");
        }

        String normalized = functionDefinition.replace(" ", "");
        normalized = normalized.replace("-", "+-");
        if (normalized.startsWith("+-")) {
            normalized = normalized.substring(1);
        }

        String[] rawTerms = normalized.split("\\+");
        List<PolynomialTerm> terms = new ArrayList<>();

        for (String rawTerm : rawTerms) {
            if (rawTerm == null || rawTerm.isEmpty()) {
                continue;
            }

            BigInteger coefficient = BigInteger.ONE;
            if (rawTerm.startsWith("-")) {
                coefficient = coefficient.negate();
                rawTerm = rawTerm.substring(1);
            }
            Map<Integer, Integer> exponents = new LinkedHashMap<>();
            String[] factors = rawTerm.split("\\*");

            for (String factor : factors) {
                if (factor == null || factor.isEmpty()) {
                    continue;
                }

                if (factor.matches("-?\\d+")) {
                    coefficient = coefficient.multiply(new BigInteger(factor));
                    continue;
                }

                Matcher matcher = VARIABLE_PATTERN.matcher(factor);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Unsupported factor in function definition: " + factor);
                }

                int variableIndex = Integer.parseInt(matcher.group(1));
                int exponent = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
                exponents.put(variableIndex, exponents.getOrDefault(variableIndex, 0) + exponent);
            }

            terms.add(new PolynomialTerm(coefficient, exponents));
        }

        if (terms.isEmpty()) {
            throw new IllegalArgumentException("No valid polynomial term found in function definition");
        }

        return terms;
    }

    private void validateVariables(List<PolynomialTerm> terms, Map<Integer, Map<String, BigInteger>> encryptedMessages) {
        for (PolynomialTerm term : terms) {
            for (Integer variableIndex : term.exponents.keySet()) {
                if (!encryptedMessages.containsKey(variableIndex)) {
                    throw new IllegalArgumentException(
                        "Function references x" + variableIndex + ", but no such encrypted message exists"
                    );
                }
            }
        }
    }

    private Map<Integer, List<PolynomialTerm>> assignTerms(List<PolynomialTerm> terms, int threshold) {
        Map<Integer, List<PolynomialTerm>> assignments = new HashMap<>();
        for (int l = 1; l <= threshold; l++) {
            assignments.put(l, new ArrayList<>());
        }

        for (int index = 0; index < terms.size(); index++) {
            int assignedServer = (index % threshold) + 1;
            assignments.get(assignedServer).add(terms.get(index));
        }

        return assignments;
    }

    private int readThreshold(Object thresholdValue) {
        if (thresholdValue instanceof Integer) {
            return (Integer) thresholdValue;
        }
        if (thresholdValue instanceof BigInteger) {
            return ((BigInteger) thresholdValue).intValue();
        }
        if (thresholdValue instanceof Number) {
            return ((Number) thresholdValue).intValue();
        }
        throw new IllegalArgumentException("Unsupported threshold value: " + thresholdValue);
    }

    private BigInteger randomNonZero(SecureRandom random, BigInteger modulus) {
        BigInteger candidate;
        do {
            candidate = new BigInteger(Math.max(2, modulus.bitLength()), random).mod(modulus);
        } while (candidate.equals(BigInteger.ZERO));
        return candidate;
    }

    private static class PolynomialTerm {
        private BigInteger coefficient;
        private Map<Integer, Integer> exponents;

        private PolynomialTerm(BigInteger coefficient, Map<Integer, Integer> exponents) {
            this.coefficient = coefficient;
            this.exponents = exponents;
        }

        private int degree() {
            int degree = 0;
            for (Integer exponent : exponents.values()) {
                degree += exponent;
            }
            return degree;
        }
    }
}
