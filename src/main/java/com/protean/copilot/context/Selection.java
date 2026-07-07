package com.protean.copilot.context;

/**
 * 表示编辑器中的文本选区。
 */
public record Selection(
    int startLine,
    int endLine,
    String text
) {}
