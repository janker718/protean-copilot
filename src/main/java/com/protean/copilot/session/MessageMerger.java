package com.protean.copilot.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Merges assistant snapshots without dropping previously rendered tool blocks.
 */
public class MessageMerger {

    public JsonObject mergeAssistantMessage(JsonObject existingRaw, JsonObject newRaw) {
        if (newRaw == null) {
            return existingRaw != null ? existingRaw.deepCopy() : null;
        }
        if (existingRaw == null) {
            return newRaw.deepCopy();
        }

        JsonObject merged = existingRaw.deepCopy();
        for (Map.Entry<String, JsonElement> entry : newRaw.entrySet()) {
            if ("message".equals(entry.getKey())) {
                continue;
            }
            merged.add(entry.getKey(), entry.getValue());
        }

        JsonObject incomingMessage = newRaw.has("message") && newRaw.get("message").isJsonObject()
            ? newRaw.getAsJsonObject("message")
            : null;
        if (incomingMessage == null) {
            return merged;
        }

        JsonObject mergedMessage = merged.has("message") && merged.get("message").isJsonObject()
            ? merged.getAsJsonObject("message")
            : new JsonObject();

        for (Map.Entry<String, JsonElement> entry : incomingMessage.entrySet()) {
            if ("content".equals(entry.getKey())) {
                continue;
            }
            mergedMessage.add(entry.getKey(), entry.getValue());
        }

        mergeAssistantContentArray(mergedMessage, incomingMessage);
        merged.add("message", mergedMessage);
        return merged;
    }

    private void mergeAssistantContentArray(JsonObject targetMessage, JsonObject incomingMessage) {
        JsonArray baseContent = targetMessage.has("content") && targetMessage.get("content").isJsonArray()
            ? targetMessage.getAsJsonArray("content")
            : new JsonArray();
        Map<String, Integer> indexByKey = buildContentIndex(baseContent);
        Set<Integer> consumedUnkeyedIndexes = new HashSet<>();
        JsonArray incomingContent = incomingMessage.has("content") && incomingMessage.get("content").isJsonArray()
            ? incomingMessage.getAsJsonArray("content")
            : null;

        if (incomingContent == null) {
            targetMessage.add("content", baseContent);
            return;
        }

        for (int i = 0; i < incomingContent.size(); i++) {
            JsonElement element = incomingContent.get(i);
            JsonElement copy = element.deepCopy();
            if (element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                String key = getContentBlockKey(block);
                if (key != null && indexByKey.containsKey(key)) {
                    baseContent.set(indexByKey.get(key), copy);
                    continue;
                }
                if (key != null) {
                    baseContent.add(copy);
                    indexByKey.put(key, baseContent.size() - 1);
                    continue;
                }

                int matchingIndex = findMatchingUnkeyedBlockIndex(baseContent, block, consumedUnkeyedIndexes);
                if (matchingIndex >= 0) {
                    baseContent.set(matchingIndex, mergeUnkeyedBlock(baseContent.get(matchingIndex).getAsJsonObject(), block));
                    consumedUnkeyedIndexes.add(matchingIndex);
                    continue;
                }

                int lastSameTypeIndex = findLastSameTypeBlockIndex(baseContent, block);
                if (lastSameTypeIndex >= 0) {
                    baseContent.set(lastSameTypeIndex, mergeUnkeyedBlock(baseContent.get(lastSameTypeIndex).getAsJsonObject(), block));
                    continue;
                }
            }
            baseContent.add(copy);
        }

        targetMessage.add("content", baseContent);
    }

