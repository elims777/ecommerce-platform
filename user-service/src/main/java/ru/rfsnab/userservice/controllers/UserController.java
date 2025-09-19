package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.exceptions.CustomException;
import ru.rfsnab.userservice.mappers.UserMapper;
import ru.rfsnab.userservice.models.dto.UserDto;
import ru.rfsnab.userservice.services.UserService;

import java.util.List;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ResponseEntity<UserDto> registerUser(@RequestBody UserDto userDto){
        if(userService.findUserByEmail(userDto.getEmail()).isPresent()){
            throw new CustomException("User already existed!", HttpStatus.CONFLICT.value());
        } else {
            userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
            return ResponseEntity.ok(
                    UserMapper.mapToUserDto(
                            userService.registerUser(
                                    UserMapper.mapToUserEntity(userDto))));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable("id") Long id, @RequestBody UserDto userDto){
        return ResponseEntity.ok(
                UserMapper.mapToUserDto(
                        userService.updateUser(id, UserMapper.mapToUserEntity(userDto))));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserDto>> getAllUsers(){
        return ResponseEntity.ok(
                userService.findAllUsers()
                        .stream()
                        .map(UserMapper::mapToUserDto)
                        .toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable("id") Long id){
        if(userService.findUserById(id).isPresent()){
            userService.deleteUser(id);
            return ResponseEntity.ok("User deleted.");
        } else {throw new CustomException(
                String.format("User whith id = %s not found", id),
                HttpStatus.NOT_FOUND.value());}
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable("id") Long id){
        return ResponseEntity.ok(
                UserMapper.mapToUserDto(
                        userService.findUserById(id)
                                .orElseThrow(()->new CustomException(
                                        String.format("User whith id = %s not found", id)))));
    }
}
