package com.example.springai.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.springai.vapid.VapidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Transaction;

/*
 * The VAPID keys serve as our server's identity:
 * Public key: Identifies our application to browsers and push services
 * Private key: Proves we own that identity through JWT signing
 * This is different from the user's keys (p256dh and auth) which are used for message encryption. 
 * VAPID keys are for authentication/identification, while user keys are for encryption.
 */
@Service
public class VapidKeyService {
    private static final String VAPID_KEY_PREFIX = "vapid:keys";
    private final JedisSentinelPool jedisPool;
    private final ObjectMapper objectMapper;

    public VapidKeyService(JedisSentinelPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    @Async("taskExecutor")
    public CompletableFuture<Map<String, String>> getOrGenerateKeys() {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.watch(VAPID_KEY_PREFIX);  // Watch the key for changes
                String storedKeys = jedis.get(VAPID_KEY_PREFIX);
                
                if (storedKeys != null) {
                    return objectMapper.readValue(storedKeys, Map.class);
                }

                // Generate new keys if none exist
                VapidGenerator generator = new VapidGenerator();
                Map<String, String> keys = new HashMap<>();
                keys.put("publicKey", generator.getPublicKey());
                keys.put("privateKey", generator.getPrivateKey());

                // Use transaction to ensure atomic operation
                Transaction transaction = jedis.multi();
                try {
                    transaction.set(VAPID_KEY_PREFIX, objectMapper.writeValueAsString(keys));
                    if (transaction.exec() == null) {
                        // Transaction failed, retry recursively
                        return getOrGenerateKeys().get(); // Note: blocking call in async context
                    }
                } catch (Exception e) {
                    transaction.discard();
                    throw e;
                }
                
                return keys;
            } catch (Exception e) {
                throw new RuntimeException("Failed to manage VAPID keys", e);
            }
        });
    }

    @Async("taskExecutor")
    public CompletableFuture<Optional<Map<String, String>>> getKeys() {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String storedKeys = jedis.get(VAPID_KEY_PREFIX);
                if (storedKeys == null) {
                    return Optional.empty();
                }
                return Optional.of(objectMapper.readValue(storedKeys, Map.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to retrieve VAPID keys", e);
            }
        });
    }
}