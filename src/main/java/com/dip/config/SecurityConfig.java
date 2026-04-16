package com.dip.config;


import com.dip.security.OAuth2LoginSuccessHandler;
import com.dip.security.RoleInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable for API
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                            "/", 
                            "/index.html", 
                            "/app.html", 
                            "/api/auth/**",           // All auth endpoints
                            "/api/registration/**",   // All registration endpoints
                            "/api/services/**",       // Service endpoints
                            "/api/documents/**",      // Document endpoints
                            "/api/query/**",          // Query endpoints
                            "/api/users/**",          // User endpoints (role check in interceptor)
                            "/login/**", 
                            "/oauth2/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/index.html")
                        .successHandler(oAuth2LoginSuccessHandler)
                );

        return http.build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }
}
