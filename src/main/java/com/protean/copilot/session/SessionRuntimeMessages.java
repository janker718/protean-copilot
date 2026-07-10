package com.protean.copilot.session;

import org.jetbrains.annotations.Nullable;

/**
 * Centralizes user-facing runtime, resume, and permission error messages so
 * Java/WebView diagnostics can match on stable wording across providers.
 */
public final class SessionRuntimeMessages {

    private SessionRuntimeMessages() {
    }

    public static String bridgeUnavailable(@Nullable String provider) {
        return displayProvider(provider)
            + " runtime unavailable. Check Node.js and the installed SDK dependency, then retry.";
    }

    public static String requestFailed(@Nullable String provider, @Nullable Throwable throwable) {
        return classify(displayProvider(provider), null, rootMessage(throwable), "query", null);
    }

    public static String historySessionIdMissing() {
        return "Resume failed: missing history sessionId.";
    }

    public static String historyResumeFailed(@Nullable String provider, @Nullable Throwable throwable) {
        return classify(displayProvider(provider), "SESSION_RESUME_FAILED", rootMessage(throwable), "resume", null);
    }

    public static String historyResumeFailed(@Nullable String provider, @Nullable String detail) {
        return classify(displayProvider(provider), "SESSION_RESUME_FAILED", detail, "resume", null);
    }

    public static String newSessionCreateFailed(@Nullable Throwable throwable) {
        return "Failed to start a new session. " + sanitizeDetail(rootMessage(throwable));
    }

    public static String newSessionCreated() {
        return "New session ready";
    }

    public static String sdkUnavailable(
        @Nullable String provider,
        @Nullable String version,
        @Nullable String hint,
        @Nullable String importError
    ) {
        StringBuilder builder = new StringBuilder(displayProvider(provider))
            .append(" runtime unavailable");
        if (version != null && !version.isBlank() && !"unknown".equals(version)) {
            builder.append(" (version ").append(version).append(')');
        }
        String detail = hint != null && !hint.isBlank() ? hint : importError;
        if (detail != null && !detail.isBlank()) {
            builder.append(". ").append(sanitizeDetail(detail));
        } else {
            builder.append(". Check Node.js and the installed SDK dependency, then retry.");
        }
        return builder.toString();
    }

    public static String bridgeProcessExited(@Nullable String provider, int exitCode) {
        return displayProvider(provider)
            + " runtime exited unexpectedly (exit code " + exitCode
            + "). Check Node.js and the installed SDK dependency, then retry.";
    }

    public static String runtimeRecovered(@Nullable String provider) {
        return displayProvider(provider) + " runtime recovered with warnings";
    }

    public static String bridgeError(
        @Nullable String provider,
        @Nullable String code,
        @Nullable String message,
        @Nullable String phase,
        @Nullable String hint
    ) {
        return classify(displayProvider(provider), code, message, phase, hint);
    }

    static String rootMessage(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message;
    }

    private static String classify(
        String provider,
        @Nullable String code,
        @Nullable String message,
        @Nullable String phase,
        @Nullable String hint
    ) {
        String detail = sanitizeDetail(message);
        String normalizedCode = code == null ? "" : code.trim().toUpperCase();
        String normalizedPhase = phase == null ? "" : phase.trim().toLowerCase();

        if (isPermissionDenied(normalizedCode, detail)) {
            return appendHint(provider + " permission request was denied. " + detail, hint);
        }
        if (isSandboxDenied(normalizedCode, detail)) {
            return appendHint(provider + " sandbox denied the requested operation. " + detail, hint);
        }
        if (isResumeFailure(normalizedCode, normalizedPhase, detail)) {
            return appendHint(provider + " session resume failed. " + detail, hint);
        }

        StringBuilder builder = new StringBuilder(provider)
            .append(" runtime error [")
            .append(normalizedCode.isBlank() ? "UNKNOWN" : normalizedCode)
            .append("] ")
            .append(detail);
        if (!normalizedPhase.isBlank()) {
            builder.append(" (").append(normalizedPhase).append(')');
        }
        return appendHint(builder.toString(), hint);
    }

    private static String appendHint(String base, @Nullable String hint) {
        if (hint == null || hint.isBlank()) {
            return base;
        }
        return base + " " + sanitizeDetail(hint);
    }

    private static boolean isPermissionDenied(String code, String detail) {
        return code.contains("PERMISSION_DENIED")
            || containsIgnoreCase(detail, "permission denied")
            || containsIgnoreCase(detail, "approval denied")
            || containsIgnoreCase(detail, "permission request denied");
    }

    private static boolean isSandboxDenied(String code, String detail) {
        return code.contains("SANDBOX_DENIED")
            || containsIgnoreCase(detail, "sandbox denied")
            || containsIgnoreCase(detail, "read-only sandbox")
            || containsIgnoreCase(detail, "write access");
    }

    private static boolean isResumeFailure(String code, String phase, String detail) {
        return "resume".equals(phase)
            || code.contains("RESUME")
            || containsIgnoreCase(detail, "thread/resume")
            || containsIgnoreCase(detail, "resume failed");
    }

    private static boolean containsIgnoreCase(String text, String token) {
        return text.toLowerCase().contains(token.toLowerCase());
    }

    private static String sanitizeDetail(@Nullable String detail) {
        if (detail == null || detail.isBlank()) {
            return "Unknown error.";
        }
        String trimmed = detail.trim();
        return trimmed.endsWith(".") ? trimmed : trimmed + ".";
    }

    private static String displayProvider(@Nullable String provider) {
        if (provider == null || provider.isBlank()) {
            return "Provider";
        }
        String trimmed = provider.trim();
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase();
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
