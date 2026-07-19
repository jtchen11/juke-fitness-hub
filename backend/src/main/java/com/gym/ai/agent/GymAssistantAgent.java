package com.gym.ai.agent;

import com.gym.ai.agent.model.AgentResult;
import com.gym.ai.agent.model.AgentState;
import com.gym.ai.tool.GymTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GymAssistantAgent extends ToolCallAgent {
    private String context = "";
    private String lastResponse = "";

    public AgentResult chat(String userMessage, String userContext) {
        this.context = userContext;
        registerTools();
        return execute(userMessage);
    }

    @Override
    protected String analyze(String input) {
        if (input.contains("\u8bfe\u7a0b") || input.contains("\u9884\u7ea6")) return "NEED_TOOL:query_available";
        if (input.contains("\u4f53\u6d4b") || input.contains("\u5065\u5eb7")) return "NEED_TOOL:query_test";
        if (input.contains("\u6559\u7ec3") || input.contains("\u8bad\u7ec3")) return "NEED_TOOL:query_trainer";
        return "NEED_LLM:direct_reply";
    }

    @Override
    protected boolean shouldFinish(String thought) {
        return thought == null || thought.startsWith("NEED_LLM");
    }

    @Override
    protected String decideAction(String thought) {
        if (thought.contains("query_available")) return "\u67e5\u8be2\u53ef\u9884\u7ea6\u56e2\u8bfe";
        if (thought.contains("query_test")) return "\u67e5\u8be2\u4f53\u6d4b\u5386\u53f2";
        if (thought.contains("query_trainer")) return "\u67e5\u8be2\u6559\u7ec3\u4fe1\u606f";
        return "";
    }

    @Override
    protected String buildFinalResponse() {
        return lastResponse;
    }

    @Override
    public String act(String action) {
        String result = super.act(action);
        lastResponse = result;
        return result;
    }
}