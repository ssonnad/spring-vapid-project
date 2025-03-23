package com.example.springai.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springai.service.SubscriptionService;
import com.example.springai.vapid.PushSubscription;
import com.example.springai.vapid.VapidGenerator;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@RestController
@RequestMapping("/vapid")
public class VapidController {
    private final PushService pushService;
    private final String publicKey;
    private final String privateKey;
    private final SubscriptionService subscriptionService;

    public VapidController(SubscriptionService subscriptionService) throws GeneralSecurityException {
        this.subscriptionService = subscriptionService;
        
        Security.addProvider(new BouncyCastleProvider());

        VapidGenerator generator = new VapidGenerator();
        this.publicKey = generator.getPublicKey();
        this.privateKey = generator.getPrivateKey();
        
        this.pushService = new PushService(publicKey, privateKey, "mailto:ssonnad@infomedia.com.au");
    }

    @GetMapping("/keys")
    public Map<String, String> getVapidKeys() {
        Map<String, String> keys = new HashMap<>();
        keys.put("publicKey", publicKey);
        return keys; // Don't expose private key
    }

    @PostMapping("/subscribe/{email}")
    @Async("pushNotificationExecutor")
    public CompletableFuture<ResponseEntity<String>> subscribe(
            @PathVariable String email,
            @RequestBody PushSubscription subscription) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate email
                if (!isValidEmail(email)) {
                    return ResponseEntity.badRequest()
                        .body("Invalid email format");
                }
    
                // Validate subscription
                if (subscription == null || subscription.getEndpoint() == null) {
                    return ResponseEntity.badRequest()
                        .body("Invalid subscription endpoint");
                }
    
                subscriptionService.addSubscription(email, subscription);
                
                String successMessage = String.format("Successfully subscribed email: %s", email);
                return ResponseEntity.ok(successMessage);
    
            } catch (IllegalArgumentException e) {
                // Handle validation errors
                return ResponseEntity.badRequest()
                    .body("Subscription failed: " + e.getMessage());
                
            } catch (Exception e) {
                // Handle unexpected errors
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Subscription failed: " + e.getMessage());
            }
        });
    }

    @PostMapping("/push")
    @Async("pushNotificationExecutor")
    public CompletableFuture<ResponseEntity<String>> sendPushNotification(
            @RequestBody Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String message = (String) payload.get("message");
                @SuppressWarnings("unchecked")
                List<String> targetEmails = (List<String>) payload.get("emails");
                
                if (message == null) {
                    return ResponseEntity.badRequest().body("Message is required");
                }

                if (targetEmails == null || targetEmails.isEmpty()) {
                    return ResponseEntity.badRequest().body("Target emails are required");
                }

                // Create AtomicInteger for thread-safe counting
                final AtomicInteger sentCount = new AtomicInteger(0);
                
                // Get target subscriptions
                List<PushSubscription> targetSubscriptions = targetEmails.stream()
                    .map(email -> subscriptionService.getSubscriptionsByEmail(email))
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

                if (targetSubscriptions.isEmpty()) {
                    return ResponseEntity.ok("No subscriptions found for the specified emails");
                }

                // Create final variables for lambda
                final String finalMessage = message;

                // Process notifications in parallel
                List<CompletableFuture<Void>> futures = targetSubscriptions.stream()
                    .map(subscription -> CompletableFuture.runAsync(() -> {
                        try {
                            sendPushNotification(subscription, finalMessage);
                            sentCount.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("Failed to send to " + subscription.getEndpoint() + ": " + e.getMessage());
                        }
                    }))
                    .toList();

                // Wait for all notifications to complete
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

                return ResponseEntity.ok("Push notifications sent to " + sentCount.get() + " subscribers");
            } catch (Exception e) {
                return ResponseEntity.internalServerError()
                    .body("Failed to process push notification: " + e.getMessage());
            }
        });
    }

    @DeleteMapping("/unsubscribe/{email}")
    @Async("pushNotificationExecutor")
    public CompletableFuture<ResponseEntity<String>> unsubscribe(
            @PathVariable String email,
            @RequestBody Map<String, String> payload) {
        return CompletableFuture.supplyAsync(() -> {
            String endpoint = payload.get("endpoint");
            if (endpoint == null) {
                return ResponseEntity.badRequest().body("Endpoint is required");
            }
            
            subscriptionService.removeSubscription(email, endpoint);
            return ResponseEntity.ok("Unsubscribed successfully");
        });
    }

    @Async("pushNotificationExecutor")
    protected CompletableFuture<Void> sendPushNotification(
            PushSubscription subscription, 
            String message) {
        return CompletableFuture.runAsync(() -> {
            try {
                Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getKeys().getP256dh(),
                    subscription.getKeys().getAuth(),
                    message.getBytes()
                );
                pushService.send(notification);
            } catch (IOException | InterruptedException | GeneralSecurityException | ExecutionException | JoseException e) {
                throw new RuntimeException("Failed to send notification", e);
            }
        });
    }

    private boolean isValidEmail(String email) {
        return email != null && 
               email.matches("^[A-Za-z0-9+_.-]+@(.+)$") && 
               email.length() <= 254;
    }
}