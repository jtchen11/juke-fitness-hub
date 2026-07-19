package com.gym.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    private String role;   // 新增：ADMIN 或 MEMBER
}