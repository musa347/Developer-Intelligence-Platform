package com.dip.security;

import com.dip.domain.UserRole;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RoleRequired {
    UserRole value();
}
