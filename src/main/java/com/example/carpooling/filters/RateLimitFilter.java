package com.example.carpooling.filters;

import com.example.carpooling.services.RateLimiter;
import com.example.carpooling.utils.AuthUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private AuthUtil authUtil;

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String clientKey = authUtil.getId();
            if (clientKey == null || clientKey.isBlank()) {
                clientKey = resolveClientKey(request);
            }

            if (!rateLimiter.checkForRateLimiting(clientKey)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Too many requests, try again later.");
                return;
            }
        } catch (Exception e) {
            log.error("Rate limiting failed, allowing request to continue", e);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
