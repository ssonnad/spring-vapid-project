package com.example.springai.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.example.springai.vapid.PushSubscription;

public interface SubscriptionStore {
    CompletableFuture<Void> addSubscription(String email, PushSubscription subscription);
    CompletableFuture<List<PushSubscription>> getSubscriptionsByEmail(String email);
    CompletableFuture<List<PushSubscription>> getAllSubscriptions();
    CompletableFuture<Void> removeSubscription(String email, String endpoint);
}