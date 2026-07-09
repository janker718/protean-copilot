package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

final class InputHistoryHandler {

    private static final Logger LOG = Logger.getInstance(InputHistoryHandler.class);
    private static final int MAX_ITEMS = 200;

    private final HandlerContext context;
    private final Gson gson = new Gson();

    InputHistoryHandler(HandlerContext context) {
        this.context = context;
    }

    void handleGetInputHistory() {
        JsonObject payload = loadStore();
        push("window.onInputHistoryLoaded", payload);
    }

    void handleRecordInputHistory(String content) {
        try {
            JsonObject store = loadStore();
            JsonArray items = store.getAsJsonArray("items");
            JsonObject counts = store.getAsJsonObject("counts");
            JsonObject timestamps = store.getAsJsonObject("timestamps");
            JsonArray input = JsonParser.parseString(content).getAsJsonArray();
            String now = Instant.now().toString();
            for (JsonElement element : input) {
                if (element == null || element.isJsonNull()) {
                    continue;
                }
                String text = element.getAsString().trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (!contains(items, text)) {
                    items.add(text);
                }
                int nextCount = counts.has(text) ? counts.get(text).getAsInt() + 1 : 1;
                counts.addProperty(text, nextCount);
                timestamps.addProperty(text, now);
            }
            trimStore(items, counts, timestamps);
            writeStore(store);
        } catch (Exception e) {
            LOG.warn("[InputHistoryHandler] Failed to record history: " + e.getMessage(), e);
        }
    }

    void handleDeleteInputHistoryItem(String content) {
        try {
            JsonObject store = loadStore();
            String text = normalizeRawString(content);
            if (!text.isEmpty()) {
                remove(store.getAsJsonArray("items"), text);
                store.getAsJsonObject("counts").remove(text);
                store.getAsJsonObject("timestamps").remove(text);
                writeStore(store);
            }
        } catch (Exception e) {
            LOG.warn("[InputHistoryHandler] Failed to delete history item: " + e.getMessage(), e);
        }
    }

    void handleClearInputHistory() {
        try {
            writeStore(emptyStore());
        } catch (Exception e) {
            LOG.warn("[InputHistoryHandler] Failed to clear history: " + e.getMessage(), e);
        }
    }

    private JsonObject loadStore() {
        try {
            Path path = getHistoryPath();
            if (!Files.exists(path)) {
                return emptyStore();
            }
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject parsed = JsonParser.parseString(raw).getAsJsonObject();
            if (!parsed.has("items")) parsed.add("items", new JsonArray());
            if (!parsed.has("counts")) parsed.add("counts", new JsonObject());
            if (!parsed.has("timestamps")) parsed.add("timestamps", new JsonObject());
            return parsed;
        } catch (Exception e) {
            LOG.warn("[InputHistoryHandler] Failed to load store: " + e.getMessage(), e);
            return emptyStore();
        }
    }

    private void writeStore(JsonObject store) throws IOException {
        Path path = getHistoryPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, gson.toJson(store), StandardCharsets.UTF_8);
    }

    private Path getHistoryPath() {
        return Paths.get(System.getProperty("user.home"), ".codemoss", "inputHistory.json");
    }

    private JsonObject emptyStore() {
        JsonObject object = new JsonObject();
        object.add("items", new JsonArray());
        object.add("counts", new JsonObject());
        object.add("timestamps", new JsonObject());
        return object;
    }

    private static boolean contains(JsonArray items, String text) {
        for (JsonElement item : items) {
            if (text.equals(item.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static void remove(JsonArray items, String text) {
        for (int i = 0; i < items.size(); i++) {
            if (text.equals(items.get(i).getAsString())) {
                items.remove(i);
                return;
            }
        }
    }

    private void trimStore(JsonArray items, JsonObject counts, JsonObject timestamps) {
        if (items.size() <= MAX_ITEMS) {
            return;
        }
        Map<String, Integer> ranking = new LinkedHashMap<>();
        for (JsonElement item : items) {
            String text = item.getAsString();
            ranking.put(text, counts.has(text) ? counts.get(text).getAsInt() : 0);
        }
        ranking.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
            .limit(Math.max(0, ranking.size() - MAX_ITEMS))
            .map(Map.Entry::getKey)
            .forEach(text -> {
                remove(items, text);
                counts.remove(text);
                timestamps.remove(text);
            });
    }

    private static String normalizeRawString(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                return JsonParser.parseString(trimmed).getAsString().trim();
            } catch (Exception ignored) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private void push(String callback, JsonObject payload) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript(callback, context.escapeJs(payload.toString())));
    }
}
