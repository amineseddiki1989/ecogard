package com.ecoguard.tracking.mapper;

import com.ecoguard.tracking.dto.DeviceDTO;
import com.ecoguard.tracking.entity.Device;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeviceMapper {

    @Mapping(target = "hasActiveTheftReport", ignore = true)
    @Mapping(target = "observationCount", ignore = true)
    DeviceDTO toDTO(Device device);
}
