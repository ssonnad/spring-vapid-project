package com.example.springai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    // private final ChatClient chatClient;

    // public AiController(ChatClient chatClient) {
    //     this.chatClient = chatClient;
    // }

    @GetMapping("/ai/chat")
    public String chat(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return "Response: " + message;
    }
}