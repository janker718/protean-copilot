package com.protean.copilot.handler.diff;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.handler.core.MessageHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Diff 与文件操作的消息处理器。
 * 将前端发来的 6 种 diff 消息路由到对应的子处理器。
 */
public class DiffHandler implements MessageHandler {

    private static final Logger LOG = Logger.getInstance(DiffHandler.class);

    private final SimpleDiffDisplayHandler simpleHandler;
    private final InteractiveDiffHandler interactiveHandler;
    private final String[] supportedTypes;

    public DiffHandler(HandlerContext context) {
        Gson gson = new Gson();
        DiffFileOperations fileOps = new DiffFileOperations(context.project);
        DiffBrowserBridge bridge = new DiffBrowserBridge(context.browser, gson);

        this.simpleHandler = new SimpleDiffDisplayHandler(context.project, gson, fileOps);
        this.interactiveHandler = new InteractiveDiffHandler(context.project, gson, bridge, fileOps);

        // 聚合所有支持的消息类型
        List<String> types = new ArrayList<>();
        for (String t : simpleHandler.getSupportedTypes()) types.add(t);
        for (String t : interactiveHandler.getSupportedTypes()) types.add(t);
        this.supportedTypes = types.toArray(new String[0]);
    }

    @Override
    public List<String> getSupportedTypes() {
        return List.of(supportedTypes);
    }

    @Override
    public boolean handle(String type, String content) {
        if (simpleHandler.handle(type, content)) return true;
        if (interactiveHandler.handle(type, content)) return true;
        LOG.debug("No diff handler for type: " + type);
        return false;
    }
}
