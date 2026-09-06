// src/com/ptmoc/model/Polynomial.java
package com.ptmoc.model;

import java.math.BigInteger;
import java.util.Arrays;

public class Polynomial {
    private BigInteger[] coefficients;
    
    public Polynomial(BigInteger[] coefficients) {
        this.coefficients = Arrays.copyOf(coefficients, coefficients.length);
    }
    
    public BigInteger evaluate(BigInteger x) {
        BigInteger result = BigInteger.ZERO;
        for (int i = coefficients.length - 1; i >= 0; i--) {
            result = result.multiply(x).add(coefficients[i]);
        }
        return result;
    }
    
    public int degree() {
        return coefficients.length - 1;
    }
    
    public BigInteger[] getCoefficients() {
        return Arrays.copyOf(coefficients, coefficients.length);
    }
}