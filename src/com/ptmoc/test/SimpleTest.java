package com.ptmoc.test;

import com.ptmoc.core.BaseModule;
import com.ptmoc.core.CryptoModule;
import com.ptmoc.core.EvalModule;
import com.ptmoc.model.Entity;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleTest {
    public static void main(String[] args) {
        System.out.println("=== PTMOC End-to-End Demo ===");

        try {
            BaseModule baseModule = new BaseModule();
            baseModule.setup(256);

            baseModule.keyGen(Entity.SENDER, "TestSender");
            baseModule.keyGen(Entity.SERVER, "TestServer");
            baseModule.keyGen(Entity.CSP, "TestCSP");
            baseModule.keyGen(Entity.RECEIVER, "TestReceiver");

            CryptoModule cryptoModule = new CryptoModule(baseModule);
            EvalModule evalModule = new EvalModule(baseModule);

            Map<Integer, BigInteger> messages = new LinkedHashMap<>();
            messages.put(1, new BigInteger("12"));
            messages.put(2, new BigInteger("7"));
            messages.put(3, new BigInteger("5"));

            String functionDefinition = "3*x1^2*x2 + 4*x3 + 6";
            BigInteger expected =
                BigInteger.valueOf(3).multiply(messages.get(1).pow(2)).multiply(messages.get(2))
                    .add(BigInteger.valueOf(4).multiply(messages.get(3)))
                    .add(BigInteger.valueOf(6));

            Map<String, Object> encryptionResult = cryptoModule.encrypt("TestSender", 3, messages);
            Map<String, BigInteger> evaluationResult = evalModule.evaluate(
                "TestServer",
                "TestCSP",
                encryptionResult,
                functionDefinition
            );
            BigInteger decryptedResult = cryptoModule.decrypt("TestReceiver", evaluationResult);
            BigInteger p0 = (BigInteger) encryptionResult.get("p_0_i");
            BigInteger expectedModP0 = expected.mod(p0);

            System.out.println("Function: " + functionDefinition);
            System.out.println("Messages: " + messages);
            System.out.println("Expected plaintext result: " + expected);
            System.out.println("Expected mod p0 result: " + expectedModP0);
            System.out.println("Decrypted evaluation result: " + decryptedResult);
            System.out.println("p0: " + p0);

            if (!expectedModP0.equals(decryptedResult)) {
                throw new RuntimeException("PTMOC evaluation mismatch");
            }

            System.out.println("=== PTMOC End-to-End Demo Passed ===");
        } catch (Exception e) {
            System.out.println("PTMOC demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
