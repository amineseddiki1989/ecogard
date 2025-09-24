package com.ecoguard.tracking.repository;

import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.entity.Observation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ObservationRepository extends JpaRepository<Observation, Long> {
    
    Page<Observation> findByDevice(Device device, Pageable pageable);
    
    Page<Observation> findByDeviceAndObservationTimeBetween(
            Device device, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    @Query("SELECT o FROM Observation o WHERE o.device.id = :deviceId ORDER BY o.observationTime DESC")
    Page<Observation> findByDeviceIdOrderByObservationTimeDesc(
            @Param("deviceId") Long deviceId, Pageable pageable);
    
    @Query("SELECT o FROM Observation o WHERE o.device.id = :deviceId AND o.observationTime BETWEEN :start AND :end " +
           "ORDER BY o.observationTime DESC")
    Page<Observation> findByDeviceIdAndTimeRangeOrderByObservationTimeDesc(
            @Param("deviceId") Long deviceId, 
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end, 
            Pageable pageable);
    
    @Query("SELECT o FROM Observation o WHERE o.device.id = :deviceId ORDER BY o.observationTime DESC LIMIT 1")
    Optional<Observation> findLatestByDeviceId(@Param("deviceId") Long deviceId);
    
    @Query("SELECT COUNT(o) FROM Observation o WHERE o.device.id = :deviceId")
    int countByDeviceId(@Param("deviceId") Long deviceId);
    
    @Query("SELECT COUNT(o) FROM Observation o WHERE o.device.id = :deviceId AND o.observationTime >= :since")
    int countByDeviceIdSince(@Param("deviceId") Long deviceId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(DISTINCT o.reporterHash) FROM Observation o WHERE o.device.id = :deviceId AND o.reporterHash IS NOT NULL")
    int countUniqueReportersByDeviceId(@Param("deviceId") Long deviceId);
    
    @Query("SELECT AVG(o.confidence) FROM Observation o WHERE o.device.id = :deviceId")
    Double getAverageConfidenceByDeviceId(@Param("deviceId") Long deviceId);
    
    @Query("SELECT MIN(o.observationTime) FROM Observation o WHERE o.device.id = :deviceId")
    LocalDateTime getFirstObservationTimeByDeviceId(@Param("deviceId") Long deviceId);
    
    @Query("SELECT MAX(o.observationTime) FROM Observation o WHERE o.device.id = :deviceId")
    LocalDateTime getLastObservationTimeByDeviceId(@Param("deviceId") Long deviceId);
    
    @Query("SELECT o FROM Observation o WHERE o.observationTime < :cutoffDate")
    List<Observation> findObservationsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}
