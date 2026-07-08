package com.protean.copilot.handler.context;

/**
 * 表示当前打开的文件信息。
 */
public record CurrentFile(
    String path,
    String name,
    String fileType,
    String text
) {}
