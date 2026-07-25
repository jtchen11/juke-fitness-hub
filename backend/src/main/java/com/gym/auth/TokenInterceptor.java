package com.gym.auth;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TokenInterceptor implements HandlerInterceptor {

    private static final String[] STATIC_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico", ".bmp"};
    private static final String[] STATIC_PREFIXES = {"/group_class/", "/static/", "/upload/", "/images/", "/trainer/"};

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof ResourceHttpRequestHandler) {
            return true;
        }

        String path = request.getRequestURI();

        for (String ext : STATIC_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }

        if (path.equals("/api/auth/login") || path.equals("/api/auth/mini-login")
            || path.equals("/api/auth/logout") || path.equals("/api/auth/send-code")
            || path.equals("/api/auth/login-by-code")) {
            return true;
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
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\"}");
                } catch (Exception ignored) {
                }
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
