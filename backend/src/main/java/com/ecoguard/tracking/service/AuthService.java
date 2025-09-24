package com.ecoguard.tracking.service;

import com.ecoguard.tracking.dto.AuthRequestDTO;
import com.ecoguard.tracking.dto.AuthResponseDTO;
import com.ecoguard.tracking.dto.RegisterRequestDTO;
import com.ecoguard.tracking.dto.UserDTO;
import com.ecoguard.tracking.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final UserDetailsServiceImpl userDetailsService;

    @Transactional
    public AuthResponseDTO login(AuthRequestDTO authRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequest.getEmail(),
                        authRequest.getPassword()
                )
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authRequest.getEmail());
        
        // Update last login time
        userService.updateLastLogin(authRequest.getEmail());
        
        // Get user details
        UserDTO userDTO = userService.getUserByEmail(authRequest.getEmail());
        
        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getJwtExpiration() / 1000)
                .user(userDTO)
                .requiresTwoFactor(userDTO.isTwoFactorEnabled())
                .build();
    }

    @Transactional
    public AuthResponseDTO refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        
        String newAccessToken = jwtTokenProvider.generateToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);
        
        UserDTO userDTO = userService.getUserByEmail(username);
        
        return AuthResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getJwtExpiration() / 1000)
                .user(userDTO)
                .requiresTwoFactor(userDTO.isTwoFactorEnabled())
                .build();
    }

    @Transactional
    public UserDTO register(RegisterRequestDTO registerRequest) {
        return userService.registerUser(registerRequest);
    }
}
