package com.protean.copilot.provider.claude;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.protean.copilot.util.PathUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregates Claude project sessions into usage and cost statistics.
 */
class ClaudeUsageAggregator {

    private static final String ALL_PROJECTS = "all";
    private static final String UNKNOWN_MODEL = "unknown";
    private static final double ONE_MILLION = 1_000_000d;

    private static final Pricing DEFAULT_PRICING = new Pricing(3.0, 15.0, 3.75, 0.30);
    private static final Pricing OPUS_PRICING = new Pricing(15.0, 75.0, 18.75, 1.50);
    private static final Pricing OPUS_45_PRICING = new Pricing(5.0, 25.0, 6.25, 0.50);
    private static final Pricing FABLE_PRICING = new Pricing(10.0, 50.0, 12.5, 1.0);
    private static final Pricing HAIKU_PRICING = new Pricing(1.0, 5.0, 1.25, 0.10);

    private static final Map<String, Pricing> MODEL_PRICING = Map.ofEntries(
        Map.entry("claude-opus-4", OPUS_PRICING),
        Map.entry("claude-opus-4-1", OPUS_PRICING),
        Map.entry("claude-opus-4-20250514", OPUS_PRICING),
        Map.entry("claude-opus-4-5", OPUS_45_PRICING),
        Map.entry("claude-opus-4-6", OPUS_45_PRICING),
        Map.entry("claude-opus-4-7", OPUS_45_PRICING),
        Map.entry("claude-opus-4-8", OPUS_45_PRICING),
        Map.entry("claude-fable-5", FABLE_PRICING),
        Map.entry("claude-sonnet-4", DEFAULT_PRICING),
        Map.entry("claude-sonnet-4-5", DEFAULT_PRICING),
        Map.entry("claude-sonnet-4-6", DEFAULT_PRICING),
        Map.entry("claude-sonnet-4-20250514", DEFAULT_PRICING),
        Map.entry("claude-haiku-4", HAIKU_PRICING),
        Map.entry("claude-haiku-4-5", HAIKU_PRICING)
    );

    private final Path projectsDir;
    private final ClaudeHistoryParser parser;

    ClaudeUsageAggregator(Path projectsDir, ClaudeHistoryParser parser) {
        this.projectsDir = projectsDir;
        this.parser = parser;
    }

    ClaudeHistoryReader.ProjectStatistics getProjectStatistics(String projectPath, long cutoffTime) {
        ClaudeHistoryReader.ProjectStatistics stats = initEmptyStatistics(projectPath);
        try {
            List<ClaudeHistoryReader.SessionSummary> sessions = readSessions(projectPath);
            List<ClaudeHistoryReader.SessionSummary> filtered = cutoffTime > 0
                ? sessions.stream().filter(session -> session.timestamp >= cutoffTime).collect(Collectors.toList())
                : sessions;

            stats.totalSessions = filtered.size();
            stats.sessions = filtered.stream()
                .sorted(Comparator.comparingLong((ClaudeHistoryReader.SessionSummary session) -> session.timestamp).reversed())
                .collect(Collectors.toList());

            Map<String, MutableDailyUsage> daily = new LinkedHashMap<>();
            Map<String, MutableModelUsage> modelUsage = new LinkedHashMap<>();
            for (ClaudeHistoryReader.SessionSummary session : filtered) {
                mergeUsage(stats.totalUsage, session.usage);
                stats.estimatedCost += session.cost;
                accumulateDaily(daily, session);
                accumulateModel(modelUsage, session);
            }

            stats.dailyUsage = daily.values().stream()
                .map(MutableDailyUsage::toDailyUsage)
                .sorted(Comparator.comparing((ClaudeHistoryReader.DailyUsage usage) -> usage.date).reversed())
                .collect(Collectors.toList());

            stats.byModel = modelUsage.values().stream()
                .map(MutableModelUsage::toModelUsage)
                .sorted(Comparator.comparingDouble((ClaudeHistoryReader.ModelUsage usage) -> usage.totalCost).reversed())
                .collect(Collectors.toList());

            fillWeeklyComparison(stats.weeklyComparison, filtered);
        } catch (Exception ignored) {
        }
        return stats;
    }

