package com.protean.copilot.session;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Owns provider history loading and runtime snapshot reconciliation.
 */
public class SessionMessageOrchestrator {

    private static final Logger LOG = Logger.getInstance(SessionMessageOrchestrator.class);
    private static final Gson GSON = new Gson();

    public interface SessionHistoryAccess {
        List<JsonObject> getProviderSessionMessages(String provider, String sessionId, String cwd);
    }

    private final ChatSession session;
    private final MessageParser messageParser;
    private final SessionHistoryAccess historyAccess;
    private final Supplier<SessionCallbackAdapter> callbackSupplier;
    private final MessageMerger messageMerger;
    private final ReplayDeduplicator replayDeduplicator;

    public SessionMessageOrchestrator(
        ChatSession session,
        MessageParser messageParser,
        SessionHistoryAccess historyAccess,
        Supplier<SessionCallbackAdapter> callbackSupplier
    ) {
        this.session = session;
        this.messageParser = messageParser;
        this.historyAccess = historyAccess;
        this.callbackSupplier = callbackSupplier;
        this.messageMerger = new MessageMerger();
        this.replayDeduplicator = new ReplayDeduplicator();
    }

    public CompletableFuture<Void> loadFromServer() {
        if (session.getSessionId() == null || session.getSessionId().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        session.setLoading(true);
        session.setError(null);
        return CompletableFuture.supplyAsync(() -> historyAccess.getProviderSessionMessages(
            session.getProvider(),
            session.getSessionId(),
            session.getCwd()
        )).thenAccept(serverMessages -> {
            List<ChatSession.Message> parsedMessages = parseMessages(serverMessages != null ? serverMessages : List.of());
            session.replaceMessages(parsedMessages);
            ensureSummary(parsedMessages);
            pushMessagesToFrontend(parsedMessages);
        }).whenComplete((v, ex) -> {
            session.setLoading(false);
            session.setError(ex == null ? null : SessionRuntimeMessages.historyResumeFailed(
                session.getProvider(),
                rootMessage(ex)
            ));
        });
    }

    public boolean handleBridgeEvent(String functionName, String... args) {
        SessionCallbackAdapter callback = callbackSupplier.get();
        if (callback == null && requiresCallback(functionName)) {
            return false;
        }
        switch (functionName) {
            case "onBridgeReady" -> callback.onBridgeReady(arg(args, 0), arg(args, 1));
            case "updateSessionId" -> handleSessionIdUpdate(arg(args, 0), arg(args, 1));
            case "onStreamStart" -> {
                replayDeduplicator.reset();
                callback.onStreamStart();
            }
            case "onContentDelta" -> {
                String novelDelta = replayDeduplicator.consumeContentDelta(arg(args, 0));
                if (!novelDelta.isEmpty()) {
                    callback.onContentDelta(novelDelta);
                }
            }
            case "onThinkingDelta" -> {
                String novelDelta = replayDeduplicator.consumeThinkingDelta(arg(args, 0));
                if (!novelDelta.isEmpty()) {
                    callback.onThinkingDelta(novelDelta);
                }
            }
            case "onBlockReset" -> callback.onBlockReset();
            case "onToolUse" -> callback.onToolUse(arg(args, 0), arg(args, 1));
            case "onToolResult" -> callback.onToolResult(arg(args, 0), arg(args, 1));
            case "updateMessages" -> ingestProviderMessagesJson(arg(args, 0));
            case "updateStatus" -> callback.updateStatus(arg(args, 0));
            case "onStreamEnd" -> callback.onStreamEnd();
            case "onStreamingHeartbeat" -> callback.onStreamingHeartbeat();
            case "addErrorMessage" -> callback.showError(arg(args, 0));
            case "showLoading" -> {
                return true;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    void ingestProviderMessagesJson(String messagesJson) {
        if (messagesJson == null || messagesJson.isBlank()) {
            return;
        }
        JsonElement parsedElement = JsonParser.parseString(messagesJson);
        if (!parsedElement.isJsonArray()) {
            return;
        }

        List<ChatSession.Message> previousMessages = session.getMessages();
        List<ChatSession.Message> nextMessages = parseMessages(parsedElement.getAsJsonArray());
        prepareReplayDedupe(previousMessages, nextMessages);
        session.replaceMessages(nextMessages);
        ensureSummary(nextMessages);
        pushMessagesToFrontend(nextMessages);
    }

    private List<ChatSession.Message> parseMessages(List<JsonObject> rawMessages) {
        JsonArray array = new JsonArray();
        for (JsonObject rawMessage : rawMessages) {
            array.add(rawMessage);
        }
        return parseMessages(array);
    }

    private List<ChatSession.Message> parseMessages(JsonArray rawMessages) {
        List<ChatSession.Message> parsedMessages = new ArrayList<>();
        for (int i = 0; i < rawMessages.size(); i++) {
            JsonElement rawElement = rawMessages.get(i);
            if (!rawElement.isJsonObject()) {
                continue;
            }
            JsonObject payload = rawElement.getAsJsonObject();
            ChatSession.Message parsed = messageParser.parseServerMessage(payload);
            if (parsed == null) {
                continue;
            }
            if (payload.has("timestamp") && !payload.get("timestamp").isJsonNull()) {
                try {
                    parsed.timestamp = Instant.parse(payload.get("timestamp").getAsString()).toEpochMilli();
                } catch (Exception ignored) {
                }
            }
            parsedMessages.add(parsed);
        }
        return parsedMessages;
    }

    private void prepareReplayDedupe(List<ChatSession.Message> previousMessages, List<ChatSession.Message> nextMessages) {
        ChatSession.Message previousAssistant = findLastAssistant(previousMessages);
        ChatSession.Message nextAssistant = findLastAssistant(nextMessages);
        if (previousAssistant == null || nextAssistant == null || previousAssistant.raw == null || nextAssistant.raw == null) {
            return;
        }

        JsonObject mergedRaw = messageMerger.mergeAssistantMessage(previousAssistant.raw, nextAssistant.raw);
        String previousText = ReplayDeduplicator.extractTextContent(previousAssistant.raw);
        String previousThinking = ReplayDeduplicator.extractThinkingContent(previousAssistant.raw);
        String mergedText = ReplayDeduplicator.extractTextContent(mergedRaw);
        String mergedThinking = ReplayDeduplicator.extractThinkingContent(mergedRaw);

        if (!mergedText.isEmpty() && mergedText.length() >= previousText.length()) {
            replayDeduplicator.beginContentReplay(mergedText, previousText.length());
        }
        if (!mergedThinking.isEmpty() && mergedThinking.length() >= previousThinking.length()) {
            replayDeduplicator.beginThinkingReplay(mergedThinking, previousThinking.length());
        }

        nextAssistant.raw = mergedRaw;
        nextAssistant.content = messageParser.extractMessageContent(mergedRaw);
    }

    private ChatSession.Message findLastAssistant(List<ChatSession.Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatSession.Message message = messages.get(i);
            if (message.type == ChatSession.Message.Type.ASSISTANT) {
                return message;
            }
        }
        return null;
    }

    private void ensureSummary(List<ChatSession.Message> messages) {
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            return;
        }
        for (ChatSession.Message message : messages) {
            if (message.type != ChatSession.Message.Type.USER || message.content == null || message.content.isBlank()) {
                continue;
            }
            String summary = message.content.length() > 45 ? message.content.substring(0, 45) + "..." : message.content;
            session.setSummary(summary);
            return;
        }
    }

    private void pushMessagesToFrontend(List<ChatSession.Message> messages) {
        SessionCallbackAdapter callback = callbackSupplier.get();
        if (callback != null) {
            callback.updateMessages(buildFrontendMessagesJson(messages));
        }
    }

    private String buildFrontendMessagesJson(List<ChatSession.Message> messages) {
        JsonArray array = new JsonArray();
        for (ChatSession.Message message : messages) {
            JsonObject item = new JsonObject();
            item.addProperty("type", message.type.name().toLowerCase());
            item.addProperty("content", message.content == null ? "" : message.content);
            item.addProperty("timestamp", Instant.ofEpochMilli(message.timestamp).toString());
            if (message.raw != null) {
                item.add("raw", message.raw);
            }
            array.add(item);
        }
        return GSON.toJson(array);
    }

    private void handleSessionIdUpdate(String newSessionId, String requestSessionId) {
        if (newSessionId == null || newSessionId.isBlank()) {
            return;
        }
        String currentSessionId = session.getSessionId();
        if (requestSessionId == null || requestSessionId.isBlank()
            || currentSessionId == null || currentSessionId.isBlank()
            || currentSessionId.equals(requestSessionId)
            || currentSessionId.equals(newSessionId)) {
            session.setSessionInfo(newSessionId, null);
        }
    }

    private String arg(String[] args, int index) {
        return args != null && index < args.length && args[index] != null ? args[index] : "";
    }

    private boolean requiresCallback(String functionName) {
        return !"updateSessionId".equals(functionName) && !"updateMessages".equals(functionName);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
