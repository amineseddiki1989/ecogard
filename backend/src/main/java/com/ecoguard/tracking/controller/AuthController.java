package com.ecoguard.tracking.controller;

import com.ecoguard.tracking.dto.AuthRequestDTO;
import com.ecoguard.tracking.dto.AuthResponseDTO;
import com.ecoguard.tracking.dto.RegisterRequestDTO;
import com.ecoguard.tracking.dto.UserDTO;
import com.ecoguard.tracking.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO authRequest) {
        log.debug("Login request for user: {}", authRequest.getEmail());
        AuthResponseDTO response = authService.login(authRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refreshToken(@RequestParam String refreshToken) {
        log.debug("Token refresh request");
        AuthResponseDTO response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequestDTO registerRequest) {
        log.debug("Registration request for user: {}", registerRequest.getEmail());
        UserDTO userDTO = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(userDTO);
    }
}