    private ClaudeHistoryReader.ProjectStatistics initEmptyStatistics(String projectPath) {
        ClaudeHistoryReader.ProjectStatistics stats = new ClaudeHistoryReader.ProjectStatistics();
        stats.projectPath = projectPath;
        stats.projectName = resolveProjectName(projectPath);
        stats.totalUsage = new ClaudeHistoryReader.UsageData();
        stats.sessions = new ArrayList<>();
        stats.dailyUsage = new ArrayList<>();
        stats.byModel = new ArrayList<>();
        stats.weeklyComparison = new ClaudeHistoryReader.WeeklyComparison();
        stats.weeklyComparison.currentWeek = new ClaudeHistoryReader.WeeklyComparison.WeekData();
        stats.weeklyComparison.lastWeek = new ClaudeHistoryReader.WeeklyComparison.WeekData();
        stats.weeklyComparison.trends = new ClaudeHistoryReader.WeeklyComparison.Trends();
        stats.lastUpdated = System.currentTimeMillis();
        return stats;
    }

    private String resolveProjectName(String projectPath) {
        if (ALL_PROJECTS.equals(projectPath)) {
            return "All Projects";
        }
        if (projectPath == null || projectPath.isBlank()) {
            return "Unknown Project";
        }
        Path path = Path.of(projectPath);
        Path fileName = path.getFileName();
        return fileName == null ? projectPath : fileName.toString();
    }

    private List<ClaudeHistoryReader.SessionSummary> readSessions(String projectPath) throws IOException {
        if (ALL_PROJECTS.equals(projectPath)) {
            return readAllSessions();
        }

        if (projectPath == null || projectPath.isBlank()) {
            return List.of();
        }

        Path projectDir = projectsDir.resolve(PathUtils.sanitizePath(projectPath));
        if (!Files.isDirectory(projectDir)) {
            return List.of();
        }
        return readSessionsFromDir(projectDir);
    }

    private List<ClaudeHistoryReader.SessionSummary> readAllSessions() throws IOException {
        if (!Files.isDirectory(projectsDir)) {
            return List.of();
        }

        List<ClaudeHistoryReader.SessionSummary> sessions = new ArrayList<>();
        try (Stream<Path> paths = Files.list(projectsDir)) {
            for (Path dir : paths.filter(Files::isDirectory).toList()) {
                sessions.addAll(readSessionsFromDir(dir));
            }
        }
        return sessions;
    }

