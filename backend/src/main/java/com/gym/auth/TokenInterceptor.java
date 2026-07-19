package com.gym.auth;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TokenInterceptor implements HandlerInterceptor {

    private static final String[] SKIP_PATHS = {"/api/auth/login", "/api/auth/mini-login", "/api/auth/logout", "/api/auth/send-code", "/api/auth/login-by-code"};

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        for (String skip : SKIP_PATHS) {
            if (path.equals(skip)) return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                Long userId = JwtUtil.getUserId(token);
                String role = JwtUtil.getRole(token);
                LoginContext.set(new LoginContext.LoginUser(userId, role, null, null));
            } catch (Exception e) {
                response.setStatus(401);
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        LoginContext.clear();
    }
}
