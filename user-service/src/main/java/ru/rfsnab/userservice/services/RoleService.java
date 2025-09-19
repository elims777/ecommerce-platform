package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.rfsnab.userservice.exceptions.CustomException;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.repository.RoleRepository;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    public RoleEntity findRoleByName(String name){
        return roleRepository.findByName(name).orElseThrow(
                ()-> new CustomException(
                        String.format("Role with name - %s not found!", name),
                        HttpStatus.NOT_FOUND.value()));
    }

}
