package com.ecoguard.tracking.repository;

import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    
    List<Device> findByUser(User user);
    
    List<Device> findByUserAndStatus(User user, Device.DeviceStatus status);
    
    Optional<Device> findByPartitionUuid(String partitionUuid);
    
    boolean existsByPartitionUuid(String partitionUuid);
    
    @Query("SELECT d FROM Device d WHERE d.status = 'STOLEN'")
    List<Device> findAllStolenDevices();
    
    @Query("SELECT COUNT(d) FROM Device d WHERE d.user = :user")
    int countByUser(@Param("user") User user);
    
    @Query("SELECT d FROM Device d WHERE d.status = 'STOLEN' AND d.id IN " +
           "(SELECT tr.device.id FROM TheftReport tr WHERE tr.status = 'ACTIVE')")
    List<Device> findAllActivelyStolenDevices();
}
