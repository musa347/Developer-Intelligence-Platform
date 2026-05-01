package com.dip.dto;

import com.dip.domain.UserRole;
import lombok.Data;

@Data
public  class UpdateUserRequest {
    private String username;
    private String email;
    private String password;
    private UserRole role;
}