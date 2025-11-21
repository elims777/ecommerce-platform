package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;

    public List<UserEntity> findAllUsers(){
        return userRepository.findAll();
    }

    public Optional<UserEntity> findUserById(Long id){
        return userRepository.findById(id);
    }

    public Optional<UserEntity> findUserByEmail(String email){
        return userRepository.findByEmail(email);
    }

    public UserEntity setDefaultRole(UserEntity user){
        Set<RoleEntity> roleEntities = new HashSet<>();
        roleEntities.add(roleService.findRoleByName("ROLE_USER"));
        user.setRoles(roleEntities);
        return user;
    }

    public UserEntity registerUser(UserEntity user){
        user=setDefaultRole(user);
        return userRepository.save(user);
    }

    public UserEntity updateUser(Long id,UserEntity user){
        return userRepository.findById(id)
                .map(
                        userEntity -> {
                            userEntity.setEmail(user.getEmail());
                            userEntity.setPassword(passwordEncoder.encode(user.getPassword()));
                            userEntity.setFirstname(user.getFirstname());
                            userEntity.setLastname(user.getLastname());
                            userEntity.setSurname(user.getSurname());
                            userEntity.setRoles(user.getRoles());
                            return userRepository.save(userEntity);
                        }
                ).orElseThrow(()->new RuntimeException(String.format("User with id = %s not found", id)));
    }

    public void deleteUser(Long id){

        userRepository.deleteById(id);
    }
}