    private List<ClaudeHistoryReader.SessionSummary> readSessionsFromDir(Path projectDir) throws IOException {
        List<ClaudeHistoryReader.SessionSummary> sessions = new ArrayList<>();
        try (Stream<Path> paths = Files.list(projectDir)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".jsonl")).toList()) {
                ClaudeHistoryReader.SessionSummary summary = parseSessionFile(file);
                if (summary != null) {
                    sessions.add(summary);
                }
            }
        }
        return sessions;
    }

    private ClaudeHistoryReader.SessionSummary parseSessionFile(Path file) {
        ClaudeHistoryReader.UsageData usage = new ClaudeHistoryReader.UsageData();
        Set<String> countedMessageIds = new LinkedHashSet<>();
        String model = UNKNOWN_MODEL;
        String summary = null;
        long firstTimestamp = 0L;
        double cost = 0d;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                if (firstTimestamp == 0L) {
                    firstTimestamp = parser.parseTimestamp(readString(json, "timestamp"));
                }
                if (summary == null && "summary".equals(readString(json, "type"))) {
                    summary = readString(json, "summary");
                }

                JsonObject message = readObject(json, "message");
                String role = readString(message, "role");
                if (summary == null && "user".equals(role)) {
                    summary = parser.extractTextFromContent(message.get("content"));
                }

                if (!"assistant".equals(role)) {
                    continue;
                }

                String messageId = readString(message, "id");
                if (messageId != null && !countedMessageIds.add(messageId)) {
                    continue;
                }

                JsonObject usageJson = readObject(message, "usage");
                ClaudeHistoryReader.UsageData delta = readUsage(usageJson);
                if (delta.totalTokens == 0) {
                    continue;
                }

                String currentModel = readString(message, "model");
                if (currentModel != null && !currentModel.isBlank()) {
                    model = currentModel;
                }

                mergeUsage(usage, delta);
                cost += calculateCost(delta, model);
            }
        } catch (Exception e) {
            return null;
        }

        if (usage.totalTokens == 0) {
            return null;
        }

        ClaudeHistoryReader.SessionSummary session = new ClaudeHistoryReader.SessionSummary();
        session.sessionId = trimJsonl(file.getFileName().toString());
        session.timestamp = firstTimestamp;
        session.model = model;
        session.usage = usage;
        session.cost = cost;
        session.summary = summary == null ? "Untitled session" : summary;
        return session;
    }

    private void accumulateDaily(Map<String, MutableDailyUsage> daily, ClaudeHistoryReader.SessionSummary session) {
        String date = Instant.ofEpochMilli(session.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString();
        MutableDailyUsage item = daily.computeIfAbsent(date, ignored -> new MutableDailyUsage(date));
        item.sessions++;
        mergeUsage(item.usage, session.usage);
        item.cost += session.cost;
        item.modelsUsed.add(session.model);
    }

    private void accumulateModel(Map<String, MutableModelUsage> models, ClaudeHistoryReader.SessionSummary session) {
        String model = session.model == null || session.model.isBlank() ? UNKNOWN_MODEL : session.model;
        MutableModelUsage item = models.computeIfAbsent(model, MutableModelUsage::new);
        item.sessionCount++;
        item.totalCost += session.cost;
        item.totalTokens += session.usage.totalTokens;
        item.inputTokens += session.usage.inputTokens;
        item.outputTokens += session.usage.outputTokens;
        item.cacheCreationTokens += session.usage.cacheWriteTokens;
        item.cacheReadTokens += session.usage.cacheReadTokens;
    }

    private void fillWeeklyComparison(
        ClaudeHistoryReader.WeeklyComparison weeklyComparison,
        List<ClaudeHistoryReader.SessionSummary> sessions
    ) {
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int currentWeek = today.get(weekFields.weekOfWeekBasedYear());
        int currentYear = today.get(weekFields.weekBasedYear());
        int lastWeek = currentWeek == 1 ? today.minusWeeks(1).get(weekFields.weekOfWeekBasedYear()) : currentWeek - 1;
        int lastWeekYear = currentWeek == 1 ? today.minusWeeks(1).get(weekFields.weekBasedYear()) : currentYear;

        for (ClaudeHistoryReader.SessionSummary session : sessions) {
            LocalDate date = Instant.ofEpochMilli(session.timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
            int sessionWeek = date.get(weekFields.weekOfWeekBasedYear());
            int sessionYear = date.get(weekFields.weekBasedYear());
            if (sessionWeek == currentWeek && sessionYear == currentYear) {
                accumulateWeek(weeklyComparison.currentWeek, session);
            } else if (sessionWeek == lastWeek && sessionYear == lastWeekYear) {
                accumulateWeek(weeklyComparison.lastWeek, session);
            }
        }

        weeklyComparison.trends.sessions = percentageTrend(
            weeklyComparison.currentWeek.sessions,
            weeklyComparison.lastWeek.sessions
        );
        weeklyComparison.trends.cost = percentageTrend(
            weeklyComparison.currentWeek.cost,
            weeklyComparison.lastWeek.cost
        );
        weeklyComparison.trends.tokens = percentageTrend(
            weeklyComparison.currentWeek.tokens,
            weeklyComparison.lastWeek.tokens
        );
    }

    private void accumulateWeek(ClaudeHistoryReader.WeeklyComparison.WeekData week, ClaudeHistoryReader.SessionSummary session) {
        week.sessions++;
        week.cost += session.cost;
        week.tokens += session.usage.totalTokens;
    }

    private double percentageTrend(double current, double previous) {
        if (previous == 0d) {
            return current == 0d ? 0d : 100d;
        }
        return ((current - previous) / previous) * 100d;
    }

    private ClaudeHistoryReader.UsageData readUsage(JsonObject usageJson) {
        ClaudeHistoryReader.UsageData usage = new ClaudeHistoryReader.UsageData();
        if (usageJson == null) {
            return usage;
        }
        usage.inputTokens = readLong(usageJson, "input_tokens");
        usage.outputTokens = readLong(usageJson, "output_tokens");
        usage.cacheWriteTokens = readLong(usageJson, "cache_creation_input_tokens");
        usage.cacheReadTokens = readLong(usageJson, "cache_read_input_tokens");
        usage.totalTokens = usage.inputTokens + usage.outputTokens + usage.cacheWriteTokens + usage.cacheReadTokens;
        return usage;
    }

    private void mergeUsage(ClaudeHistoryReader.UsageData target, ClaudeHistoryReader.UsageData delta) {
        target.inputTokens += delta.inputTokens;
        target.outputTokens += delta.outputTokens;
        target.cacheWriteTokens += delta.cacheWriteTokens;
        target.cacheReadTokens += delta.cacheReadTokens;
        target.totalTokens += delta.totalTokens;
    }

    private double calculateCost(ClaudeHistoryReader.UsageData usage, String model) {
        Pricing pricing = resolvePricing(model);
        return (usage.inputTokens / ONE_MILLION) * pricing.inputPerMillion
            + (usage.outputTokens / ONE_MILLION) * pricing.outputPerMillion
            + (usage.cacheWriteTokens / ONE_MILLION) * pricing.cacheWritePerMillion
            + (usage.cacheReadTokens / ONE_MILLION) * pricing.cacheReadPerMillion;
    }

    private Pricing resolvePricing(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_PRICING;
        }
        return MODEL_PRICING.entrySet().stream()
            .filter(entry -> model.startsWith(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(DEFAULT_PRICING);
    }

    private static JsonObject readObject(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull() || !json.get(key).isJsonObject()) {
            return null;
        }
        return json.getAsJsonObject(key);
    }

    private static String readString(JsonObject json, String key) {
        return readString(json, key, null);
    }

    private static String readString(JsonObject json, String key, String defaultValue) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return json.get(key).getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static long readLong(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return json.get(key).getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String trimJsonl(String name) {
        return name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name;
    }

    private record Pricing(
        double inputPerMillion,
        double outputPerMillion,
        double cacheWritePerMillion,
        double cacheReadPerMillion
    ) {
    }

    private static final class MutableDailyUsage {
        private final String date;
        private int sessions;
        private final ClaudeHistoryReader.UsageData usage = new ClaudeHistoryReader.UsageData();
        private double cost;
        private final Set<String> modelsUsed = new LinkedHashSet<>();

        private MutableDailyUsage(String date) {
            this.date = date;
        }

        private ClaudeHistoryReader.DailyUsage toDailyUsage() {
            ClaudeHistoryReader.DailyUsage dailyUsage = new ClaudeHistoryReader.DailyUsage();
            dailyUsage.date = date;
            dailyUsage.sessions = sessions;
            dailyUsage.usage = usage;
            dailyUsage.cost = cost;
            dailyUsage.modelsUsed = new ArrayList<>(modelsUsed);
            return dailyUsage;
        }
    }

    private static final class MutableModelUsage {
        private final String model;
        private double totalCost;
        private long totalTokens;
        private long inputTokens;
        private long outputTokens;
        private long cacheCreationTokens;
        private long cacheReadTokens;
        private int sessionCount;

        private MutableModelUsage(String model) {
            this.model = model;
        }

        private ClaudeHistoryReader.ModelUsage toModelUsage() {
            ClaudeHistoryReader.ModelUsage modelUsage = new ClaudeHistoryReader.ModelUsage();
            modelUsage.model = model;
            modelUsage.totalCost = totalCost;
            modelUsage.totalTokens = totalTokens;
            modelUsage.inputTokens = inputTokens;
            modelUsage.outputTokens = outputTokens;
            modelUsage.cacheCreationTokens = cacheCreationTokens;
            modelUsage.cacheReadTokens = cacheReadTokens;
            modelUsage.sessionCount = sessionCount;
            return modelUsage;
        }
    }
}
