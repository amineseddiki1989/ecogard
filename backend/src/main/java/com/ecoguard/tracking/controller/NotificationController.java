package com.ecoguard.tracking.controller;

import com.ecoguard.tracking.dto.NotificationDTO;
import com.ecoguard.tracking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getUserNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Getting notifications for user ID: {}, unreadOnly: {}", userId, unreadOnly);
        Page<NotificationDTO> notifications = notificationService.getUserNotifications(userId, unreadOnly, pageable);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> getNotificationById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Getting notification with ID: {} for user ID: {}", id, userId);
        NotificationDTO notification = notificationService.getNotificationById(id, userId);
        return ResponseEntity.ok(notification);
    }

    @GetMapping("/count/unread")
    public ResponseEntity<Integer> getUnreadNotificationCount(@AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Getting unread notification count for user ID: {}", userId);
        int count = notificationService.getUnreadNotificationCount(userId);
        return ResponseEntity.ok(count);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markNotificationAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Marking notification with ID: {} as read for user ID: {}", id, userId);
        NotificationDTO notification = notificationService.markNotificationAsRead(id, userId);
        return ResponseEntity.ok(notification);
    }

    @PutMapping("/read/all")
    public ResponseEntity<Void> markAllNotificationsAsRead(@AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Marking all notifications as read for user ID: {}", userId);
        notificationService.markAllNotificationsAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Deleting notification with ID: {} for user ID: {}", id, userId);
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.noContent().build();
    }
}
