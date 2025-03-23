package com.example.springai.vapid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {
    private final ConcurrentHashMap<String, List<PushSubscription>> subscriptionsByEmail = new ConcurrentHashMap<>();

    public void addSubscription(String email, PushSubscription subscription) {
        subscriptionsByEmail.compute(email, (key, existingList) -> {
            List<PushSubscription> subscriptions = existingList;
            if (subscriptions == null) {
                subscriptions = new CopyOnWriteArrayList<>();
            }
            
            boolean exists = subscriptions.stream()
                .anyMatch(sub -> sub.getEndpoint().equals(subscription.getEndpoint()));
            
            if (!exists) {
                subscriptions.add(subscription);
                System.out.println("Added new subscription for " + email);
            } else {
                System.out.println("Subscription already exists for " + email);
            }
            
            return subscriptions;
        });
    }

    public List<PushSubscription> getSubscriptionsByEmail(String email) {
        List<PushSubscription> subscriptions = subscriptionsByEmail.getOrDefault(
            email, 
            Collections.emptyList()
        );
        System.out.println("Returning " + subscriptions.size() + " subscriptions for " + email);
        return new ArrayList<>(subscriptions); // Return a defensive copy
    }

    public List<PushSubscription> getAllSubscriptions() {
        return subscriptionsByEmail.values().stream()
            .flatMap(List::stream)
            .toList(); // Returns an unmodifiable list
    }

    public void removeSubscription(String email, String endpoint) {
        subscriptionsByEmail.computeIfPresent(email, (key, subs) -> {
            subs.removeIf(sub -> sub.getEndpoint().equals(endpoint));
            return subs.isEmpty() ? null : subs;
        });
    }
}