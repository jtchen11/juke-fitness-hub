package com.gym.ai.agent.model;
public class AgentResult {
    private String content;
    private boolean success;
    private int stepsUsed;
    private AgentState state;
    public AgentResult() {}
    public AgentResult(String content, boolean success, int stepsUsed, AgentState state) {
        this.content = content; this.success = success; this.stepsUsed = stepsUsed; this.state = state;
    }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public int getStepsUsed() { return stepsUsed; }
    public void setStepsUsed(int stepsUsed) { this.stepsUsed = stepsUsed; }
    public AgentState getState() { return state; }
    public void setState(AgentState state) { this.state = state; }
}