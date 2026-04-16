package com.dip.config;

import com.dip.domain.User;
import com.dip.domain.UserRole;
import com.dip.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
    
    private final UserRepository userRepository;
    
    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("admin123");
            admin.setRole(UserRole.ADMIN);
            userRepository.save(admin);
            
            User user = new User();
            user.setUsername("user");
            user.setPassword("user123");
            user.setRole(UserRole.USER);
            userRepository.save(user);
            
            System.out.println("✅ Default users created: admin/admin123, user/user123");
        }
    }
}
