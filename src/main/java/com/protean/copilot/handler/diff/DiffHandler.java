package com.protean.copilot.handler.diff;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.handler.core.MessageHandler;

import java.util.List;

/**
 * Diff 与文件操作的消息处理器。
 * 将前端发来的 6 种 diff 消息路由到对应的子处理器。
 */
public class DiffHandler implements MessageHandler {

    private static final Logger LOG = Logger.getInstance(DiffHandler.class);

    private final DiffRequestDispatcher dispatcher;
    private final String[] supportedTypes;

    public DiffHandler(HandlerContext context) {
        Gson gson = new Gson();
        DiffFileOperations fileOps = new DiffFileOperations(context);
        DiffBrowserBridge bridge = new DiffBrowserBridge(context.browser, gson);
        List<DiffActionHandler> handlers = List.of(
            new SimpleDiffDisplayHandler(context.project, gson, fileOps),
            new InteractiveDiffMessageHandler(context, gson, bridge, fileOps),
            new EditableDiffHandler(context, gson, bridge, fileOps)
        );
        this.dispatcher = new DiffRequestDispatcher(handlers);
        this.supportedTypes = dispatcher.getAllSupportedTypes();
    }

    @Override
    public List<String> getSupportedTypes() {
        return List.of(supportedTypes);
    }

    @Override
    public boolean handle(String type, String content) {
        boolean handled = dispatcher.dispatch(type, content);
        if (!handled) {
            LOG.debug("No diff handler for type: " + type);
        }
        return handled;
    }
}
