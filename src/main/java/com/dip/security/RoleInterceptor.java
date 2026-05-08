package com.dip.security;

import com.dip.domain.UserRole;
import com.dip.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {
    
    private final UserRepository userRepository;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RoleRequired roleRequired = handlerMethod.getMethodAnnotation(RoleRequired.class);
        
        if (roleRequired == null) {
            return true;
        }
        
        String username = request.getHeader("X-User");
        if (username == null || username.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Missing X-User header\"}");
            return false;
        }
        
        var user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"User not found\"}");
            return false;
        }
        
        UserRole requiredRole = roleRequired.value();
        if (user.get().getRole() != requiredRole && user.get().getRole() != UserRole.ADMIN) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":\"Insufficient permissions. Required: " + requiredRole + "\"}");
            return false;
        }
        
        return true;
    }
}
