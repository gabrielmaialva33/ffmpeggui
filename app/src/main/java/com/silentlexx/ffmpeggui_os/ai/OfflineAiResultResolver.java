package com.silentlexx.ffmpeggui_os.ai;

public final class OfflineAiResultResolver {

    private static final int MAX_CLIPBOARD_CHARS = 32_768;

    public static String requireClipboardText(boolean hasTextMimeType, CharSequence text)
            throws ClipboardException {
        if (!hasTextMimeType) {
            throw new ClipboardException("The clipboard does not contain text");
        }
        if (text == null || text.toString().trim().isEmpty()) {
            throw new ClipboardException("The clipboard text is empty");
        }
        if (text.length() > MAX_CLIPBOARD_CHARS) {
            throw new ClipboardException("The clipboard result is too large");
        }
        return text.toString();
    }

    public static String resolve(String pastedResult, OfflinePromptSession session,
                                 String currentInput, String currentOutput)
            throws AiPlaceholderCommand.ParseException,
            FfmpegCommandValidator.ValidationException {
        String input = session == null ? currentInput : session.getInputPath();
        String output = session == null ? currentOutput : session.getOutputPath();
        String placeholderCommand = AiPlaceholderCommand.parseModelResult(pastedResult);
        if (session == null) {
            return AiPlaceholderCommand.materialize(placeholderCommand, input, output);
        }
        return AiPlaceholderCommand.materialize(placeholderCommand, input, output,
                OfflinePlaceholderBinding.inputPlaceholder(session.getSessionToken()),
                OfflinePlaceholderBinding.outputPlaceholder(session.getSessionToken()));
    }

    public static final class ClipboardException extends Exception {
        public ClipboardException(String message) {
            super(message);
        }
    }

    private OfflineAiResultResolver() {
        throw new AssertionError("No instances");
    }
}
