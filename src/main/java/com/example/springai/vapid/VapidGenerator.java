package com.example.springai.vapid;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.Base64;

public class VapidGenerator {
    private final KeyPair keyPair;
    
    public VapidGenerator() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256);
        this.keyPair = keyPairGenerator.generateKeyPair();
    }
    
    public String getPublicKey() {
        ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
        ECPoint point = pub.getW();
        
        // Convert to uncompressed point format
        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04; // Uncompressed point indicator
        
        byte[] x = point.getAffineX().toByteArray();
        byte[] y = point.getAffineY().toByteArray();
        
    // Copy X coordinate (starting at index 1)
    System.arraycopy(x, Math.max(0, x.length - 32), 
                    uncompressed, 1 + Math.max(0, 32 - x.length), 
                    Math.min(32, x.length));
    
    // Copy Y coordinate (starting at index 33)
    System.arraycopy(y, Math.max(0, y.length - 32), 
                    uncompressed, 33 + Math.max(0, 32 - y.length), 
                    Math.min(32, y.length));
        
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
    }
    
    public String getPrivateKey() {
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
        // Extract the actual 32-byte private key from the ASN.1 structure
        byte[] rawPrivateKey = new byte[32];
        System.arraycopy(privateKeyBytes, privateKeyBytes.length - 32, rawPrivateKey, 0, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawPrivateKey);
    }


    // public String getPublicKey() {
    //     return Base64.getUrlEncoder().withoutPadding()
    //             .encodeToString(keyPair.getPublic().getEncoded());
    // }
    
    // public String getPrivateKey() {
    //     return Base64.getUrlEncoder().withoutPadding()
    //             .encodeToString(keyPair.getPrivate().getEncoded());
    // }
}