package com.dip.controller;

import com.dip.domain.User;
import com.dip.domain.UserRole;
import com.dip.repository.UserRepository;
import com.dip.security.RoleRequired;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        return ResponseEntity.ok(userRepository.save(user));
    }

    @GetMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }
    
    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private UserRole role;
    }
}
