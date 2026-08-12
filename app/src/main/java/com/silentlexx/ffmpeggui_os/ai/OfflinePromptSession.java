package com.silentlexx.ffmpeggui_os.ai;

public final class OfflinePromptSession {

    private final long createdAtMillis;
    private final String sessionToken;
    private final String inputPath;
    private final String outputPath;

    public OfflinePromptSession(long createdAtMillis, String sessionToken,
                                String inputPath, String outputPath) {
        this.createdAtMillis = createdAtMillis;
        this.sessionToken = sessionToken;
        this.inputPath = inputPath;
        this.outputPath = outputPath;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public String getInputPath() {
        return inputPath;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getOutputPath() {
        return outputPath;
    }
}
