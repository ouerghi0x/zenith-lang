package com.zenith.chat.controller;

import com.zenith.chat.dto.*;
import com.zenith.chat.entity.ChatUser;
import com.zenith.chat.mapper.ChatUserMapper;
import com.zenith.chat.repository.ChatUserRepository;
import com.zenith.chat.security.JwtService;
import com.zenith.chat.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin
@Tag(name = "Authentication", description = "Endpoints for user login and registration")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ChatUserRepository userRepository;
    private final ChatUserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
        AuthenticationManager authManager,
        JwtService jwtService,
        UserDetailsService userDetailsService,
        ChatUserRepository userRepository,
        ChatUserMapper mapper,
        PasswordEncoder passwordEncoder
    ) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String token = jwtService.generateToken(userDetails);
        ChatUser entity = userRepository.findByEmail(request.email()).orElseThrow();
        String role = entity.getRole() != null ? entity.getRole().toString() : "USER";
        return ResponseEntity.ok(AuthResponse.of(token, entity.getId(), entity.getEmail(), role));
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody CreateChatUserRequest request) {
        ChatUser entity = mapper.toEntity(request);
        entity.setPassword(passwordEncoder.encode(request.password()));
        if (entity.getRole() == null) {
            entity.setRole(UserRole.USER);
        }
        ChatUser saved = userRepository.save(entity);
        UserDetails userDetails = userDetailsService.loadUserByUsername(saved.getEmail());
        String token = jwtService.generateToken(userDetails);
        String role = saved.getRole() != null ? saved.getRole().toString() : "USER";
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.of(token, saved.getId(), saved.getEmail(), role));
    }
}
