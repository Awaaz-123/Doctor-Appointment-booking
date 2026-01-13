package com.careconnect.controller;

import com.careconnect.service.ConversationService;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*") // Allows your HTML to talk to Java
public class ChatController {

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/chat")
    public String chat(
            @RequestHeader(value = "X-Session-ID", defaultValue = "unknown") String sessionId, 
            @RequestBody String message
    ) {
        // Now the sessionId comes from the frontend and stays the same!
        return conversationService.handleMessage(sessionId, message);
    }
}