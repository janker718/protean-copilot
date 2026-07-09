package com.protean.copilot.handler.core;

/**
 * Base class for message handlers.
 * Provides shared access to {@link HandlerContext} and common helper methods.
 */
public abstract class BaseMessageHandler implements MessageHandler {

    protected final HandlerContext context;

    protected BaseMessageHandler(HandlerContext context) {
        this.context = context;
    }

    protected void callJavaScript(String functionName, String... args) {
        context.callJavaScript(functionName, args);
    }

    protected void executeJavaScript(String jsCode) {
        context.executeJavaScriptOnEDT(jsCode);
    }

    protected String escapeJs(String str) {
        return context.escapeJs(str);
    }

    protected boolean matchesType(String type, String... supportedTypes) {
        for (String supportedType : supportedTypes) {
            if (supportedType.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
