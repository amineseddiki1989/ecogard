package com.ecoguard.tracking.mapper;

import com.ecoguard.tracking.dto.UserDTO;
import com.ecoguard.tracking.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "deviceCount", ignore = true)
    UserDTO toDTO(User user);
}
