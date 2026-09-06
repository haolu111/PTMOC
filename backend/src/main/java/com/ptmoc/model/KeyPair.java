// src/com/ptmoc/model/KeyPair.java
package com.ptmoc.model;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;

public class KeyPair {
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private SecretKey symmetricKey; // 对称密钥
    private Entity entity;
    private String identifier; // 实体标识符 (如Ser_1, Csp_2等)
    
    public KeyPair(PublicKey publicKey, PrivateKey privateKey, 
                  SecretKey symmetricKey, Entity entity, String identifier) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.symmetricKey = symmetricKey;
        this.entity = entity;
        this.identifier = identifier;
    }
    
    // Getters and setters
    public PublicKey getPublicKey() { return publicKey; }
    public PrivateKey getPrivateKey() { return privateKey; }
    public SecretKey getSymmetricKey() { return symmetricKey; }
    public Entity getEntity() { return entity; }
    public String getIdentifier() { return identifier; }
}