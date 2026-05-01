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
                            "/api/auth/**",
                            "/api/registration/**",
                            "/api/services/**",
                            "/api/documents/**",
                            "/api/query/**",
                            "/api/users/**",
                            "/api/test/**",
                            "/login/**", 
                            "/oauth2/**"
                        ).permitAll()
                        .requestMatchers("/api/**").permitAll()
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
