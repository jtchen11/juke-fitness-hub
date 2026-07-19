package com.gym.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @SuppressWarnings("deprecation")
    public List<ChatMessage> getMessages(Object memoryId) {
        String id = memoryId.toString();
        ChatHistoryDoc doc = mongoTemplate.findById(id, ChatHistoryDoc.class);
        if (doc == null || doc.getMessages() == null || doc.getMessages().isEmpty()) {
            return new ArrayList<>();
        }
        return doc.getMessages().stream()
                .map(record -> {
                    if (record.getText() == null || record.getText().isEmpty()) {
                        return null;
                    }
                    if ("user".equals(record.getRole())) {
                        return UserMessage.from(record.getText());
                    } else if ("assistant".equals(record.getRole())) {
                        return AiMessage.from(record.getText());
                    } else {
                        return null;
                    }
                })
                .filter(msg -> msg != null)
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = memoryId.toString();
        List<MessageRecord> records = messages.stream()
                .map(msg -> {
                    if (msg == null || msg.text() == null) return null;
                    if (msg instanceof UserMessage) {
                        return new MessageRecord("user", msg.text(), null);
                    } else if (msg instanceof AiMessage) {
                        return new MessageRecord("assistant", msg.text(), null);
                    } else {
                        return null;
                    }
                })
                .filter(rec -> rec != null)
                .collect(Collectors.toList());
        ChatHistoryDoc doc = new ChatHistoryDoc();
        doc.setId(id);
        doc.setMessages(records);
        mongoTemplate.save(doc);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String id = memoryId.toString();
        Query query = Query.query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, ChatHistoryDoc.class);
    }

    // ====== 新增：直接保存带图片 URL 的记录 ======
    public void saveMessageRecord(String sessionId, String role, String text, String imageUrl) {
        ChatHistoryDoc doc = mongoTemplate.findById(sessionId, ChatHistoryDoc.class);
        if (doc == null) {
            doc = new ChatHistoryDoc();
            doc.setId(sessionId);
            doc.setMessages(new ArrayList<>());
        }
        List<MessageRecord> records = doc.getMessages();
        // 避免重复添加相同的消息
        if (!records.isEmpty() && records.get(records.size() - 1).getText().equals(text)) {
            return;
        }
        records.add(new MessageRecord(role, text, imageUrl));
        mongoTemplate.save(doc);
    }

    // ====== 新增：获取包含图片 URL 的历史记录 ======
    public List<MessageRecord> getMessageRecords(String sessionId) {
        ChatHistoryDoc doc = mongoTemplate.findById(sessionId, ChatHistoryDoc.class);
        if (doc == null || doc.getMessages() == null) {
            return new ArrayList<>();
        }
        return doc.getMessages();
    }
}