package com.gym.ai.agent;

import com.gym.ai.agent.model.AgentState;
import com.gym.ai.tool.GymTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.lang.reflect.Method;
import java.util.*;

@Slf4j
public abstract class ToolCallAgent extends ReActAgent {
    @Autowired
    protected GymTools gymTools;
    protected Map<String, ToolInfo> tools = new HashMap<>();

    public void registerTools() {
        tools.clear();
        for (Method m : GymTools.class.getMethods()) {
            dev.langchain4j.agent.tool.Tool anno = m.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            if (anno != null) {
                tools.put(anno.value()[0], new ToolInfo(anno.value()[0], m));
            }
        }
        log.info("Registered {} tools", tools.size());
    }

    @Override
    protected String act(String action) {
        state = AgentState.RUNNING;
        if (action == null || action.isEmpty()) return "\u65e0\u6548\u52a8\u4f5c";
        for (ToolInfo info : tools.values()) {
            if (action.contains(info.name)) {
                try {
                    Object result = info.method.invoke(gymTools, extractArgs(action));
                    return result != null ? result.toString() : "\u6267\u884c\u5b8c\u6210";
                } catch (Exception e) {
                    log.error("Tool error: {}", info.name, e);
                    return "\u5de5\u5177\u6267\u884c\u5931\u8d25\uff1a" + e.getCause().getMessage();
                }
            }
        }
        return "\u672a\u627e\u5230\u5bf9\u5e94\u5de5\u5177\uff1a" + action;
    }

    protected Object[] extractArgs(String action) { return new Object[0]; }

    static class ToolInfo {
        String name;
        Method method;
        ToolInfo(String name, Method method) { this.name = name; this.method = method; }
    }
}