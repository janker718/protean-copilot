package com.protean.copilot.bridge;

public interface ToolWindowBridgeView {
    void setStatus(String message);

    void showOutput(String text);

    void clearPrompt();
}
