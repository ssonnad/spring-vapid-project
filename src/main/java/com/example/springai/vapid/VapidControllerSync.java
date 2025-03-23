package com.example.springai.vapid;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@RestController
@RequestMapping("/vapidsync")
public class VapidControllerSync {
    private final PushService pushService;
    private final String publicKey;
    private final String privateKey;
    private final SubscriptionService subscriptionService;

    public VapidControllerSync(SubscriptionService subscriptionService) throws GeneralSecurityException {
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
    public ResponseEntity<String> subscribe(
            @PathVariable String email,
            @RequestBody PushSubscription subscription) {
        subscriptionService.addSubscription(email, subscription);
        return ResponseEntity.ok("Subscription successful for " + email);
    }

    @PostMapping("/push")
    public ResponseEntity<String> sendPushNotification(@RequestBody Map<String, Object> payload) {
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

            int sentCount = 0;
            List<PushSubscription> targetSubscriptions = targetEmails.stream()
                .flatMap(email -> subscriptionService.getSubscriptionsByEmail(email).stream())
                .toList();

            if (targetSubscriptions.isEmpty()) {
                return ResponseEntity.ok("No subscriptions found for the specified emails");
            }

            for (PushSubscription subscription : targetSubscriptions) {
                try {
                    sendPushNotification(subscription, message);
                    sentCount++;
                } catch (Exception e) {
                    System.err.println("Failed to send to " + subscription.getEndpoint() + ": " + e.getMessage());
                }
            }

            return ResponseEntity.ok("Push notifications sent to " + sentCount + " subscribers");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Failed to process push notification: " + e.getMessage());
        }
    }

    @DeleteMapping("/unsubscribe/{email}")
    public ResponseEntity<String> unsubscribe(
            @PathVariable String email,
            @RequestBody Map<String, String> payload) {
        String endpoint = payload.get("endpoint");
        if (endpoint == null) {
            return ResponseEntity.badRequest().body("Endpoint is required");
        }
        
        subscriptionService.removeSubscription(email, endpoint);
        return ResponseEntity.ok("Unsubscribed successfully");
    }

    private void sendPushNotification(PushSubscription subscription, String message) 
            throws GeneralSecurityException, IOException, JoseException, ExecutionException, InterruptedException {
        Notification notification = new Notification(
            subscription.getEndpoint(),
            subscription.getKeys().getP256dh(),
            subscription.getKeys().getAuth(),
            message.getBytes()
        );

        pushService.send(notification);
    }
}