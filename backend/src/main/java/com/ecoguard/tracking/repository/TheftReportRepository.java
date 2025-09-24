package com.ecoguard.tracking.repository;

import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.entity.TheftReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TheftReportRepository extends JpaRepository<TheftReport, Long> {
    
    List<TheftReport> findByDevice(Device device);
    
    List<TheftReport> findByDeviceAndStatus(Device device, TheftReport.TheftReportStatus status);
    
    @Query("SELECT tr FROM TheftReport tr WHERE tr.device.user.id = :userId")
    List<TheftReport> findByUserId(@Param("userId") Long userId);
    
    @Query("SELECT tr FROM TheftReport tr WHERE tr.device.id = :deviceId AND tr.status = 'ACTIVE'")
    Optional<TheftReport> findActiveReportByDeviceId(@Param("deviceId") Long deviceId);
    
    @Query("SELECT COUNT(tr) FROM TheftReport tr WHERE tr.device.user.id = :userId AND tr.status = 'ACTIVE'")
    int countActiveReportsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT tr FROM TheftReport tr WHERE tr.status = 'ACTIVE' AND tr.reportedAt >= :since")
    List<TheftReport> findActiveReportsSince(@Param("since") LocalDateTime since);
}