    private Map<String, Integer> buildContentIndex(JsonArray contentArray) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < contentArray.size(); i++) {
            JsonElement element = contentArray.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            String key = getContentBlockKey(element.getAsJsonObject());
            if (key != null && !index.containsKey(key)) {
                index.put(key, i);
            }
        }
        return index;
    }

    private String getContentBlockKey(JsonObject block) {
        if (block.has("id") && block.get("id").isJsonPrimitive()) {
            return block.get("id").getAsString();
        }
        if (block.has("tool_use_id") && block.get("tool_use_id").isJsonPrimitive()) {
            return "tool_result:" + block.get("tool_use_id").getAsString();
        }
        return null;
    }

    private int findMatchingUnkeyedBlockIndex(
        JsonArray baseContent,
        JsonObject incomingBlock,
        Set<Integer> consumedUnkeyedIndexes
    ) {
        String incomingType = getBlockType(incomingBlock);
        if (incomingType == null) {
            return -1;
        }
        for (int i = 0; i < baseContent.size(); i++) {
            if (consumedUnkeyedIndexes.contains(i) || !baseContent.get(i).isJsonObject()) {
                continue;
            }
            JsonObject existingBlock = baseContent.get(i).getAsJsonObject();
            if (getContentBlockKey(existingBlock) != null || !incomingType.equals(getBlockType(existingBlock))) {
                continue;
            }
            if (blocksLikelyRepresentSameSegment(existingBlock, incomingBlock)) {
                return i;
            }
        }
        return -1;
    }

    private JsonObject mergeUnkeyedBlock(JsonObject existingBlock, JsonObject incomingBlock) {
        String type = getBlockType(incomingBlock);
        JsonObject merged = incomingBlock.deepCopy();
        if ("text".equals(type)) {
            merged.addProperty("text", preferMoreCompleteContent(getText(existingBlock), getText(incomingBlock)));
            return merged;
        }
        if ("thinking".equals(type)) {
            String thinking = preferMoreCompleteContent(getThinking(existingBlock), getThinking(incomingBlock));
            if (thinking != null && !thinking.isEmpty()) {
                merged.addProperty("thinking", thinking);
                merged.addProperty("text", thinking);
            }
        }
        return merged;
    }

    private boolean blocksLikelyRepresentSameSegment(JsonObject existingBlock, JsonObject incomingBlock) {
        String type = getBlockType(incomingBlock);
        if (type == null || !type.equals(getBlockType(existingBlock))) {
            return false;
        }
        if ("text".equals(type)) {
            return textLooksRelated(getText(existingBlock), getText(incomingBlock));
        }
        if ("thinking".equals(type)) {
            String existingThinking = getThinking(existingBlock);
            String incomingThinking = getThinking(incomingBlock);
            if (existingThinking.isEmpty() || incomingThinking.isEmpty()) {
                return true;
            }
            return textLooksRelated(existingThinking, incomingThinking);
        }
        return existingBlock.equals(incomingBlock);
    }

    private int findLastSameTypeBlockIndex(JsonArray baseContent, JsonObject incomingBlock) {
        String incomingType = getBlockType(incomingBlock);
        if (incomingType == null) {
            return -1;
        }
        for (int i = baseContent.size() - 1; i >= 0; i--) {
            JsonElement element = baseContent.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject existingBlock = element.getAsJsonObject();
            if (getContentBlockKey(existingBlock) != null) {
                break;
            }
            if (incomingType.equals(getBlockType(existingBlock))) {
                return i;
            }
        }
        return -1;
    }

    private String getBlockType(JsonObject block) {
        if (block == null || !block.has("type") || block.get("type").isJsonNull()) {
            return null;
        }
        return block.get("type").getAsString();
    }

    private String getText(JsonObject block) {
        if (block != null && block.has("text") && !block.get("text").isJsonNull()) {
            return block.get("text").getAsString();
        }
        return "";
    }

    private String getThinking(JsonObject block) {
        if (block != null && block.has("thinking") && !block.get("thinking").isJsonNull()) {
            return block.get("thinking").getAsString();
        }
        return getText(block);
    }

    private String preferMoreCompleteContent(String existing, String incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return existing != null ? existing : "";
        }
        if (existing == null || existing.isEmpty()) {
            return incoming;
        }
        if (incoming.length() >= existing.length()) {
            return incoming;
        }
        return existing;
    }

    private boolean textLooksRelated(String existing, String incoming) {
        if (existing == null || existing.isEmpty() || incoming == null || incoming.isEmpty()) {
            return true;
        }
        return existing.startsWith(incoming)
            || incoming.startsWith(existing)
            || existing.contains(incoming)
            || incoming.contains(existing);
    }
}
