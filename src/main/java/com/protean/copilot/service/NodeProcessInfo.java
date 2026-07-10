package com.protean.copilot.service;

/**
 * DTO describing a single Node.js process surfaced to the webview's process panel.
 * Mirrors the structure used by jetbrains-cc-gui so frontend contracts stay aligned.
 */
public final class NodeProcessInfo {

    public enum Kind {
        DAEMON,
        CHANNEL,
        ORPHAN
    }

    private final String id;
    private final Kind kind;
    private final String provider;
    private final long pid;
    private final boolean alive;
    private final long startedAtMs;
    private final long uptimeMs;
    private final String command;
    private final long heapUsedBytes;
    private final int activeRequestCount;
    private final String channelId;
    private final String sessionId;
    private final String tabName;
    private final boolean orphan;

    private NodeProcessInfo(Builder builder) {
        this.id = builder.id;
        this.kind = builder.kind;
        this.provider = builder.provider;
        this.pid = builder.pid;
        this.alive = builder.alive;
        this.startedAtMs = builder.startedAtMs;
        this.uptimeMs = builder.uptimeMs;
        this.command = builder.command;
        this.heapUsedBytes = builder.heapUsedBytes;
        this.activeRequestCount = builder.activeRequestCount;
        this.channelId = builder.channelId;
        this.sessionId = builder.sessionId;
        this.tabName = builder.tabName;
        this.orphan = builder.kind == Kind.ORPHAN;
    }

    public String getId() { return id; }
    public Kind getKind() { return kind; }
    public String getProvider() { return provider; }
    public long getPid() { return pid; }
    public boolean isAlive() { return alive; }
    public long getStartedAtMs() { return startedAtMs; }
    public long getUptimeMs() { return uptimeMs; }
    public String getCommand() { return command; }
    public long getHeapUsedBytes() { return heapUsedBytes; }
    public int getActiveRequestCount() { return activeRequestCount; }
    public String getChannelId() { return channelId; }
    public String getSessionId() { return sessionId; }
    public String getTabName() { return tabName; }
    public boolean isOrphan() { return orphan; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private Kind kind;
        private String provider;
        private long pid;
        private boolean alive = true;
        private long startedAtMs = -1L;
        private long uptimeMs = 0L;
        private String command;
        private long heapUsedBytes = -1L;
        private int activeRequestCount = 0;
        private String channelId;
        private String sessionId;
        private String tabName;

        public Builder id(String id) { this.id = id; return this; }
        public Builder kind(Kind kind) { this.kind = kind; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder pid(long pid) { this.pid = pid; return this; }
        public Builder alive(boolean alive) { this.alive = alive; return this; }
        public Builder startedAtMs(long startedAtMs) { this.startedAtMs = startedAtMs; return this; }
        public Builder uptimeMs(long uptimeMs) { this.uptimeMs = uptimeMs; return this; }
        public Builder command(String command) { this.command = command; return this; }
        public Builder heapUsedBytes(long heapUsedBytes) { this.heapUsedBytes = heapUsedBytes; return this; }
        public Builder activeRequestCount(int activeRequestCount) { this.activeRequestCount = activeRequestCount; return this; }
        public Builder channelId(String channelId) { this.channelId = channelId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder tabName(String tabName) { this.tabName = tabName; return this; }

        public NodeProcessInfo build() {
            if (kind == null) {
                throw new IllegalStateException("NodeProcessInfo requires kind");
            }
            if (id == null || id.isEmpty()) {
                String suffix = channelId != null ? channelId : (sessionId != null ? sessionId : "");
                id = kind.name().toLowerCase() + "-" + pid + (suffix.isEmpty() ? "" : "-" + suffix);
            }
            return new NodeProcessInfo(this);
        }
    }
}
