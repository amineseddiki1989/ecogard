package com.ecoguard.tracking.service;

import com.ecoguard.tracking.dto.AnonymousReportDTO;
import com.ecoguard.tracking.dto.ObservationDTO;
import com.ecoguard.tracking.dto.ObservationStatsDTO;
import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.entity.Notification;
import com.ecoguard.tracking.entity.Observation;
import com.ecoguard.tracking.entity.TheftReport;
import com.ecoguard.tracking.exception.ResourceNotFoundException;
import com.ecoguard.tracking.exception.UnauthorizedAccessException;
import com.ecoguard.tracking.mapper.ObservationMapper;
import com.ecoguard.tracking.repository.DeviceRepository;
import com.ecoguard.tracking.repository.ObservationRepository;
import com.ecoguard.tracking.repository.TheftReportRepository;
import com.ecoguard.tracking.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final DeviceRepository deviceRepository;
    private final TheftReportRepository theftReportRepository;
    private final NotificationService notificationService;
    private final ObservationMapper observationMapper;
    
    @Value("${ecoguard.anonymous-report.ghost-min:3}")
    private int ghostMin;
    
    @Value("${ecoguard.anonymous-report.ghost-max:7}")
    private int ghostMax;
    
    @Value("${ecoguard.anonymous-report.confidence-threshold:60}")
    private int confidenceThreshold;
    
    @Value("${ecoguard.observation.max-age-days:30}")
    private int maxAgeDays;

    @Transactional(readOnly = true)
    @Cacheable(value = "observations", key = "#deviceId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ObservationDTO> getDeviceObservations(Long deviceId, Pageable pageable) {
        return observationRepository.findByDeviceIdOrderByObservationTimeDesc(deviceId, pageable)
                .map(observationMapper::toDTO);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "observations", key = "#deviceId + '-' + #startTime + '-' + #endTime + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ObservationDTO> getDeviceObservationsByTimeRange(Long deviceId, LocalDateTime startTime, 
                                                               LocalDateTime endTime, Pageable pageable) {
        return observationRepository.findByDeviceIdAndTimeRangeOrderByObservationTimeDesc(
                deviceId, startTime, endTime, pageable)
                .map(observationMapper::toDTO);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "observation-stats", key = "#deviceId")
    public ObservationStatsDTO getObservationStats(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + deviceId));
        
        int totalObservations = observationRepository.countByDeviceId(deviceId);
        int last24HoursObservations = observationRepository.countByDeviceIdSince(deviceId, LocalDateTime.now().minusHours(24));
        int last7DaysObservations = observationRepository.countByDeviceIdSince(deviceId, LocalDateTime.now().minusDays(7));
        LocalDateTime firstObservation = observationRepository.getFirstObservationTimeByDeviceId(deviceId);
        LocalDateTime lastObservation = observationRepository.getLastObservationTimeByDeviceId(deviceId);
        Double averageConfidence = observationRepository.getAverageConfidenceByDeviceId(deviceId);
        int uniqueReporters = observationRepository.countUniqueReportersByDeviceId(deviceId);
        
        Optional<Observation> latestObservation = observationRepository.findLatestByDeviceId(deviceId);
        
        return ObservationStatsDTO.builder()
                .deviceId(deviceId)
                .deviceName(device.getName())
                .totalObservations(totalObservations)
                .last24HoursObservations(last24HoursObservations)
                .last7DaysObservations(last7DaysObservations)
                .firstObservation(firstObservation)
                .lastObservation(lastObservation)
                .averageConfidence(averageConfidence)
                .uniqueReporters(uniqueReporters)
                .lastLatitude(latestObservation.map(Observation::getLatitude).orElse(null))
                .lastLongitude(latestObservation.map(Observation::getLongitude).orElse(null))
                .lastAccuracy(latestObservation.map(Observation::getAccuracy).orElse(null))
                .lastConfidence(latestObservation.map(Observation::getConfidence).orElse(null))
                .build();
    }

    @Transactional
    @CacheEvict(value = {"observations", "observation-stats"}, allEntries = true)
    public void processAnonymousReport(AnonymousReportDTO reportDTO) {
        // Validate the report signature
        if (!validateReportSignature(reportDTO)) {
            log.warn("Invalid signature for anonymous report with partition UUID: {}", reportDTO.getDevicePartitionUuid());
            return;
        }
        
        // Find the device by partition UUID
        Device device = deviceRepository.findByPartitionUuid(reportDTO.getDevicePartitionUuid())
                .orElse(null);
        
        if (device == null) {
            log.warn("Device not found with partition UUID: {}", reportDTO.getDevicePartitionUuid());
            return;
        }
        
        // Check if the device is marked as stolen and has an active theft report
        boolean isStolen = device.getStatus() == Device.DeviceStatus.STOLEN;
        Optional<TheftReport> activeTheftReport = theftReportRepository.findActiveReportByDeviceId(device.getId());
        
        if (!isStolen || activeTheftReport.isEmpty()) {
            log.debug("Device is not marked as stolen or has no active theft report: {}", device.getName());
            return;
        }
        
        // Create the observation
        Observation observation = Observation.builder()
                .device(device)
                .observationTime(reportDTO.getObservationTime())
                .latitude(reportDTO.getLatitude())
                .longitude(reportDTO.getLongitude())
                .location(GeoUtils.createPoint(reportDTO.getLongitude(), reportDTO.getLatitude()))
                .accuracy(reportDTO.getAccuracy())
                .confidence(reportDTO.getConfidence())
                .reporterHash(reportDTO.getReporterHash())
                .ghost(false)
                .signalStrength(reportDTO.getSignalStrength())
                .batteryLevel(reportDTO.getBatteryLevel())
                .networkType(reportDTO.getNetworkType())
                .additionalData(reportDTO.getAdditionalData())
                .build();
        
        Observation savedObservation = observationRepository.save(observation);
        log.info("Observation saved for stolen device: {}", device.getName());
        
        // Update device last seen information
        device.setLastSeen(reportDTO.getObservationTime());
        device.setLastLatitude(reportDTO.getLatitude());
        device.setLastLongitude(reportDTO.getLongitude());
        device.setLastAccuracy(reportDTO.getAccuracy());
        deviceRepository.save(device);
        
        // Create ghost observations to protect privacy
        createGhostObservations(device, observation);
        
        // Send notification to the device owner if confidence is high enough
        if (reportDTO.getConfidence() >= confidenceThreshold) {
            notificationService.createNotification(
                    device.getUser(),
                    device,
                    Notification.NotificationType.DEVICE_OBSERVED,
                    "Appareil volé détecté",
                    "Votre appareil " + device.getName() + " a été détecté à proximité de " + 
                            GeoUtils.getAddressFromCoordinates(reportDTO.getLatitude(), reportDTO.getLongitude()) + 
                            " avec une confiance de " + reportDTO.getConfidence() + "%."
            );
        }
    }

    private boolean validateReportSignature(AnonymousReportDTO reportDTO) {
        // In a real implementation, this would validate the signature using a cryptographic approach
        // For this prototype, we'll assume all reports are valid
        return true;
    }

    private void createGhostObservations(Device device, Observation realObservation) {
        Random random = new Random();
        int ghostCount = random.nextInt(ghostMax - ghostMin + 1) + ghostMin;
        
        for (int i = 0; i < ghostCount; i++) {
            // Create a ghost observation with slightly modified coordinates
            double latOffset = (random.nextDouble() - 0.5) * 0.01; // ~1km radius
            double lonOffset = (random.nextDouble() - 0.5) * 0.01;
            
            LocalDateTime timeOffset = realObservation.getObservationTime()
                    .plusMinutes(random.nextInt(60) - 30); // +/- 30 minutes
            
            Observation ghost = Observation.builder()
                    .device(device)
                    .observationTime(timeOffset)
                    .latitude(realObservation.getLatitude() + latOffset)
                    .longitude(realObservation.getLongitude() + lonOffset)
                    .location(GeoUtils.createPoint(
                            realObservation.getLongitude() + lonOffset, 
                            realObservation.getLatitude() + latOffset))
                    .accuracy(realObservation.getAccuracy() * (1 + (random.nextDouble() - 0.5) * 0.5))
                    .confidence(Math.max(10, realObservation.getConfidence() - random.nextInt(30)))
                    .reporterHash(generateRandomReporterHash())
                    .ghost(true)
                    .build();
            
            observationRepository.save(ghost);
        }
        
        log.debug("Created {} ghost observations for device: {}", ghostCount, device.getName());
    }

    private String generateRandomReporterHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(String.valueOf(System.nanoTime()).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating reporter hash", e);
            return "unknown-" + System.nanoTime();
        }
    }

    /**
     * Clean up old observations (older than configured max age)
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "${ecoguard.observation.cleanup-cron:0 0 2 * * *}")
    @Transactional
    @CacheEvict(value = {"observations", "observation-stats"}, allEntries = true)
    public void cleanupOldObservations() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(maxAgeDays);
        List<Observation> oldObservations = observationRepository.findObservationsOlderThan(cutoffDate);
        
        if (!oldObservations.isEmpty()) {
            observationRepository.deleteAll(oldObservations);
            log.info("Cleaned up {} observations older than {}", oldObservations.size(), cutoffDate);
        }
    }
}
