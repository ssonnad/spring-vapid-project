package com.example.springai.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AsyncHttpClientTest {

    @LocalServerPort
    private int port;

    @Test
    void testAsyncSubscribeWithCallback() {
        // Create async HTTP client
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

        // Send async request with callback
        CompletableFuture<HttpResponse<String>> futureResponse = client
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                // Verify response status
                assertEquals(200, response.statusCode());
                System.out.println("Response received: " + response.body());
                return response;
            })
            .exceptionally(throwable -> {
                fail("Request failed: " + throwable.getMessage());
                return null;
            });

        // Wait for completion to prevent test from ending too early
        futureResponse.join();
    }

    @Test
    void testInvalidEmailWithCallback() {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        String jsonBody = """
            {
                "endpoint": "https://test.com/push",
                "keys": {
                    "p256dh": "test_key",
                    "auth": "test_auth"
                }
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/vapid/subscribe/invalid-email"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        CompletableFuture<HttpResponse<String>> futureResponse = client
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                // Verify error response
                assertEquals(400, response.statusCode());
                assertTrue(response.body().contains("Invalid email format"));
                return response;
            })
            .exceptionally(throwable -> {
                fail("Request failed: " + throwable.getMessage());
                return null;
            });

        futureResponse.join();
    }
}