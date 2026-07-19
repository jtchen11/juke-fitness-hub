package com.gym.ai.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecord {
    private String role;   // "user" 或 "assistant"
    private String text;
    private String imageUrl;  // 新增：存储图片 URL
}