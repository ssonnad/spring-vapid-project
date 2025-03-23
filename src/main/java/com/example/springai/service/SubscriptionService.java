package com.example.springai.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.example.springai.vapid.PushSubscription;

@Service
public class SubscriptionService {
    private final SubscriptionStore subscriptionStore;

    public SubscriptionService(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    public CompletableFuture<Void> addSubscription(String email, PushSubscription subscription) {
        return subscriptionStore.addSubscription(email, subscription);
    }

    public CompletableFuture<List<PushSubscription>> getSubscriptionsByEmail(String email) {
        return subscriptionStore.getSubscriptionsByEmail(email);
    }

    public CompletableFuture<List<PushSubscription>> getAllSubscriptions() {
        return subscriptionStore.getAllSubscriptions();
    }

    public CompletableFuture<Void> removeSubscription(String email, String endpoint) {
        return subscriptionStore.removeSubscription(email, endpoint);
    }
}