package com.gym.ai.agent;

import com.gym.ai.agent.model.AgentResult;
import com.gym.ai.agent.model.AgentState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseAgent {
    protected AgentState state = AgentState.IDLE;
    protected int maxSteps = 10;
    protected int currentStep = 0;

    public AgentResult execute(String userMessage) {
        state = AgentState.RUNNING;
        currentStep = 0;
        log.info("Agent start: {}", userMessage);
        try {
            while (currentStep < maxSteps) {
                currentStep++;
                String nextAction = think(userMessage);
                if (nextAction == null || "FINISH".equals(nextAction)) {
                    state = AgentState.FINISHED;
                    return new AgentResult(buildFinalResponse(), true, currentStep, state);
                }
                String observation = act(nextAction);
                userMessage = observe(observation);
            }
            state = AgentState.FINISHED;
            return new AgentResult("\u5bf9\u8bdd\u8f6e\u6b21\u5df2\u8fbe\u4e0a\u9650\uff0c\u8bf7\u91cd\u65b0\u5f00\u59cb\u4e00\u8ba2\u65b0\u5bf9\u8bdd", true, currentStep, state);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Agent error", e);
            return new AgentResult("\u7cfb\u7edf\u5f02\u5e38\uff1a" + e.getMessage(), false, currentStep, state);
        }
    }

    protected abstract String think(String userMessage);
    protected abstract String act(String action);
    protected abstract String observe(String observation);
    protected abstract String buildFinalResponse();
}