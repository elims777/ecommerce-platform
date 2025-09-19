package ru.rfsnab.userservice.mappers;

import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.dto.RoleDto;

public class RoleMapper {
    private RoleMapper() {}

    public static RoleEntity mapToRoleEntity(RoleDto roleDto){
        return RoleEntity.builder()
                .id(roleDto.getId())
                .name(roleDto.getName())
                .build();
    }

    public static RoleDto mapToRoleDto(RoleEntity role){
        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .build();
    }
}
