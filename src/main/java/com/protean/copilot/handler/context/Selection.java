package com.protean.copilot.handler.context;

/**
 * 表示编辑器中的文本选区。
 */
public record Selection(
    int startLine,
    int endLine,
    String text
) {}
