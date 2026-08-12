package com.silentlexx.ffmpeggui_os.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tokenizes the app's double-quoted FFmpeg command format into a direct argv list. */
public final class FfmpegCommandParser {

    public static List<String> parseArguments(String commandLine) throws ParseException {
        if (commandLine == null) {
            throw new ParseException("The FFmpeg command is missing");
        }

        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean tokenStarted = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                tokenStarted = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (tokenStarted) {
                    arguments.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
            } else {
                current.append(c);
                tokenStarted = true;
            }
        }

        if (quoted) {
            throw new ParseException("The FFmpeg command contains an unmatched double quote");
        }
        if (tokenStarted) {
            arguments.add(current.toString());
        }
        return Collections.unmodifiableList(arguments);
    }

    public static FileArguments extractFileArguments(String commandLine) throws ParseException {
        List<String> arguments = parseArguments(commandLine);
        List<String> inputs = new ArrayList<>();
        int lastInputIndex = -1;
        for (int i = 0; i < arguments.size(); i++) {
            if (!"-i".equals(arguments.get(i))) {
                continue;
            }
            if (i + 1 >= arguments.size()) {
                throw new ParseException("The FFmpeg command has -i without an input");
            }
            lastInputIndex = ++i;
            inputs.add(arguments.get(i));
        }

        if (inputs.isEmpty() || arguments.isEmpty() ||
                lastInputIndex == arguments.size() - 1) {
            throw new ParseException("The FFmpeg command is missing an input or output");
        }
        String output = arguments.get(arguments.size() - 1);
        if (output.isEmpty()) {
            throw new ParseException("The FFmpeg command is missing an input or output");
        }
        return new FileArguments(inputs, output);
    }

    public static final class FileArguments {
        private final List<String> inputs;
        private final String output;

        private FileArguments(List<String> inputs, String output) {
            this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
            this.output = output;
        }

        public List<String> getInputs() {
            return inputs;
        }

        public String getOutput() {
            return output;
        }
    }

    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    private FfmpegCommandParser() {
        throw new AssertionError("No instances");
    }
}
