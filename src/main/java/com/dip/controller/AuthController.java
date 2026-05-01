package com.dip.controller;

import com.dip.domain.PasswordResetToken;
import com.dip.domain.User;
import com.dip.dto.ForgotPasswordRequest;
import com.dip.dto.LoginRequest;
import com.dip.dto.ResetPasswordRequest;
import com.dip.repository.PasswordResetTokenRepository;
import com.dip.repository.UserRepository;
import com.dip.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        var user = userRepository.findByUsername(request.getUsername());

        if (user.isEmpty() || !passwordEncoder.matches(request.getPassword(), user.get().getPassword())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid username or password");
            return ResponseEntity.status(401).body(error);
        }

        Map<String, String> response = new HashMap<>();
        response.put("username", user.get().getUsername());
        response.put("role", user.get().getRole().name());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/oauth/user")
    public ResponseEntity<?> getOAuthUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        var user = userRepository.findByUsername(email);
        if (user.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Map<String, String> response = new HashMap<>();
        response.put("username", user.get().getUsername());
        response.put("role", user.get().getRole().name());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElse(null);
            
            if (user == null) {
                return ResponseEntity.ok(Map.of("message", "Password reset email sent"));
            }
            
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setToken(UUID.randomUUID().toString());
            resetToken.setUser(user);
            resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));
            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
            return ResponseEntity.ok(Map.of("message", "Password reset email sent"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send password reset email"));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        return passwordResetTokenRepository.findByToken(request.getToken())
                .map(resetToken -> {
                    if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return ResponseEntity.status(400).body(Map.of("error", "Token is invalid or expired"));
                    }
                    resetToken.getUser().setPassword(passwordEncoder.encode(request.getNewPassword()));
                    userRepository.save(resetToken.getUser());
                    resetToken.setUsed(true);
                    passwordResetTokenRepository.save(resetToken);
                    return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
                })
                .orElse(ResponseEntity.status(400).body(Map.of("error", "Invalid token")));
    }
}
