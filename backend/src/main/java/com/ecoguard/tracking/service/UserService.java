package com.ecoguard.tracking.service;

import com.ecoguard.tracking.dto.RegisterRequestDTO;
import com.ecoguard.tracking.dto.UserDTO;
import com.ecoguard.tracking.entity.User;
import com.ecoguard.tracking.exception.EmailAlreadyExistsException;
import com.ecoguard.tracking.exception.ResourceNotFoundException;
import com.ecoguard.tracking.mapper.UserMapper;
import com.ecoguard.tracking.repository.DeviceRepository;
import com.ecoguard.tracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        UserDTO userDTO = userMapper.toDTO(user);
        userDTO.setDeviceCount(deviceRepository.countByUser(user));
        
        return userDTO;
    }

    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        
        UserDTO userDTO = userMapper.toDTO(user);
        userDTO.setDeviceCount(deviceRepository.countByUser(user));
        
        return userDTO;
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> {
                    UserDTO dto = userMapper.toDTO(user);
                    dto.setDeviceCount(deviceRepository.countByUser(user));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDTO registerUser(RegisterRequestDTO registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new EmailAlreadyExistsException("Email already in use: " + registerRequest.getEmail());
        }
        
        if (!registerRequest.getPassword().equals(registerRequest.getPasswordConfirmation())) {
            throw new IllegalArgumentException("Password and confirmation do not match");
        }
        
        User user = User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .name(registerRequest.getName())
                .phoneNumber(registerRequest.getPhoneNumber())
                .accountEnabled(true)
                .accountLocked(false)
                .accountExpired(false)
                .credentialsExpired(false)
                .twoFactorEnabled(false)
                .roles(new HashSet<>())
                .build();
        
        user.getRoles().add("ROLE_USER");
        
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());
        
        UserDTO userDTO = userMapper.toDTO(savedUser);
        userDTO.setDeviceCount(0);
        
        return userDTO;
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        user.setName(userDTO.getName());
        user.setPhoneNumber(userDTO.getPhoneNumber());
        user.setTwoFactorEnabled(userDTO.isTwoFactorEnabled());
        
        User updatedUser = userRepository.save(user);
        log.info("User updated successfully: {}", updatedUser.getEmail());
        
        UserDTO updatedDTO = userMapper.toDTO(updatedUser);
        updatedDTO.setDeviceCount(deviceRepository.countByUser(updatedUser));
        
        return updatedDTO;
    }

    @Transactional
    public void updateLastLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        
        userRepository.deleteById(id);
        log.info("User deleted successfully with id: {}", id);
    }
}
