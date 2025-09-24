package com.ecoguard.tracking.service;

import com.ecoguard.tracking.dto.DeviceDTO;
import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.entity.TheftReport;
import com.ecoguard.tracking.entity.User;
import com.ecoguard.tracking.exception.DuplicateResourceException;
import com.ecoguard.tracking.exception.ResourceNotFoundException;
import com.ecoguard.tracking.mapper.DeviceMapper;
import com.ecoguard.tracking.repository.DeviceRepository;
import com.ecoguard.tracking.repository.ObservationRepository;
import com.ecoguard.tracking.repository.TheftReportRepository;
import com.ecoguard.tracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final TheftReportRepository theftReportRepository;
    private final ObservationRepository observationRepository;
    private final DeviceMapper deviceMapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "devices", key = "#userId")
    public List<DeviceDTO> getUserDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        return deviceRepository.findByUser(user).stream()
                .map(device -> {
                    DeviceDTO dto = deviceMapper.toDTO(device);
                    enrichDeviceDTO(dto, device);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "device", key = "#id")
    public DeviceDTO getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + id));
        
        DeviceDTO dto = deviceMapper.toDTO(device);
        enrichDeviceDTO(dto, device);
        
        return dto;
    }

    @Transactional(readOnly = true)
    public DeviceDTO getDeviceByPartitionUuid(String partitionUuid) {
        Device device = deviceRepository.findByPartitionUuid(partitionUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with partition UUID: " + partitionUuid));
        
        DeviceDTO dto = deviceMapper.toDTO(device);
        enrichDeviceDTO(dto, device);
        
        return dto;
    }

    @Transactional
    @CacheEvict(value = {"devices", "device"}, allEntries = true)
    public DeviceDTO registerDevice(Long userId, DeviceDTO deviceDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        if (deviceRepository.existsByPartitionUuid(deviceDTO.getPartitionUuid())) {
            throw new DuplicateResourceException("Device already exists with partition UUID: " + deviceDTO.getPartitionUuid());
        }
        
        Device device = Device.builder()
                .user(user)
                .partitionUuid(deviceDTO.getPartitionUuid())
                .name(deviceDTO.getName())
                .model(deviceDTO.getModel())
                .status(Device.DeviceStatus.ACTIVE)
                .registrationDate(LocalDateTime.now())
                .build();
        
        Device savedDevice = deviceRepository.save(device);
        log.info("Device registered successfully: {} for user: {}", savedDevice.getName(), user.getEmail());
        
        DeviceDTO savedDTO = deviceMapper.toDTO(savedDevice);
        savedDTO.setHasActiveTheftReport(false);
        savedDTO.setObservationCount(0);
        
        return savedDTO;
    }

    @Transactional
    @CacheEvict(value = {"devices", "device"}, allEntries = true)
    public DeviceDTO updateDevice(Long id, DeviceDTO deviceDTO) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + id));
        
        device.setName(deviceDTO.getName());
        device.setModel(deviceDTO.getModel());
        
        Device updatedDevice = deviceRepository.save(device);
        log.info("Device updated successfully: {}", updatedDevice.getName());
        
        DeviceDTO updatedDTO = deviceMapper.toDTO(updatedDevice);
        enrichDeviceDTO(updatedDTO, updatedDevice);
        
        return updatedDTO;
    }

    @Transactional
    @CacheEvict(value = {"devices", "device"}, allEntries = true)
    public DeviceDTO updateDeviceStatus(Long id, Device.DeviceStatus status) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + id));
        
        device.setStatus(status);
        
        Device updatedDevice = deviceRepository.save(device);
        log.info("Device status updated to {} for device: {}", status, updatedDevice.getName());
        
        DeviceDTO updatedDTO = deviceMapper.toDTO(updatedDevice);
        enrichDeviceDTO(updatedDTO, updatedDevice);
        
        return updatedDTO;
    }

    @Transactional
    @CacheEvict(value = {"devices", "device"}, allEntries = true)
    public void deleteDevice(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Device not found with id: " + id);
        }
        
        deviceRepository.deleteById(id);
        log.info("Device deleted successfully with id: {}", id);
    }

    @Transactional(readOnly = true)
    public List<DeviceDTO> getAllStolenDevices() {
        return deviceRepository.findAllStolenDevices().stream()
                .map(device -> {
                    DeviceDTO dto = deviceMapper.toDTO(device);
                    enrichDeviceDTO(dto, device);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeviceDTO> getAllActivelyStolenDevices() {
        return deviceRepository.findAllActivelyStolenDevices().stream()
                .map(device -> {
                    DeviceDTO dto = deviceMapper.toDTO(device);
                    enrichDeviceDTO(dto, device);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private void enrichDeviceDTO(DeviceDTO dto, Device device) {
        // Check if device has active theft report
        Optional<TheftReport> activeReport = theftReportRepository.findActiveReportByDeviceId(device.getId());
        dto.setHasActiveTheftReport(activeReport.isPresent());
        
        // Get observation count
        int observationCount = observationRepository.countByDeviceId(device.getId());
        dto.setObservationCount(observationCount);
    }
}
