package com.example.springai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.example.springai.vapid.PushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;

@Component
public class AwsElastiCacheStore implements SubscriptionStore {
    private final JedisCluster jedisCluster;
    private final ObjectMapper objectMapper;
    private static final String KEY_PREFIX = "subscriptions:";

    @Value("${aws.elasticache.endpoint}")
    private String elasticacheEndpoint;

    @Value("${aws.elasticache.port:6379}")
    private int port;

    public AwsElastiCacheStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        
        HostAndPort hostAndPort = new HostAndPort(elasticacheEndpoint, port);
        this.jedisCluster = new JedisCluster(hostAndPort);

    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> addSubscription(String email, PushSubscription subscription) {
        return CompletableFuture.runAsync(() -> {
            try {
                String key = KEY_PREFIX + email;
                String currentValue = jedisCluster.get(key);
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
                    jedisCluster.set(key, objectMapper.writeValueAsString(subscriptions));
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
            try {
                String value = jedisCluster.get(KEY_PREFIX + email);
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
            try {
                String key = KEY_PREFIX + email;
                String value = jedisCluster.get(key);
                
                if (value != null) {
                    List<PushSubscription> subscriptions = objectMapper.readValue(
                        value,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PushSubscription.class)
                    );

                    List<PushSubscription> newSubs = subscriptions.stream()
                        .filter(sub -> !sub.getEndpoint().equals(endpoint))
                        .collect(Collectors.toList());

                    if (newSubs.isEmpty()) {
                        jedisCluster.del(key);
                    } else {
                        jedisCluster.set(key, objectMapper.writeValueAsString(newSubs));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to remove subscription", e);
            }
        });
    }

    @PreDestroy
    public void cleanup() {
        if (jedisCluster != null) {
            jedisCluster.close();
        }
    }
}