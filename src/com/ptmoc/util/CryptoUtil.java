// src/com/ptmoc/util/CryptoUtil.java
package com.ptmoc.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.util.Base64;

public class CryptoUtil {
    
    // 使用陷门置换加密
    public static byte[] trapdoorEncrypt(PublicKey publicKey, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Trapdoor encryption failed", e);
        }
    }
    
    // 使用陷门置换解密
    public static byte[] trapdoorDecrypt(PrivateKey privateKey, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Trapdoor decryption failed", e);
        }
    }
    
    // 对称加密
    public static byte[] symmetricEncrypt(SecretKey key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Symmetric encryption failed", e);
        }
    }
    
    // 对称解密
    public static byte[] symmetricDecrypt(SecretKey key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Symmetric decryption failed", e);
        }
    }

    public static BigInteger trapdoorEncryptBigInteger(PublicKey publicKey, BigInteger value) {
        return trapdoorEncryptString(publicKey, value.toString());
    }

    public static BigInteger trapdoorDecryptBigInteger(PrivateKey privateKey, BigInteger ciphertext) {
        return new BigInteger(trapdoorDecryptString(privateKey, ciphertext));
    }

    public static BigInteger trapdoorEncryptString(PublicKey publicKey, String value) {
        byte[] encrypted = trapdoorEncrypt(publicKey, value.getBytes(StandardCharsets.UTF_8));
        return new BigInteger(1, encrypted);
    }

    public static String trapdoorDecryptString(PrivateKey privateKey, BigInteger ciphertext) {
        byte[] plaintext = trapdoorDecrypt(privateKey, toFixedLength(ciphertext, rsaByteLength(privateKey)));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public static String symmetricEncryptToBase64(SecretKey key, String value) {
        byte[] encrypted = symmetricEncrypt(key, value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String symmetricDecryptFromBase64(SecretKey key, String value) {
        byte[] decoded = Base64.getDecoder().decode(value);
        return new String(symmetricDecrypt(key, decoded), StandardCharsets.UTF_8);
    }

    public static BigInteger trapdoorEncryptBigIntegerPair(PublicKey publicKey, BigInteger first, BigInteger second) {
        byte[] firstBytes = unsignedBytes(first);
        byte[] secondBytes = unsignedBytes(second);
        ByteBuffer buffer = ByteBuffer.allocate(8 + firstBytes.length + secondBytes.length);
        buffer.putInt(firstBytes.length);
        buffer.put(firstBytes);
        buffer.putInt(secondBytes.length);
        buffer.put(secondBytes);
        return new BigInteger(1, trapdoorEncrypt(publicKey, buffer.array()));
    }

    public static BigInteger[] trapdoorDecryptBigIntegerPair(PrivateKey privateKey, BigInteger ciphertext) {
        byte[] plaintext = trapdoorDecrypt(privateKey, toFixedLength(ciphertext, rsaByteLength(privateKey)));
        ByteBuffer buffer = ByteBuffer.wrap(plaintext);
        int firstLength = buffer.getInt();
        byte[] firstBytes = new byte[firstLength];
        buffer.get(firstBytes);
        int secondLength = buffer.getInt();
        byte[] secondBytes = new byte[secondLength];
        buffer.get(secondBytes);
        return new BigInteger[] {
            new BigInteger(1, firstBytes),
            new BigInteger(1, secondBytes)
        };
    }

    private static int rsaByteLength(Key key) {
        if (!(key instanceof RSAKey)) {
            throw new IllegalArgumentException("Key is not an RSA key");
        }
        RSAKey rsaKey = (RSAKey) key;
        return (rsaKey.getModulus().bitLength() + 7) / 8;
    }

    private static byte[] toFixedLength(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();

        if (bytes.length == length) {
            return bytes;
        }

        if (bytes.length == length + 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[length];
            System.arraycopy(bytes, 1, trimmed, 0, length);
            return trimmed;
        }

        if (bytes.length > length) {
            throw new IllegalArgumentException("Ciphertext length exceeds RSA modulus size");
        }

        byte[] padded = new byte[length];
        System.arraycopy(bytes, 0, padded, length - bytes.length, bytes.length);
        return padded;
    }

    private static byte[] unsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
