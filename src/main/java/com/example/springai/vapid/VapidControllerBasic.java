package com.example.springai.vapid;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@RestController
public class VapidControllerBasic {
    private final List<PushSubscription> subscriptions = new ArrayList<>();
    private final PushService pushService;
    private final String publicKey;
    private final String privateKey;

    public VapidControllerBasic() throws GeneralSecurityException {

        Security.addProvider(new BouncyCastleProvider());

        VapidGenerator generator = new VapidGenerator();
        this.publicKey = generator.getPublicKey();
        this.privateKey = generator.getPrivateKey();
        
        this.pushService = new PushService(publicKey, privateKey, "mailto:ssonnad@infomedia.com.au");
    }

    @GetMapping("/vapidb/keys")
    public Map<String, String> getVapidKeys() throws NoSuchAlgorithmException {
        Map<String, String> keys = new HashMap<>();
        keys.put("publicKey", publicKey);
        keys.put("privateKey", privateKey);
        return keys;
    }

        @PostMapping("/vapidb/subscribe")
    public ResponseEntity<String> subscribe(@RequestBody PushSubscription subscription) {
        subscriptions.add(subscription);
        return ResponseEntity.ok("Subscription successful");
    }

    @PostMapping("/vapidb/push")
    public ResponseEntity<String> sendPushNotification(@RequestBody String message) {
        for (PushSubscription subscription : subscriptions) {
            try {
                // In a real implementation, you would use a WebPush library to send 
                // the notification using the subscription details and VAPID keys
                sendPushNotification(subscription, message);
            } catch (Exception e) {
                return ResponseEntity.internalServerError()
                    .body("Failed to send push notification: " + e.getMessage());
            }
        }
        return ResponseEntity.ok("Push notifications sent successfully");
    }

    private void sendPushNotification(PushSubscription subscription, String message) 
            throws GeneralSecurityException, IOException, JoseException, ExecutionException, InterruptedException {
        // byte[] decodedPublicKey = Base64.getDecoder().decode(subscription.getKeys().getP256dh());
        // byte[] decodedAuthKey = Base64.getDecoder().decode(subscription.getKeys().getAuth());

        // Implement actual push notification sending logic here

        // You would typically use a library like web-push-java
        System.out.println("Sending push notification to: " + subscription.getEndpoint());
        System.out.println("Message: " + message);

        Notification notification = new Notification(
            subscription.getEndpoint(),
            subscription.getKeys().getP256dh(),
            subscription.getKeys().getAuth(),
            message.getBytes()
        );

        pushService.send(notification);
    }
}