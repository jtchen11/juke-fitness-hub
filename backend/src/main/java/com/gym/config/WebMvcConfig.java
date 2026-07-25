package com.gym.config;

import com.gym.auth.TokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] STATIC_PATTERNS = {
        "/group_class/**", "/trainer/**", "/static/**", "/upload/**", "/images/**"
    };

    private static final String[] AUTH_SKIP_PATTERNS = {
        "/api/auth/login", "/api/auth/mini-login", "/api/auth/logout",
        "/api/auth/send-code", "/api/auth/login-by-code"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TokenInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(STATIC_PATTERNS)
                .excludePathPatterns(AUTH_SKIP_PATTERNS);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /group_class/** 映射到 classpath:static/group_class/
        registry.addResourceHandler("/group_class/**")
                .addResourceLocations("classpath:/static/group_class/");
        // 将 /trainer/** 映射到 classpath:static/trainer/
        registry.addResourceHandler("/trainer/**")
                .addResourceLocations("classpath:/static/trainer/");
        // 将 /static/** 映射到 classpath:static/
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
