package com.ecoguard.tracking.service;

import com.ecoguard.tracking.dto.NotificationDTO;
import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.entity.Notification;
import com.ecoguard.tracking.entity.User;
import com.ecoguard.tracking.exception.ResourceNotFoundException;
import com.ecoguard.tracking.exception.UnauthorizedAccessException;
import com.ecoguard.tracking.mapper.NotificationMapper;
import com.ecoguard.tracking.repository.NotificationRepository;
import com.ecoguard.tracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getUserNotifications(Long userId, boolean unreadOnly, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        Page<Notification> notifications;
        if (unreadOnly) {
            notifications = notificationRepository.findByUserAndReadOrderByCreatedAtDesc(user, false, pageable);
        } else {
            notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        }
        
        return notifications.map(notificationMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public NotificationDTO getNotificationById(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        
        // Check if the notification belongs to the user
        if (!notification.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to access this notification");
        }
        
        return notificationMapper.toDTO(notification);
    }

    @Transactional(readOnly = true)
    public int getUnreadNotificationCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public NotificationDTO createNotification(User user, Device device, Notification.NotificationType type, 
                                             String title, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .device(device)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .build();
        
        if (type == Notification.NotificationType.DEVICE_OBSERVED) {
            notification.setActionUrl("/tracking/" + device.getId());
            notification.setActionText("Voir le tracking");
        } else if (type == Notification.NotificationType.DEVICE_STOLEN) {
            notification.setActionUrl("/tracking/" + device.getId());
            notification.setActionText("Voir le tracking");
        }
        
        Notification savedNotification = notificationRepository.save(notification);
        log.info("Notification created for user: {} with type: {}", user.getEmail(), type);
        
        // Send push notification if user has FCM tokens
        if (user.getFcmTokens() != null && !user.getFcmTokens().isEmpty()) {
            Map<String, String> data = Map.of(
                    "notificationId", savedNotification.getId().toString(),
                    "type", savedNotification.getType().toString(),
                    "deviceId", device != null ? device.getId().toString() : ""
            );
            
            fcmService.sendMulticastMessage(
                    user.getFcmTokens().stream().collect(Collectors.toList()),
                    title,
                    message,
                    data
            );
        }
        
        return notificationMapper.toDTO(savedNotification);
    }

    @Transactional
    public NotificationDTO markNotificationAsRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        
        // Check if the notification belongs to the user
        if (!notification.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to access this notification");
        }
        
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        
        Notification updatedNotification = notificationRepository.save(notification);
        log.debug("Notification marked as read: {}", id);
        
        return notificationMapper.toDTO(updatedNotification);
    }

    @Transactional
    public void markAllNotificationsAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        log.debug("All notifications marked as read for user: {}", userId);
    }

    @Transactional
    public void deleteNotification(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        
        // Check if the notification belongs to the user
        if (!notification.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to delete this notification");
        }
        
        notificationRepository.delete(notification);
        log.debug("Notification deleted: {}", id);
    }

    /**
     * Clean up old read notifications (older than 30 days)
     * Runs daily at 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        notificationRepository.deleteOldReadNotifications(cutoffDate);
        log.info("Cleaned up old read notifications older than {}", cutoffDate);
    }
}
