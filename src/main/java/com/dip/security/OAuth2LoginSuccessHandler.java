package com.dip.security;

import com.dip.domain.User;
import com.dip.domain.UserRole;
import com.dip.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        // Check if user exists, if not create new user
        User user = userRepository.findByUsername(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setUsername(email);
                    newUser.setPassword(UUID.randomUUID().toString()); // Random password for OAuth users
                    newUser.setRole(UserRole.USER); // Default role
                    return userRepository.save(newUser);
                });

        // Redirect to frontend with user info
        String redirectUrl = String.format("/app.html?username=%s&role=%s",
                user.getUsername(),
                user.getRole().name());
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
