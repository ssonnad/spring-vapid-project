package com.example.springai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.example.springai.vapid.PushSubscription;

@Component("redisStore")
public class RedisSubscriptionStore implements SubscriptionStore {
    private final RedisTemplate<String, List<PushSubscription>> redisTemplate;

    // namespace for subscription related keys
    private static final String KEY_PREFIX = "subscriptions:";

    public RedisSubscriptionStore(RedisTemplate<String, List<PushSubscription>> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> addSubscription(String email, PushSubscription subscription) {
        return CompletableFuture.runAsync(() -> {
            String key = KEY_PREFIX + email;
            
            SessionCallback<Void> callback = new SessionCallback<Void>() {
                @SuppressWarnings("unchecked")
                @Override
                public Void execute(RedisOperations operations) {
                    operations.watch(key);
                    
                    List<PushSubscription> subscriptions = (List<PushSubscription>) operations.opsForValue().get(key);
                    if (subscriptions == null) {
                        subscriptions = new ArrayList<>();
                    }
                    
                    boolean exists = subscriptions.stream()
                        .anyMatch(sub -> sub.getEndpoint().equals(subscription.getEndpoint()));
                    
                    if (!exists) {
                        subscriptions.add(subscription);
                        operations.multi();
                        operations.opsForValue().set(key, subscriptions);
                        operations.exec();
                    } else {
                        operations.unwatch();
                    }
                    
                    return null;
                }
            };
            
            try {
                redisTemplate.execute(callback);
            } catch (Exception e) {
                throw new RuntimeException("Failed to add subscription", e);
            }
        });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<List<PushSubscription>> getSubscriptionsByEmail(String email) {
        return CompletableFuture.supplyAsync(() -> {
            String key = KEY_PREFIX + email;
            List<PushSubscription> subscriptions = redisTemplate.opsForValue().get(key);
            return subscriptions != null ? new ArrayList<>(subscriptions) : new ArrayList<>();
        });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<List<PushSubscription>> getAllSubscriptions() {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");

            return keys.stream()
                .map(key -> redisTemplate.opsForValue().get(key))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        });
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> removeSubscription(String email, String endpoint) {
        return CompletableFuture.runAsync(() -> {
            String key = KEY_PREFIX + email;
            List<PushSubscription> subscriptions = redisTemplate.opsForValue().get(key);
            
            if (subscriptions != null) {
                List<PushSubscription> newSubs = subscriptions.stream()
                    .filter(sub -> !sub.getEndpoint().equals(endpoint))
                    .collect(Collectors.toList());
                    
                if (newSubs.isEmpty()) {
                    redisTemplate.delete(key);
                } else {
                    redisTemplate.opsForValue().set(key, newSubs);
                }
            }
        });
    }
}