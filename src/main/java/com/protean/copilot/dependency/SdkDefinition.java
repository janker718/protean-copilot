package com.protean.copilot.dependency;

import java.util.Collections;
import java.util.List;

public enum SdkDefinition {

    CLAUDE_SDK(
        "claude-sdk",
        "Claude Code SDK",
        "@anthropic-ai/claude-agent-sdk",
        "0.2.58",
        List.of("@anthropic-ai/sdk", "@anthropic-ai/bedrock-sdk"),
        List.of("0.2.88", "0.2.81", "0.2.58"),
        "Claude AI provider runtime dependency."
    ),

    CODEX_SDK(
        "codex-sdk",
        "Codex SDK",
        "@openai/codex-sdk",
        "0.143.0",
        Collections.emptyList(),
        List.of("0.143.0", "0.142.5", "0.142.4"),
        "Codex AI provider runtime dependency."
    );

    private final String id;
    private final String displayName;
    private final String npmPackage;
    private final String lockedVersion;
    private final List<String> dependencies;
    private final List<String> fallbackVersions;
    private final String description;

    SdkDefinition(
        String id,
        String displayName,
        String npmPackage,
        String lockedVersion,
        List<String> dependencies,
        List<String> fallbackVersions,
        String description
    ) {
        this.id = id;
        this.displayName = displayName;
        this.npmPackage = npmPackage;
        this.lockedVersion = lockedVersion;
        this.dependencies = dependencies;
        this.fallbackVersions = fallbackVersions;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNpmPackage() {
        return npmPackage;
    }

    public String getLockedVersion() {
        return lockedVersion;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<String> getFallbackVersions() {
        return fallbackVersions;
    }

    public String getDescription() {
        return description;
    }

    public String getRequestedVersion(String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) {
            return lockedVersion;
        }
        return requestedVersion.trim();
    }

    public List<String> getAllPackages(String requestedVersion) {
        String resolvedVersion = getRequestedVersion(requestedVersion);
        if (dependencies.isEmpty()) {
            return List.of(npmPackage + "@" + resolvedVersion);
        }
        java.util.ArrayList<String> packages = new java.util.ArrayList<>();
        packages.add(npmPackage + "@" + resolvedVersion);
        packages.addAll(dependencies);
        return packages;
    }

    public static SdkDefinition fromId(String id) {
        for (SdkDefinition definition : values()) {
            if (definition.id.equals(id)) {
                return definition;
            }
        }
        return null;
    }
}
