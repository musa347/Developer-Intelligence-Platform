package com.dip.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PIIMaskingService {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\b\\d{10,16}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("\\bhttps?://[^\\s<>\"']+|www\\.[^\\s<>\"']+\\b");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("\\b[A-Za-z0-9+/]{20,}={0,2}\\b");
    
    public String maskPII(String text) {
        if (text == null) return null;
        
        String masked = text;
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE]");
        masked = ACCOUNT_PATTERN.matcher(masked).replaceAll("[ACCOUNT]");
        masked = URL_PATTERN.matcher(masked).replaceAll("[URL]");
        masked = API_KEY_PATTERN.matcher(masked).replaceAll("[API_KEY]");
        
        return masked;
    }
}
