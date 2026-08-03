package com.datavault.server.service;

import com.datavault.server.config.JwtProperties;
import com.datavault.server.dto.AuthRequest;
import com.datavault.server.dto.AuthResponse;
import com.datavault.server.dto.RegisterRequest;
import com.datavault.server.entity.User;
import com.datavault.server.exception.StorageException;
import com.datavault.server.repository.UserRepository;
import com.datavault.server.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider, JwtProperties jwtProperties, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(AuthRequest authRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(authRequest.username(), authRequest.password())
        );

        String token = tokenProvider.generateToken(authentication);
        return new AuthResponse(token, jwtProperties.getExpirationMs() / 1000);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new StorageException("Username '" + request.username() + "' is already taken.");
        }

        User newUser = new User(
            request.username(),
            passwordEncoder.encode(request.password()),
            "ROLE_USER"
        );
        userRepository.save(newUser);

        return login(new AuthRequest(request.username(), request.password()));
    }
}
