package com.ecoguard.tracking.mapper;

import com.ecoguard.tracking.dto.ObservationDTO;
import com.ecoguard.tracking.entity.Observation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ObservationMapper {

    @Mapping(target = "deviceId", source = "device.id")
    @Mapping(target = "deviceName", source = "device.name")
    ObservationDTO toDTO(Observation observation);
}
