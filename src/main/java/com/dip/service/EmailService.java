package com.dip.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendPasswordResetEmail(String toEmail, String token) {
        try {
            String resetLink = baseUrl + "/reset-password?token=" + token;
            
            log.info("Attempting to send password reset email to: {}", toEmail);
            log.info("From email: {}", fromEmail);
            log.debug("Reset link: {}", resetLink);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset Request");
            message.setText("Click the link below to reset your password. It expires in 1 hour:\n\n" + resetLink);

            String emailText = "Password Reset Request\n\n" +
                              "Click the link below to reset your password. It expires in 1 hour:\n\n" +
                              resetLink + "\n\n" +
                              "If the link doesn't work, copy and paste it into your browser.\n\n" +
                              "If you didn't request this, please ignore this email.";
            message.setText(emailText);

            log.info("About to call mailSender.send()...");
            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Exception message: {}", e.getMessage());
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}
