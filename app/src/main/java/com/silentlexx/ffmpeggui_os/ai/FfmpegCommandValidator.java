package com.silentlexx.ffmpeggui_os.ai;

import com.silentlexx.ffmpeggui_os.model.FfmpegCommandParser;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FfmpegCommandValidator {

    private static final int MAX_COMMAND_LENGTH = 16_384;
    private static final String FORBIDDEN_SHELL_CHARS = "|&<>`$#";
    private static final String UNSUPPORTED_PARSER_CHARS = "'\\";
    private static final Set<String> SIDE_FILE_FILTERS = new HashSet<>(
            java.util.Arrays.asList("movie", "amovie", "subtitles", "ass", "sendcmd",
                    "asendcmd", "zmq", "azmq"));

    public static String validate(String rawCommand, String selectedInput, String selectedOutput)
            throws ValidationException {
        return validate(rawCommand, selectedInput, selectedOutput, null);
    }

    public static String validate(String rawCommand, String selectedInput, String selectedOutput,
                                  Set<String> compiledFilters)
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
        validateCompiledFilters(tokens, compiledFilters);
        return command;
    }

    private static void validateCharacters(String command) throws ValidationException {
        boolean quoted = false;
        for (int i = 0; i < command.length(); ) {
            int codePoint = validatedCodePointAt(command, i, "command");
            if (codePoint == '"') {
                quoted = !quoted;
                i++;
                continue;
            }
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
            if (codePoint == ';' && !quoted) {
                throw new ValidationException(
                        "A filter-chain separator must be inside a double-quoted argument");
            }
            if (codePoint <= Character.MAX_VALUE &&
                    UNSUPPORTED_PARSER_CHARS.indexOf((char) codePoint) >= 0) {
                throw new ValidationException(
                        "The generated command contains a quote or escape unsupported by this app");
            }
            i += Character.charCount(codePoint);
        }
    }

    private static void validateCompiledFilters(List<String> tokens,
                                                Set<String> compiledFilters)
            throws ValidationException {
        if (compiledFilters == null) {
            return;
        }
        Set<String> normalized = new HashSet<>();
        for (String filter : FfmpegFilterInventory.safeForAi(compiledFilters)) {
            if (filter != null) {
                normalized.add(filter);
            }
        }
        for (int i = 1; i < tokens.size(); i++) {
            String option = tokens.get(i).toLowerCase(Locale.US);
            if (!isFilterOption(option)) {
                continue;
            }
            if (++i >= tokens.size()) {
                throw new ValidationException("Missing filtergraph after " + option);
            }
            validateFilterGraph(tokens.get(i), normalized);
        }
    }

    private static boolean isFilterOption(String option) {
        return "-vf".equals(option) || "-af".equals(option) ||
                "-lavfi".equals(option) || "-filter_complex".equals(option) ||
                "-filter".equals(option) ||
                option.matches("-filter(?::[a-z0-9?]+)+");
    }

    private static void validateFilterGraph(String graph, Set<String> compiledFilters)
            throws ValidationException {
        int offset = 0;
        boolean foundFilter = false;
        while (true) {
            offset = skipWhitespace(graph, offset);
            while (offset < graph.length() && graph.charAt(offset) == '[') {
                int end = graph.indexOf(']', offset + 1);
                if (end < 0 || end == offset + 1) {
                    throw new ValidationException("The generated filtergraph has an invalid label");
                }
                offset = skipWhitespace(graph, end + 1);
            }
            if (offset >= graph.length()) {
                if (foundFilter) {
                    return;
                }
                throw new ValidationException("The generated filtergraph is empty");
            }

            int nameStart = offset;
            while (offset < graph.length() && isFilterNameCharacter(graph.charAt(offset))) {
                offset++;
            }
            if (nameStart == offset) {
                throw new ValidationException("The generated filtergraph has an invalid filter name");
            }
            String filterName = graph.substring(nameStart, offset);
            if (offset < graph.length() && graph.charAt(offset) == '@') {
                offset++;
                int instanceStart = offset;
                while (offset < graph.length() &&
                        isFilterInstanceCharacter(graph.charAt(offset))) {
                    offset++;
                }
                if (instanceStart == offset) {
                    throw new ValidationException(
                            "The generated filtergraph has an invalid filter instance name");
                }
            }

            boolean swsFlagsDirective = !foundFilter && "sws_flags".equals(filterName);
            if (!swsFlagsDirective) {
                foundFilter = true;
                if (SIDE_FILE_FILTERS.contains(filterName)) {
                    throw new ValidationException(
                            "AI commands cannot use a filter that reads or writes a side file: " +
                                    filterName);
                }
                if (!compiledFilters.contains(filterName)) {
                    if (compiledFilters.isEmpty()) {
                        throw new ValidationException(
                                "The bundled FFmpeg filter inventory is unavailable; regenerate " +
                                        "the AI prompt before using filters");
                    }
                    throw new ValidationException(
                            "Unknown or unavailable FFmpeg filter: " + filterName);
                }
            }

            int separator = findFilterSeparator(graph, offset);
            if (separator < 0) {
                if (swsFlagsDirective) {
                    throw new ValidationException(
                            "sws_flags must be followed by a filter chain");
                }
                return;
            }
            char separatorChar = graph.charAt(separator);
            offset = separator + 1;
            if (skipWhitespace(graph, offset) >= graph.length()) {
                throw new ValidationException(
                        "The generated filtergraph ends with a chain separator");
            }
            if (swsFlagsDirective && separatorChar != ';') {
                throw new ValidationException(
                        "sws_flags must be separated from filters with a semicolon");
            }
        }
    }

    private static int findFilterSeparator(String graph, int offset)
            throws ValidationException {
        int bracketDepth = 0;
        for (int i = offset; i < graph.length(); i++) {
            char c = graph.charAt(i);
            if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                if (bracketDepth == 0) {
                    throw new ValidationException(
                            "The generated filtergraph has an unmatched label bracket");
                }
                bracketDepth--;
            } else if (bracketDepth == 0 && (c == ',' || c == ';')) {
                return i;
            }
        }
        if (bracketDepth != 0) {
            throw new ValidationException(
                    "The generated filtergraph has an unmatched label bracket");
        }
        return -1;
    }

    private static int skipWhitespace(String value, int offset) {
        while (offset < value.length() && Character.isWhitespace(value.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private static boolean isFilterNameCharacter(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    private static boolean isFilterInstanceCharacter(char c) {
        return c == '_' || c == '-' || c == '.' || Character.isLetterOrDigit(c);
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
        try {
            return FfmpegCommandParser.parseArguments(command);
        } catch (FfmpegCommandParser.ParseException e) {
            throw new ValidationException(e.getMessage());
        }
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
