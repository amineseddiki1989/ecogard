package com.ecoguard.tracking.controller;

import com.ecoguard.tracking.dto.UserDTO;
import com.ecoguard.tracking.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Getting current user: {}", userDetails.getUsername());
        UserDTO userDTO = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(userDTO);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #userDetails.username == @userService.getUserById(#id).email")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id, 
                                             @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Getting user with ID: {}", id);
        UserDTO userDTO = userService.getUserById(id);
        return ResponseEntity.ok(userDTO);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        log.debug("Getting all users");
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #userDetails.username == @userService.getUserById(#id).email")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, 
                                            @Valid @RequestBody UserDTO userDTO,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Updating user with ID: {}", id);
        UserDTO updatedUser = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.debug("Deleting user with ID: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
