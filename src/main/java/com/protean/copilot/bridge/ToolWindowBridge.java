package com.protean.copilot.bridge;

public interface ToolWindowBridge {
    void refreshContext();

    void submitPrompt(String prompt);
}
