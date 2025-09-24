package com.ecoguard.tracking.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FCMService {

    /**
     * Send FCM message to a specific device
     */
    public void sendMessage(String token, String title, String body, Map<String, String> data) {
        Message message = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .setToken(token)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("Successfully sent message: {}", response);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to send FCM message", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Send FCM message to multiple devices
     */
    public void sendMulticastMessage(List<String> tokens, String title, String body, Map<String, String> data) {
        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .addAllTokens(tokens)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendMulticastAsync(message).get();
            log.info("Successfully sent message to {} devices", response.getSuccessCount());
            
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        log.error("Failed to send message to token {}: {}", 
                                tokens.get(i), responses.get(i).getException().getMessage());
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to send FCM multicast message", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Send FCM message to a topic
     */
    public void sendTopicMessage(String topic, String title, String body, Map<String, String> data) {
        Message message = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .setTopic(topic)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("Successfully sent message to topic {}: {}", topic, response);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to send FCM topic message", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Subscribe tokens to a topic
     */
    public void subscribeToTopic(List<String> tokens, String topic) {
        try {
            TopicManagementResponse response = FirebaseMessaging.getInstance()
                    .subscribeToTopicAsync(tokens, topic).get();
            
            log.info("Successfully subscribed {} tokens to topic {}", 
                    tokens.size() - response.getFailureCount(), topic);
            
            if (response.getFailureCount() > 0) {
                log.error("Failed to subscribe {} tokens to topic {}", 
                        response.getFailureCount(), topic);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to subscribe to topic", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Unsubscribe tokens from a topic
     */
    public void unsubscribeFromTopic(List<String> tokens, String topic) {
        try {
            TopicManagementResponse response = FirebaseMessaging.getInstance()
                    .unsubscribeFromTopicAsync(tokens, topic).get();
            
            log.info("Successfully unsubscribed {} tokens from topic {}", 
                    tokens.size() - response.getFailureCount(), topic);
            
            if (response.getFailureCount() > 0) {
                log.error("Failed to unsubscribe {} tokens from topic {}", 
                        response.getFailureCount(), topic);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to unsubscribe from topic", e);
            Thread.currentThread().interrupt();
        }
    }
}
