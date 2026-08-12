package com.silentlexx.ffmpeggui_os.ai;

public final class AiPromptBundle {

    private final String systemPrompt;
    private final String userPrompt;
    private final String clipboardPrompt;

    AiPromptBundle(String systemPrompt, String userPrompt, String clipboardPrompt) {
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.clipboardPrompt = clipboardPrompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public String getClipboardPrompt() {
        return clipboardPrompt;
    }
}
