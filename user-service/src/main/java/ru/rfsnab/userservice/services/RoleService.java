package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.repository.RoleRepository;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    public RoleEntity findRoleByName(String name){
        return roleRepository.findByName(name).orElseThrow(
                ()-> new UsernameNotFoundException("Пользователь с email " + name + " не найден"));
    }

}
