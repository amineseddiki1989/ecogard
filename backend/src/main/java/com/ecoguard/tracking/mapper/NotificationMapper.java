package com.ecoguard.tracking.mapper;

import com.ecoguard.tracking.dto.NotificationDTO;
import com.ecoguard.tracking.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    @Mapping(target = "deviceId", source = "device.id")
    @Mapping(target = "deviceName", source = "device.name")
    NotificationDTO toDTO(Notification notification);
}
