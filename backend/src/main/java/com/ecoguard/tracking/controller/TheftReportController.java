package com.ecoguard.tracking.controller;

import com.ecoguard.tracking.dto.TheftReportDTO;
import com.ecoguard.tracking.service.TheftReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/theft-reports")
@RequiredArgsConstructor
@Slf4j
public class TheftReportController {

    private final TheftReportService theftReportService;

    @GetMapping
    public ResponseEntity<List<TheftReportDTO>> getUserTheftReports(@AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Getting theft reports for user ID: {}", userId);
        List<TheftReportDTO> theftReports = theftReportService.getUserTheftReports(userId);
        return ResponseEntity.ok(theftReports);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TheftReportDTO> getTheftReportById(@PathVariable Long id,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Getting theft report with ID: {} for user ID: {}", id, userId);
        TheftReportDTO theftReport = theftReportService.getTheftReportById(id, userId);
        return ResponseEntity.ok(theftReport);
    }

    @GetMapping("/device/{deviceId}/active")
    public ResponseEntity<TheftReportDTO> getActiveTheftReportByDeviceId(@PathVariable Long deviceId,
                                                                        @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Getting active theft report for device ID: {}", deviceId);
        TheftReportDTO theftReport = theftReportService.getActiveTheftReportByDeviceId(deviceId);
        return ResponseEntity.ok(theftReport);
    }

    @PostMapping
    public ResponseEntity<TheftReportDTO> createTheftReport(@Valid @RequestBody TheftReportDTO theftReportDTO,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Creating theft report for device ID: {} by user ID: {}", theftReportDTO.getDeviceId(), userId);
        TheftReportDTO createdReport = theftReportService.createTheftReport(theftReportDTO, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdReport);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TheftReportDTO> updateTheftReport(@PathVariable Long id,
                                                          @Valid @RequestBody TheftReportDTO theftReportDTO,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Updating theft report with ID: {} by user ID: {}", id, userId);
        TheftReportDTO updatedReport = theftReportService.updateTheftReport(id, theftReportDTO, userId);
        return ResponseEntity.ok(updatedReport);
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<TheftReportDTO> resolveTheftReport(@PathVariable Long id,
                                                           @RequestParam String resolutionNotes,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Resolving theft report with ID: {} by user ID: {}", id, userId);
        TheftReportDTO resolvedReport = theftReportService.resolveTheftReport(id, resolutionNotes, userId);
        return ResponseEntity.ok(resolvedReport);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<TheftReportDTO> cancelTheftReport(@PathVariable Long id,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Cancelling theft report with ID: {} by user ID: {}", id, userId);
        TheftReportDTO cancelledReport = theftReportService.cancelTheftReport(id, userId);
        return ResponseEntity.ok(cancelledReport);
    }
}
