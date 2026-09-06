// src/com/ptmoc/util/MathUtil.java
package com.ptmoc.util;

import java.math.BigInteger;

import java.security.SecureRandom;
import java.util.Random;
import com.ptmoc.model.Polynomial;

public class MathUtil {
    
    // 生成多项式
    public static Polynomial generatePolynomial(BigInteger constant, int degree, BigInteger modulus) {
        BigInteger[] coefficients = new BigInteger[degree + 1];
        coefficients[0] = constant;
        
        Random random = new SecureRandom();
        for (int i = 1; i <= degree; i++) {
            byte[] bytes = new byte[modulus.bitLength() / 8];
            random.nextBytes(bytes);
            coefficients[i] = new BigInteger(1, bytes).mod(modulus);
        }
        
        return new Polynomial(coefficients);
    }
    
    // 模逆元计算
    public static BigInteger modInverse(BigInteger a, BigInteger m) {
        return a.modInverse(m);
    }

    public static BigInteger normalizeMod(BigInteger value, BigInteger modulus) {
        BigInteger normalized = value.mod(modulus);
        return normalized.signum() < 0 ? normalized.add(modulus) : normalized;
    }

    public static BigInteger lagrangeCoefficientAtZero(int index, int threshold, BigInteger modulus) {
        BigInteger numerator = BigInteger.ONE;
        BigInteger denominator = BigInteger.ONE;

        for (int current = 1; current <= threshold; current++) {
            if (current == index) {
                continue;
            }

            numerator = numerator.multiply(BigInteger.valueOf(-current)).mod(modulus);
            denominator = denominator.multiply(BigInteger.valueOf(index - current)).mod(modulus);
        }

        return normalizeMod(numerator.multiply(modInverse(normalizeMod(denominator, modulus), modulus)), modulus);
    }
    
    // 中国剩余定理
    public static BigInteger crt(BigInteger[] values, BigInteger[] moduli) {
        BigInteger M = BigInteger.ONE;
        for (BigInteger modulus : moduli) {
            M = M.multiply(modulus);
        }
        
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < values.length; i++) {
            BigInteger M_i = M.divide(moduli[i]);
            BigInteger M_i_inv = M_i.modInverse(moduli[i]);
            result = result.add(values[i].multiply(M_i).multiply(M_i_inv));
        }
        
        return result.mod(M);
    }
}
