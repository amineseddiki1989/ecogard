package com.ecoguard.tracking.mapper;

import com.ecoguard.tracking.dto.TheftReportDTO;
import com.ecoguard.tracking.entity.TheftReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TheftReportMapper {

    @Mapping(target = "deviceId", source = "device.id")
    @Mapping(target = "deviceName", source = "device.name")
    @Mapping(target = "deviceModel", source = "device.model")
    TheftReportDTO toDTO(TheftReport theftReport);
}
