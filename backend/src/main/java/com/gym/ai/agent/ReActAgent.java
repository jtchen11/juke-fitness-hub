package com.gym.ai.agent;

import com.gym.ai.agent.model.AgentState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ReActAgent extends BaseAgent {
    protected String lastThought = "";
    protected String lastAction = "";
    protected String lastObservation = "";

    @Override
    protected String think(String userMessage) {
        state = AgentState.THINKING;
        lastThought = analyze(userMessage);
        log.info("Step {} | Think: {}", currentStep, lastThought);
        if (shouldFinish(lastThought)) return "FINISH";
        lastAction = decideAction(lastThought);
        return lastAction;
    }

    @Override
    protected String observe(String observation) {
        lastObservation = observation;
        log.info("Step {} | Observe: {}", currentStep, observation);
        return lastObservation;
    }

    protected abstract String analyze(String input);
    protected abstract boolean shouldFinish(String thought);
    protected abstract String decideAction(String thought);
}