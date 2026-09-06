// src/com/ptmoc/model/PublicParameters.java
package com.ptmoc.model;

import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.math.BigInteger;

public class PublicParameters {
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private AlgorithmParameterSpec seParams;
    private BigInteger p0;
    private int lambda;
    private int eta;
    // Add polynomial field if needed
    private Polynomial polynomial;
    
    // Existing constructor
    public PublicParameters(PublicKey publicKey, PrivateKey privateKey, 
                           AlgorithmParameterSpec seParams, BigInteger p0, 
                           int lambda, int eta) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.seParams = seParams;
        this.p0 = p0;
        this.lambda = lambda;
        this.eta = eta;
    }
    
    // Constructor with polynomial if needed
    public PublicParameters(PublicKey publicKey, PrivateKey privateKey, 
                           AlgorithmParameterSpec seParams, BigInteger p0, 
                           int lambda, int eta, Polynomial polynomial) {
        this(publicKey, privateKey, seParams, p0, lambda, eta);
        this.polynomial = polynomial;
    }
    
    // Getters and setters
    public PublicKey getPublicKey() { return publicKey; }
    public PrivateKey getPrivateKey() { return privateKey; }
    public AlgorithmParameterSpec getSeParams() { return seParams; }
    public BigInteger getP0() { return p0; }
    public int getLambda() { return lambda; }
    public int getEta() { return eta; }
    public Polynomial getPolynomial() { return polynomial; }
    public void setPolynomial(Polynomial polynomial) { this.polynomial = polynomial; }
}