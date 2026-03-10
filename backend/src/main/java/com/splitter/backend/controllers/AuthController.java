package com.splitter.backend.controllers;

import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import com.splitter.backend.security.JwtUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager,
                        UserRepository userRepository,
                        PasswordEncoder encoder,
                        JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> signUpRequest) {
        String username = signUpRequest.get("username");
        String password = signUpRequest.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Username and password are required.");
        }

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body("Error: Username is already in use.");
        }

        User user = new User(username, encoder.encode(password));
        userRepository.save(user);

        return ResponseEntity.ok("User has been registered successfully!");
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.get("username"),
                        loginRequest.get("password"))
        );

        String jwt = jwtUtils.generateJwtToken(authentication);

        return ResponseEntity.ok(Map.of(
                "token", jwt,
                "type", "Bearer"
        ));
    }
}