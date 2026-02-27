package com.splitter.backend.controllers;

import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import com.splitter.backend.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    USerRespository userRepository:

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBod Map<String, String> signUpRequest) {
        String username = signUpRequest.get("username");
        String password = signUpRequest.get("password");

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
            new UsernamePasswordAuthenticationToken(loginRequest.get("username), loginRequest.get("password")));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = jwtUtils.generateJwtToken(authentication);

            return ResponseEntity.ok(Map.of("token", jwt, "type", "Bearer"));
    }
    
}

