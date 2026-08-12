package com.silentlexx.ffmpeggui_os.ai;

import java.util.ArrayList;
import java.util.List;

public final class FfmpegCommandValidator {

    private static final int MAX_COMMAND_LENGTH = 16_384;
    private static final String FORBIDDEN_SHELL_CHARS = ";|&<>`$#";
    private static final String UNSUPPORTED_PARSER_CHARS = "'\\";

    public static String validate(String rawCommand, String selectedInput, String selectedOutput)
            throws ValidationException {
        if (rawCommand == null) {
            throw new ValidationException("The generated command is missing");
        }
        String command = rawCommand.trim();
        if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH) {
            throw new ValidationException("The generated command has an invalid length");
        }
        if (selectedInput == null || selectedInput.trim().isEmpty()) {
            throw new ValidationException("Select an input file before generating a command");
        }
        String input = selectedInput.trim();
        if (selectedOutput == null || selectedOutput.trim().isEmpty()) {
            throw new ValidationException("Choose an output file before generating a command");
        }
        String expectedOutput = selectedOutput.trim();
        validateCharacters(command);
        validatePathCharacters(input, "input");
        validatePathCharacters(expectedOutput, "output");
        if (!expectedOutput.startsWith("/") || expectedOutput.contains("://")) {
            throw new ValidationException("The selected output must be an absolute local path");
        }

        List<String> tokens = tokenize(command);
        if (tokens.isEmpty() || !"ffmpeg".equals(tokens.get(0))) {
            throw new ValidationException("The generated command must start with ffmpeg");
        }
        if (tokens.size() < 4) {
            throw new ValidationException("The generated FFmpeg command is incomplete");
        }

        int inputOptionCount = 0;
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String lowerToken = token.toLowerCase(java.util.Locale.US);
            if ("-progress".equals(lowerToken) || lowerToken.startsWith("-progress=")) {
                throw new ValidationException("The -progress output is unsupported by this app");
            }
            if (lowerToken.startsWith("pipe:") || lowerToken.startsWith("fd:")) {
                throw new ValidationException("Pipe and file-descriptor protocols are forbidden");
            }
            if ("-i".equals(token)) {
                inputOptionCount++;
                if (i + 1 >= tokens.size() || !input.equals(tokens.get(i + 1))) {
                    throw new ValidationException(
                            "The command must use the selected input exactly after -i");
                }
            } else if (input.equals(token) && !"-i".equals(tokens.get(i - 1))) {
                    throw new ValidationException("The selected input cannot also be an output");
            }
        }
        if (inputOptionCount != 1) {
            throw new ValidationException(
                    "The command must contain exactly one -i input");
        }

        String output = tokens.get(tokens.size() - 1);
        if (input.equals(output)) {
            throw new ValidationException("The selected input cannot also be the output");
        }
        if (!expectedOutput.equals(output)) {
            throw new ValidationException(
                    "The final argument must be the selected local output path");
        }
        return command;
    }

    private static void validateCharacters(String command) throws ValidationException {
        for (int i = 0; i < command.length(); ) {
            int codePoint = validatedCodePointAt(command, i, "command");
            if (codePoint < 0x20 || codePoint == 0x7f || codePoint == 0x2028 ||
                    codePoint == 0x2029 || codePoint == 0xfeff ||
                    Character.getType(codePoint) == Character.FORMAT) {
                throw new ValidationException(
                        "The generated command contains a control or hidden format character");
            }
            if (codePoint <= Character.MAX_VALUE &&
                    FORBIDDEN_SHELL_CHARS.indexOf((char) codePoint) >= 0) {
                throw new ValidationException(
                        "The generated command contains a forbidden shell operator");
            }
            if (codePoint <= Character.MAX_VALUE &&
                    UNSUPPORTED_PARSER_CHARS.indexOf((char) codePoint) >= 0) {
                throw new ValidationException(
                        "The generated command contains a quote or escape unsupported by this app");
            }
            i += Character.charCount(codePoint);
        }
    }

    private static void validatePathCharacters(String path, String label)
            throws ValidationException {
        if (path.indexOf('"') >= 0) {
            throw new ValidationException("The selected " + label +
                    " path contains an unsupported quote");
        }
        for (int i = 0; i < path.length(); ) {
            int codePoint = validatedCodePointAt(path, i, label + " path");
            if (codePoint < 0x20 || codePoint == 0x7f ||
                    (codePoint <= Character.MAX_VALUE &&
                            FORBIDDEN_SHELL_CHARS.indexOf((char) codePoint) >= 0) ||
                    (codePoint <= Character.MAX_VALUE &&
                            UNSUPPORTED_PARSER_CHARS.indexOf((char) codePoint) >= 0) ||
                    codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0xfeff ||
                    Character.getType(codePoint) == Character.FORMAT) {
                throw new ValidationException(
                        "The selected " + label + " path contains unsupported characters");
            }
            i += Character.charCount(codePoint);
        }
    }

    private static int validatedCodePointAt(String value, int offset, String label)
            throws ValidationException {
        char first = value.charAt(offset);
        if (Character.isSurrogate(first) &&
                (!Character.isHighSurrogate(first) || offset + 1 >= value.length() ||
                        !Character.isLowSurrogate(value.charAt(offset + 1)))) {
            throw new ValidationException(
                    "The generated " + label + " contains an invalid Unicode surrogate");
        }
        return Character.codePointAt(value, offset);
    }

    private static List<String> tokenize(String command) throws ValidationException {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (Character.isWhitespace(c) && !quoted) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }
        if (quoted) {
            throw new ValidationException("The generated command contains an unmatched quote");
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    public static final class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    private FfmpegCommandValidator() {
        throw new AssertionError("No instances");
    }
}
