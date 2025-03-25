package com.example.springai.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.example.springai.vapid.PushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Transaction;

@Component("valkeyStore")
public class ValkeySubscriptionStore implements SubscriptionStore {
    private final JedisSentinelPool jedisPool;
    private final ObjectMapper objectMapper;
    private static final String KEY_PREFIX = "subscriptions:";
    private static final String MASTER_NAME = "mymaster";

    @Value("${aws.valkey.sentinels}")
    private String[] sentinelNodes;

    @Value("${aws.valkey.password}")
    private String password;

    @Value("${aws.valkey.ssl.enabled:true}")
    private boolean sslEnabled;

    public ValkeySubscriptionStore() {
        this.objectMapper = new ObjectMapper();
        GenericObjectPoolConfig<Jedis> poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        
        Set<HostAndPort> sentinels = Arrays.stream(sentinelNodes)
            .map(node -> {
                String[] parts = node.split(":");
                return new HostAndPort(parts[0], Integer.parseInt(parts[1]));
            })
            .collect(Collectors.toSet());

        // Configure master client with SSL
        JedisClientConfig masterClientConfig = DefaultJedisClientConfig.builder()
            .ssl(sslEnabled)
            .password(password)
            .connectionTimeoutMillis(2000)
            .socketTimeoutMillis(1000)
            .build();

        // Configure sentinel client with SSL
        JedisClientConfig sentinelClientConfig = DefaultJedisClientConfig.builder()
            .ssl(sslEnabled)
            .connectionTimeoutMillis(2000)
            .socketTimeoutMillis(1000)
            .build();

        this.jedisPool = new JedisSentinelPool(MASTER_NAME, sentinels, poolConfig, 
                                    masterClientConfig, sentinelClientConfig);

    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> addSubscription(String email, PushSubscription subscription) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = KEY_PREFIX + email;
                jedis.watch(key);  // Enable optimistic locking
                
                String currentValue = jedis.get(key);
                List<PushSubscription> subscriptions;
                
                if (currentValue == null) {
                    subscriptions = new ArrayList<>();
                } else {
                    subscriptions = objectMapper.readValue(
                        currentValue,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PushSubscription.class)
                    );
                }

                boolean exists = subscriptions.stream()
                    .anyMatch(sub -> sub.getEndpoint().equals(subscription.getEndpoint()));

                if (!exists) {
                    subscriptions.add(subscription);
                    String newValue = objectMapper.writeValueAsString(subscriptions);
                    
                    Transaction transaction = jedis.multi();  // Start transaction
                    transaction.set(key, newValue);
                    transaction.exec();   // Execute transaction
                } else {
                    jedis.unwatch();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to add subscription", e);
            }
        });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<List<PushSubscription>> getSubscriptionsByEmail(String email) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String value = jedis.get(KEY_PREFIX + email);
                if (value == null) {
                    return new ArrayList<>();
                }
                return objectMapper.readValue(
                    value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PushSubscription.class)
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to get subscriptions", e);
            }
        });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<List<PushSubscription>> getAllSubscriptions() {
        return null;
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> removeSubscription(String email, String endpoint) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = KEY_PREFIX + email;
                String value = jedis.get(key);
                
                if (value != null) {
                    List<PushSubscription> subscriptions = objectMapper.readValue(
                        value,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PushSubscription.class)
                    );

                    List<PushSubscription> newSubs = subscriptions.stream()
                        .filter(sub -> !sub.getEndpoint().equals(endpoint))
                        .collect(Collectors.toList());

                    if (newSubs.isEmpty()) {
                        jedis.del(key);
                    } else {
                        jedis.set(key, objectMapper.writeValueAsString(newSubs));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to remove subscription", e);
            }
        });
    }

    @PreDestroy
    public void cleanup() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}