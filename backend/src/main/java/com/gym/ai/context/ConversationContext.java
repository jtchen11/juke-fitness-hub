package com.gym.ai.context;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 对话上下文：统一封装多轮对话的会话标识、用户、状态与业务负载。
 * 只能通过 ContextManager 读写，业务层不得直接持有 Map。
 */
@Data
public class ConversationContext {

    /** 会话ID（客户端传入的聊天会话标识） */
    private String sessionId;

    /** 用户ID（会员ID；访客场景可能为 null） */
    private Long userId;

    /** 当前对话状态 */
    private ConversationState currentState;

    /** 业务负载：存放待完成预约等中间数据 */
    private Map<String, Object> payload = new HashMap<>();

    /** 最后访问时间（用于超时清理） */
    private LocalDateTime lastAccessTime;

    /** 上下文超时时间：2 小时 */
    private static final long TIMEOUT_MINUTES = 120;

    public ConversationContext() {
        this.currentState = ConversationState.IDLE;
        this.lastAccessTime = LocalDateTime.now();
    }

    public ConversationContext(Long userId, String sessionId) {
        this();
        this.userId = userId;
        this.sessionId = sessionId;
    }

    /** 刷新最后访问时间 */
    public void refresh() {
        this.lastAccessTime = LocalDateTime.now();
    }

    /** 是否已过期（超过 2 小时未访问） */
    public boolean isExpired() {
        return lastAccessTime == null || lastAccessTime.plusMinutes(TIMEOUT_MINUTES).isBefore(LocalDateTime.now());
    }
}