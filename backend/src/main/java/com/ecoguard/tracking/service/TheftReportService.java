package com.ecoguard.tracking.service;

import com.ecoguard.tracking.dto.TheftReportDTO;
import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.entity.Notification;
import com.ecoguard.tracking.entity.TheftReport;
import com.ecoguard.tracking.entity.User;
import com.ecoguard.tracking.exception.ResourceNotFoundException;
import com.ecoguard.tracking.exception.UnauthorizedAccessException;
import com.ecoguard.tracking.mapper.TheftReportMapper;
import com.ecoguard.tracking.repository.DeviceRepository;
import com.ecoguard.tracking.repository.TheftReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TheftReportService {

    private final TheftReportRepository theftReportRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final NotificationService notificationService;
    private final FCMService fcmService;
    private final TheftReportMapper theftReportMapper;

    @Transactional(readOnly = true)
    public List<TheftReportDTO> getUserTheftReports(Long userId) {
        return theftReportRepository.findByUserId(userId).stream()
                .map(theftReportMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TheftReportDTO getTheftReportById(Long id, Long userId) {
        TheftReport theftReport = theftReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theft report not found with id: " + id));
        
        // Check if the user owns this report
        if (!theftReport.getDevice().getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to access this theft report");
        }
        
        return theftReportMapper.toDTO(theftReport);
    }

    @Transactional(readOnly = true)
    public TheftReportDTO getActiveTheftReportByDeviceId(Long deviceId) {
        return theftReportRepository.findActiveReportByDeviceId(deviceId)
                .map(theftReportMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("No active theft report found for device id: " + deviceId));
    }

    @Transactional
    public TheftReportDTO createTheftReport(TheftReportDTO theftReportDTO, Long userId) {
        Device device = deviceRepository.findById(theftReportDTO.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + theftReportDTO.getDeviceId()));
        
        // Check if the user owns this device
        if (!device.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to report this device as stolen");
        }
        
        // Check if there's already an active theft report for this device
        Optional<TheftReport> existingReport = theftReportRepository.findActiveReportByDeviceId(device.getId());
        if (existingReport.isPresent()) {
            throw new IllegalStateException("An active theft report already exists for this device");
        }
        
        // Update device status to STOLEN
        deviceService.updateDeviceStatus(device.getId(), Device.DeviceStatus.STOLEN);
        
        // Create theft report
        TheftReport theftReport = TheftReport.builder()
                .device(device)
                .reportedAt(LocalDateTime.now())
                .theftDate(theftReportDTO.getTheftDate())
                .theftLocation(theftReportDTO.getTheftLocation())
                .theftLatitude(theftReportDTO.getTheftLatitude())
                .theftLongitude(theftReportDTO.getTheftLongitude())
                .circumstances(theftReportDTO.getCircumstances())
                .policeReportNumber(theftReportDTO.getPoliceReportNumber())
                .policeReportFiled(theftReportDTO.isPoliceReportFiled())
                .status(TheftReport.TheftReportStatus.ACTIVE)
                .alertSent(false)
                .build();
        
        TheftReport savedReport = theftReportRepository.save(theftReport);
        log.info("Theft report created for device: {} by user: {}", device.getName(), device.getUser().getEmail());
        
        // Send notification to the user
        notificationService.createNotification(
                device.getUser(),
                device,
                Notification.NotificationType.DEVICE_STOLEN,
                "Appareil déclaré volé",
                "Votre appareil " + device.getName() + " a été déclaré volé. Le système de tracking communautaire a été activé."
        );
        
        // Send FCM alert to all devices
        sendCommunityAlert(device);
        
        return theftReportMapper.toDTO(savedReport);
    }

    @Transactional
    public TheftReportDTO updateTheftReport(Long id, TheftReportDTO theftReportDTO, Long userId) {
        TheftReport theftReport = theftReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theft report not found with id: " + id));
        
        // Check if the user owns this report
        if (!theftReport.getDevice().getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to update this theft report");
        }
        
        // Only allow updates if the report is still active
        if (theftReport.getStatus() != TheftReport.TheftReportStatus.ACTIVE) {
            throw new IllegalStateException("Cannot update a resolved or cancelled theft report");
        }
        
        theftReport.setTheftLocation(theftReportDTO.getTheftLocation());
        theftReport.setTheftLatitude(theftReportDTO.getTheftLatitude());
        theftReport.setTheftLongitude(theftReportDTO.getTheftLongitude());
        theftReport.setCircumstances(theftReportDTO.getCircumstances());
        theftReport.setPoliceReportNumber(theftReportDTO.getPoliceReportNumber());
        theftReport.setPoliceReportFiled(theftReportDTO.isPoliceReportFiled());
        
        TheftReport updatedReport = theftReportRepository.save(theftReport);
        log.info("Theft report updated for device: {}", theftReport.getDevice().getName());
        
        return theftReportMapper.toDTO(updatedReport);
    }

    @Transactional
    public TheftReportDTO resolveTheftReport(Long id, String resolutionNotes, Long userId) {
        TheftReport theftReport = theftReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theft report not found with id: " + id));
        
        // Check if the user owns this report
        if (!theftReport.getDevice().getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to resolve this theft report");
        }
        
        // Only allow resolution if the report is still active
        if (theftReport.getStatus() != TheftReport.TheftReportStatus.ACTIVE) {
            throw new IllegalStateException("Cannot resolve a report that is not active");
        }
        
        // Update theft report status
        theftReport.setStatus(TheftReport.TheftReportStatus.RESOLVED);
        theftReport.setResolvedAt(LocalDateTime.now());
        theftReport.setResolutionNotes(resolutionNotes);
        
        // Update device status to RECOVERED
        deviceService.updateDeviceStatus(theftReport.getDevice().getId(), Device.DeviceStatus.RECOVERED);
        
        TheftReport resolvedReport = theftReportRepository.save(theftReport);
        log.info("Theft report resolved for device: {}", theftReport.getDevice().getName());
        
        // Send notification to the user
        notificationService.createNotification(
                theftReport.getDevice().getUser(),
                theftReport.getDevice(),
                Notification.NotificationType.DEVICE_RECOVERED,
                "Appareil récupéré",
                "Votre appareil " + theftReport.getDevice().getName() + " a été marqué comme récupéré."
        );
        
        return theftReportMapper.toDTO(resolvedReport);
    }

    @Transactional
    public TheftReportDTO cancelTheftReport(Long id, Long userId) {
        TheftReport theftReport = theftReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theft report not found with id: " + id));
        
        // Check if the user owns this report
        if (!theftReport.getDevice().getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to cancel this theft report");
        }
        
        // Only allow cancellation if the report is still active
        if (theftReport.getStatus() != TheftReport.TheftReportStatus.ACTIVE) {
            throw new IllegalStateException("Cannot cancel a report that is not active");
        }
        
        // Update theft report status
        theftReport.setStatus(TheftReport.TheftReportStatus.CANCELLED);
        
        // Update device status back to ACTIVE
        deviceService.updateDeviceStatus(theftReport.getDevice().getId(), Device.DeviceStatus.ACTIVE);
        
        TheftReport cancelledReport = theftReportRepository.save(theftReport);
        log.info("Theft report cancelled for device: {}", theftReport.getDevice().getName());
        
        return theftReportMapper.toDTO(cancelledReport);
    }

    private void sendCommunityAlert(Device device) {
        // Send FCM message to all devices
        String title = "Alerte appareil volé";
        String body = "Un appareil a été signalé volé dans votre région. Votre application EcoGuard va vérifier si elle l'a détecté récemment.";
        
        // Include device partition UUID in the data payload
        fcmService.sendTopicMessage("stolen_devices", title, body, 
                java.util.Map.of("devicePartitionUuid", device.getPartitionUuid()));
        
        // Update the theft report to mark alert as sent
        theftReportRepository.findActiveReportByDeviceId(device.getId()).ifPresent(report -> {
            report.setAlertSent(true);
            report.setAlertSentAt(LocalDateTime.now());
            theftReportRepository.save(report);
        });
        
        log.info("Community alert sent for stolen device: {}", device.getName());
    }
}
