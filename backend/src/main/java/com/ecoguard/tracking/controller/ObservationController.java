package com.ecoguard.tracking.controller;

import com.ecoguard.tracking.dto.AnonymousReportDTO;
import com.ecoguard.tracking.dto.ObservationDTO;
import com.ecoguard.tracking.dto.ObservationStatsDTO;
import com.ecoguard.tracking.service.ObservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ObservationController {

    private final ObservationService observationService;

    @GetMapping("/observations/device/{deviceId}")
    public ResponseEntity<Page<ObservationDTO>> getDeviceObservations(
            @PathVariable Long deviceId,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.debug("Getting observations for device ID: {}", deviceId);
        Page<ObservationDTO> observations = observationService.getDeviceObservations(deviceId, pageable);
        return ResponseEntity.ok(observations);
    }

    @GetMapping("/observations/device/{deviceId}/range")
    public ResponseEntity<Page<ObservationDTO>> getDeviceObservationsByTimeRange(
            @PathVariable Long deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.debug("Getting observations for device ID: {} between {} and {}", deviceId, startTime, endTime);
        Page<ObservationDTO> observations = observationService.getDeviceObservationsByTimeRange(
                deviceId, startTime, endTime, pageable);
        return ResponseEntity.ok(observations);
    }

    @GetMapping("/observations/stats/device/{deviceId}")
    public ResponseEntity<ObservationStatsDTO> getObservationStats(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.debug("Getting observation stats for device ID: {}", deviceId);
        ObservationStatsDTO stats = observationService.getObservationStats(deviceId);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/anonymous-reports")
    public ResponseEntity<Void> processAnonymousReport(@Valid @RequestBody AnonymousReportDTO reportDTO) {
        log.debug("Processing anonymous report for device with partition UUID: {}", reportDTO.getDevicePartitionUuid());
        observationService.processAnonymousReport(reportDTO);
        return ResponseEntity.ok().build();
    }
}
