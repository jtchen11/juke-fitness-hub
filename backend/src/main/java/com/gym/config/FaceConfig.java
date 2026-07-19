package com.gym.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FaceConfig {

    @Value("${face.service.url:http://localhost:5001}")
    private String faceServiceUrl;

    public String getFaceServiceUrl() {
        return faceServiceUrl;
    }
}