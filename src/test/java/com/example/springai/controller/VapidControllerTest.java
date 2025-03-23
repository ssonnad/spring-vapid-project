package com.example.springai.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.example.springai.service.SubscriptionService;
import com.example.springai.vapid.PushSubscription;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VapidControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    void testAsyncSubscribe() throws Exception {
        // Create test subscription
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint("https://test.com/push");
        subscription.setKeys(new PushSubscription.Keys("test_p256dh", "test_auth"));

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create request entity
        HttpEntity<PushSubscription> request = new HttpEntity<>(subscription, headers);

        // Send async request
        CompletableFuture<ResponseEntity<String>> future = 
            CompletableFuture.supplyAsync(() -> 
                restTemplate.exchange(
                    "http://localhost:" + port + "/vapid/subscribe/test@example.com",
                    HttpMethod.POST,
                    request,
                    String.class
                )
            );

        // Wait for response with timeout
        ResponseEntity<String> response = future.get(5, TimeUnit.SECONDS);

        // Assert response
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(Optional.ofNullable(response.getBody())
                .map(body -> body.contains("Successfully subscribed"))
                .orElse(false));

        // Verify subscription was stored
        assertTrue(
            subscriptionService.getSubscriptionsByEmail("test@example.com")
                .get(5, TimeUnit.SECONDS)
                .stream()
                .anyMatch(sub -> sub.getEndpoint().equals("https://test.com/push"))
        );
    }

    @Test
    void testAsyncSubscribeWithInvalidEmail() throws Exception {
        // Create test subscription
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint("https://test.com/push");
        subscription.setKeys(new PushSubscription.Keys("test_p256dh", "test_auth"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PushSubscription> request = new HttpEntity<>(subscription, headers);

        CompletableFuture<ResponseEntity<String>> future = 
            CompletableFuture.supplyAsync(() -> 
                restTemplate.exchange(
                    "http://localhost:" + port + "/vapid/subscribe/invalid-email",
                    HttpMethod.POST,
                    request,
                    String.class
                )
            );

        ResponseEntity<String> response = future.get(5, TimeUnit.SECONDS);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid email format", response.getBody());
    }

    //# Run specific test
    //# ./mvnw test -Dtest=VapidControllerTest#testSubscribeWithSyncClient
    @Test
    void testSubscribeWithSyncClient() throws Exception {
        // Create HTTP client with timeout
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        // Prepare subscription payload
        String jsonBody = """
            {
                "endpoint": "https://test.com/push",
                "keys": {
                    "p256dh": "test_key",
                    "auth": "test_auth"
                }
            }
            """;

        // Create request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/vapid/subscribe/test@example.com"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        // Send request and get response
        HttpResponse<String> response = client
                .send(request, HttpResponse.BodyHandlers.ofString());

        // Assert response
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Successfully subscribed"));

        // Test invalid email
        HttpRequest invalidRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/vapid/subscribe/invalid-email"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> invalidResponse = client.send(invalidRequest, 
            HttpResponse.BodyHandlers.ofString());

        assertEquals(400, invalidResponse.statusCode());
        assertTrue(invalidResponse.body().contains("Invalid email format"));
    }
}