package com.gym.ai.memory;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document("chat_histories")
public class ChatHistoryDoc {
    @Id
    private String id;
    private List<MessageRecord> messages;   // 存自定义记录
}