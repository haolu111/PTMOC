package com.ptmoc.core;

import com.ptmoc.model.Entity;
import com.ptmoc.model.KeyPair;
import com.ptmoc.model.Polynomial;
import com.ptmoc.model.PublicParameters;
import com.ptmoc.util.CryptoUtil;
import com.ptmoc.util.MathUtil;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CryptoModule {
    private BaseModule baseModule;

    public CryptoModule(BaseModule baseModule) {
        this.baseModule = baseModule;
    }

    // PTMOC.Enc - sender side threshold sharing and share encryption
    public Map<String, Object> encrypt(String identifier, int k, Map<Integer, BigInteger> messages) {
        try {
            if (k < 2) {
                throw new IllegalArgumentException("Threshold k must be at least 2");
            }

            PublicParameters ppr = baseModule.getPublicParameters();
            KeyPair senderKeyPair = baseModule.getKeyPair(identifier);
            KeyPair serverKeyPair = baseModule.getKeyPair(Entity.SERVER);
            KeyPair cspKeyPair = baseModule.getKeyPair(Entity.CSP);

            if (ppr == null) {
                throw new IllegalStateException("Public parameters have not been initialized");
            }
            if (senderKeyPair == null || !senderKeyPair.getEntity().equals(Entity.SENDER)) {
                throw new RuntimeException("Invalid sender identifier: " + identifier);
            }
            if (serverKeyPair == null) {
                throw new IllegalStateException("Server key pair is required before encryption");
            }
            if (cspKeyPair == null) {
                throw new IllegalStateException("CSP key pair is required before encryption");
            }

            SecureRandom random = new SecureRandom();
            BigInteger p0 = BigInteger.probablePrime(ppr.getLambda(), random);

            Map<String, Map<String, Object>> serverMetadata = new LinkedHashMap<>();
            Map<Integer, BigInteger> shareP = new HashMap<>();
            Map<Integer, BigInteger> shareQ = new HashMap<>();
            Map<Integer, BigInteger> shareR = new HashMap<>();
            Map<String, Object> firstServerMeta = null;

            for (int l = 1; l <= k; l++) {
                BigInteger p = BigInteger.probablePrime(ppr.getEta(), random);
                BigInteger q = BigInteger.probablePrime(ppr.getEta(), random);
                BigInteger h = BigInteger.probablePrime(ppr.getEta(), random);
                BigInteger n = p.multiply(q);
                BigInteger t = n.multiply(h);
                BigInteger r = randomNonZero(random, p0);

                shareP.put(l, p);
                shareQ.put(l, q);
                shareR.put(l, r);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("C_i_l_1", CryptoUtil.trapdoorEncryptBigInteger(serverKeyPair.getPublicKey(), r));
                metadata.put("C_i_l_2", CryptoUtil.trapdoorEncryptBigIntegerPair(cspKeyPair.getPublicKey(), p, q));
                metadata.put("N_i_l", n);
                metadata.put("T_i_l", t);
                serverMetadata.put("server_" + l, metadata);

                if (firstServerMeta == null) {
                    firstServerMeta = metadata;
                }
            }

            Map<Integer, Map<String, BigInteger>> encryptedMessages = new LinkedHashMap<>();
            Map<Integer, Map<String, Map<String, String>>> auxiliaryShares = new LinkedHashMap<>();

            for (Map.Entry<Integer, BigInteger> entry : messages.entrySet()) {
                int messageIndex = entry.getKey();
                BigInteger messageValue = MathUtil.normalizeMod(entry.getValue(), p0);

                Polynomial polynomial = MathUtil.generatePolynomial(messageValue, k - 1, p0);
                Map<Integer, BigInteger> shares = new LinkedHashMap<>();
                for (int l = 1; l <= k; l++) {
                    shares.put(l, polynomial.evaluate(BigInteger.valueOf(l)).mod(p0));
                }

                Map<String, BigInteger> encryptedMainShares = new LinkedHashMap<>();
                Map<String, Map<String, String>> messageAuxiliaryShares = new LinkedHashMap<>();

                for (int l = 1; l <= k; l++) {
                    Map<String, Object> metadata = serverMetadata.get("server_" + l);
                    BigInteger n = (BigInteger) metadata.get("N_i_l");
                    BigInteger t = (BigInteger) metadata.get("T_i_l");
                    BigInteger p = shareP.get(l);
                    BigInteger q = shareQ.get(l);
                    BigInteger r = shareR.get(l);

                    BigInteger kRandom = new BigInteger(Math.max(1, ppr.getLambda() - 1), random);
                    BigInteger value = shares.get(l).add(kRandom.multiply(p0));

                    encryptedMainShares.put(
                        "C_i_i_prime_l_" + l,
                        encryptShare(value, p, q, n, t, r, random)
                    );

                    Map<String, String> serverVisibleShares = new LinkedHashMap<>();
                    for (int lPrime = 1; lPrime <= k; lPrime++) {
                        if (lPrime == l) {
                            continue;
                        }

                        serverVisibleShares.put(
                            "share_" + lPrime,
                            CryptoUtil.symmetricEncryptToBase64(
                                serverKeyPair.getSymmetricKey(),
                                shares.get(lPrime).toString()
                            )
                        );
                    }
                    messageAuxiliaryShares.put("server_" + l, serverVisibleShares);
                }

                encryptedMessages.put(messageIndex, encryptedMainShares);
                auxiliaryShares.put(messageIndex, messageAuxiliaryShares);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("senderId", identifier);
            result.put("thresholdK", k);
            result.put("p_0_i", p0);
            result.put("N_i_l", firstServerMeta == null ? BigInteger.ZERO : firstServerMeta.get("N_i_l"));
            result.put("T_i_l", firstServerMeta == null ? BigInteger.ZERO : firstServerMeta.get("T_i_l"));
            result.put("serverMetadata", serverMetadata);
            result.put("encryptedMessages", encryptedMessages);
            result.put("auxiliaryShares", auxiliaryShares);
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed for sender: " + identifier, e);
        }
    }

    // PTMOC.Dec - receiver restores the encrypted-domain evaluation result
    public BigInteger decrypt(String identifier, Map<String, BigInteger> ciphertexts) {
        try {
            KeyPair receiverKeyPair = baseModule.getKeyPair(identifier);

            if (receiverKeyPair == null || !receiverKeyPair.getEntity().equals(Entity.RECEIVER)) {
                throw new RuntimeException("Invalid receiver identifier: " + identifier);
            }

            BigInteger p0 = ciphertexts.get("p_0_i");
            int threshold = ciphertexts.get("thresholdK").intValue();
            int degree = ciphertexts.get("degree").intValue();

            BigInteger result = BigInteger.ZERO;
            for (int l = 1; l <= threshold; l++) {
                BigInteger partialCipher = ciphertexts.get("C_F_" + l);
                BigInteger encR = ciphertexts.get("C_ser_rec_" + l);
                BigInteger encRPrime = ciphertexts.get("C_csp_rec_" + l);

                if (partialCipher == null || encR == null || encRPrime == null) {
                    continue;
                }

                BigInteger r = CryptoUtil.trapdoorDecryptBigInteger(receiverKeyPair.getPrivateKey(), encR);
                BigInteger rPrime = CryptoUtil.trapdoorDecryptBigInteger(receiverKeyPair.getPrivateKey(), encRPrime);
                BigInteger denominator = r.modPow(BigInteger.valueOf(degree), p0).multiply(rPrime).mod(p0);
                BigInteger contribution = partialCipher.multiply(denominator.modInverse(p0)).mod(p0);
                result = result.add(contribution).mod(p0);
            }

            return MathUtil.normalizeMod(result, p0);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed for receiver: " + identifier, e);
        }
    }

    private BigInteger encryptShare(
        BigInteger value,
        BigInteger p,
        BigInteger q,
        BigInteger n,
        BigInteger t,
        BigInteger r,
        SecureRandom random
    ) {
        BigInteger termP = q.multiply(MathUtil.modInverse(q, p)).multiply(value.mod(p));
        BigInteger termQ = p.multiply(MathUtil.modInverse(p, q)).multiply(value.mod(q));
        BigInteger noise = new BigInteger(Math.max(1, t.bitLength() - 1), random).mod(t);
        return MathUtil.normalizeMod(r.multiply(termP.add(termQ)).add(noise.multiply(n)), t);
    }

    private BigInteger randomNonZero(SecureRandom random, BigInteger modulus) {
        BigInteger candidate;
        do {
            candidate = new BigInteger(Math.max(2, modulus.bitLength()), random).mod(modulus);
        } while (candidate.equals(BigInteger.ZERO));
        return candidate;
    }
}
