package com.example.carpooling.utils;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    public String getId() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return null;
        }

        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
