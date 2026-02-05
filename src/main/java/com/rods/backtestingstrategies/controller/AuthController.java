package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.User;
import com.rods.backtestingstrategies.entity.AuthenticationRequest;
import com.rods.backtestingstrategies.entity.AuthenticationResponse;
import com.rods.backtestingstrategies.entity.RegisterRequest;
import com.rods.backtestingstrategies.repository.UserRepository;
import com.rods.backtestingstrategies.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

        private final UserRepository repository;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtils jwtService;
        private final AuthenticationManager authenticationManager;

        @PostMapping("/register")
        public ResponseEntity<AuthenticationResponse> register(
                        @jakarta.validation.Valid @RequestBody RegisterRequest request) {
                if (repository.findByUsername(request.getUsername()).isPresent()) {
                        return ResponseEntity.badRequest().build(); // Or throw exception
                }

                var user = User.builder()
                                .username(request.getUsername())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .role("ROLE_USER")
                                .build();

                repository.save(user); // Wait, User entity needs to implement UserDetails? No,
                                       // CustomUserDetailsService handles it.

                // After register, do we return token? Usually yes.
                // We first need to wrap it in a UserDetails object to pass to generateToken
                // Or generate token manually using the user object fields.
                // JwtUtils expects UserDetails.
                // Let's reload it or construct a dummy UserDetails or update JwtUtils to accept
                // User object (but it expects UserDetails interface).
                // Since CustomUserDetailsService converts User -> UserDetails, let's just use
                // that logic or recreate it here.
                // Simply:
                var userDetails = new org.springframework.security.core.userdetails.User(
                                user.getUsername(),
                                user.getPassword(),
                                new java.util.ArrayList<>());

                var jwtToken = jwtService.generateToken(userDetails);

                return ResponseEntity.ok(AuthenticationResponse.builder()
                                .token(jwtToken)
                                .build());
        }

        @PostMapping("/login")
        public ResponseEntity<AuthenticationResponse> authenticate(
                        @jakarta.validation.Valid @RequestBody AuthenticationRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getUsername(),
                                                request.getPassword()));
                // If we get here, correct.
                var user = repository.findByUsername(request.getUsername())
                                .orElseThrow();

                var userDetails = new org.springframework.security.core.userdetails.User(
                                user.getUsername(),
                                user.getPassword(),
                                new java.util.ArrayList<>());

                var jwtToken = jwtService.generateToken(userDetails);

                return ResponseEntity.ok(AuthenticationResponse.builder()
                                .token(jwtToken)
                                .build());
        }

}
