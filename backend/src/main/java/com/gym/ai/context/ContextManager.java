package com.gym.ai.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话上下文管理器：唯一允许读写对话上下文的 Service。
 * key 约定：业务前缀 + memberId + "_" + sessionId（沿用 memoryId 组合规则，防止多用户串号）。
 */
@Slf4j
@Service
public class ContextManager {

    // 存储所有活跃对话上下文
    private final ConcurrentHashMap<String, ConversationContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 获取上下文；同时检查是否过期（超时 2 小时），过期则自动移除并返回 null。
     *
     * @param key 上下文键（业务前缀 + memberId + "_" + sessionId）
     * @return 上下文，不存在或已过期时返回 null
     */
    public ConversationContext getContext(String key) {
        ConversationContext ctx = contextMap.get(key);
        if (ctx == null) {
            return null;
        }
        if (ctx.isExpired()) {
            log.info("对话上下文已过期，自动移除: {}", key);
            contextMap.remove(key);
            return null;
        }
        ctx.refresh();
        return ctx;
    }

    /**
     * 创建或更新上下文，并刷新最后访问时间。
     *
     * @param key     上下文键
     * @param context 对话上下文
     */
    public void updateContext(String key, ConversationContext context) {
        context.refresh();
        contextMap.put(key, context);
        log.debug("保存对话上下文: key={}, state={}", key, context.getCurrentState());
    }

    /**
     * 移除上下文（流程结束或用户取消时调用）。
     *
     * @param key 上下文键
     */
    public void removeContext(String key) {
        contextMap.remove(key);
        log.debug("移除对话上下文: {}", key);
    }

    /**
     * 返回当前所有活跃上下文的键（用于诊断日志）。
     *
     * @return 上下文键集合
     */
    public Set<String> keys() {
        return contextMap.keySet();
    }

    /**
     * 定时清理：每 60 秒扫描一次，清除所有过期上下文（兜底）。
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanUp() {
        long start = System.currentTimeMillis();
        int removed = 0;
        for (String key : contextMap.keySet()) {
            ConversationContext ctx = contextMap.get(key);
            if (ctx != null && ctx.isExpired()) {
                contextMap.remove(key);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("定时清理对话上下文完成，共移除 {} 个过期上下文，耗时 {}ms", removed, System.currentTimeMillis() - start);
        }
    }
}