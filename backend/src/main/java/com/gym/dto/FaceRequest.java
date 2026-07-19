package com.gym.dto;

import lombok.Data;

@Data
public class FaceRequest {
    private String userId;
    private String image;
    private Double tolerance;
}