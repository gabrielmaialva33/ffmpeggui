package com.silentlexx.ffmpeggui_os.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Locks FFmpeg MediaCodec video encoders to the requested Android component class. */
public final class HardwareCodecCommand {

    static final String SOFTWARE_POLICY_OPTION = "-ffmpeggui-codec-policy";
    static final String SOFTWARE_POLICY_VALUE = "system-software";
    private static final Pattern SAFE_COMPONENT = Pattern.compile("[A-Za-z0-9._-]{1,160}");
    private static final ComponentResolver SYSTEM_COMPONENT_RESOLVER =
            new ComponentResolver() {
                @Override
                public String selectHardware(String mime, int width, int height,
                                             double frameRate) {
                    return HardwareCodecSelector.selectHardwareEncoder(
                            mime, width, height, frameRate);
                }

                @Override
                public String selectSystemSoftware(String mime, int width, int height,
                                                   double frameRate) {
                    return HardwareCodecSelector.selectSystemSoftwareEncoder(
                            mime, width, height, frameRate);
                }

                @Override
                public boolean isHardware(String mime, String component, int width,
                                          int height, double frameRate) {
                    return HardwareCodecSelector.isHardwareEncoderComponent(
                            mime, component, width, height, frameRate);
                }

                @Override
                public boolean isSystemSoftware(String mime, String component,
                                                int width, int height, double frameRate) {
                    return HardwareCodecSelector.isSystemSoftwareEncoderComponent(
                            mime, component, width, height, frameRate);
                }
            };

    public static boolean usesMediaCodecEncoder(String command)
            throws HardwareCodecException {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        return findEncoderCodecToken(tokenize(command)) >= 0;
    }

    public static String lockAvailableEncoder(String command, int width, int height,
                                              double frameRate)
            throws HardwareCodecException {
        return lockEncoder(command, width, height, frameRate, false);
    }

    /** Performs the final lock and removes every app-internal policy token. */
    public static String finalizeForExecution(String command, int width, int height,
                                              double frameRate)
            throws HardwareCodecException {
        return lockEncoder(command, width, height, frameRate, true);
    }

    private static String lockEncoder(String command, int width, int height,
                                      double frameRate, boolean finalPass)
            throws HardwareCodecException {
        return lockEncoder(command, width, height, frameRate, finalPass,
                SYSTEM_COMPONENT_RESOLVER);
    }

    static String lockEncoder(String command, int width, int height,
                              double frameRate, boolean finalPass,
                              ComponentResolver resolver)
            throws HardwareCodecException {
        if (command == null || command.trim().isEmpty()) {
            return command;
        }

        List<Token> tokens = tokenize(command);
        ParsedPolicy parsedPolicy = parseEncoderPolicy(command, tokens);
        int codecTokenIndex = findEncoderCodecToken(tokens);
        if (codecTokenIndex < 0) {
            if (parsedPolicy.explicit) {
                throw new HardwareCodecException(
                        "The system software policy requires one MediaCodec video encoder");
            }
            return command;
        }
        String codec = tokens.get(codecTokenIndex).value;

        int inputTokenIndex = findToken(tokens, "-i");
        if (inputTokenIndex >= 0 && codecTokenIndex < inputTokenIndex) {
            throw new HardwareCodecException(
                    "Use -hwaccel mediacodec before -i for hardware decoding");
        }

        CodecSpec codecSpec = CodecSpec.forFfmpegName(codec);
        if (codecSpec == null) {
            throw new HardwareCodecException("Unsupported MediaCodec video encoder");
        }
        String component = finalPass ? singleCodecName(tokens) : null;
        boolean existingMatches = component != null &&
                (parsedPolicy.policy == EncoderPolicy.SYSTEM_SOFTWARE
                        ? resolver.isSystemSoftware(codecSpec.mime, component,
                        width, height, frameRate)
                        : resolver.isHardware(codecSpec.mime, component,
                        width, height, frameRate));
        if (!existingMatches) {
            component = parsedPolicy.policy == EncoderPolicy.SYSTEM_SOFTWARE
                    ? resolver.selectSystemSoftware(codecSpec.mime, width, height, frameRate)
                    : resolver.selectHardware(codecSpec.mime, width, height, frameRate);
        }
        if (component == null) {
            throw new HardwareCodecException(String.format(Locale.US,
                    "No %s %s encoder supports the requested video shape",
                    parsedPolicy.policy == EncoderPolicy.SYSTEM_SOFTWARE
                            ? "system software" : "hardware",
                    codecSpec.label));
        }
        String locked = lockToComponent(command, codec, component);
        String normalized = forcePlanarEncoderInput(locked);
        return finalPass ? removeEncoderPolicy(normalized) : normalized;
    }

    private static String forcePlanarEncoderInput(String command)
            throws HardwareCodecException {
        List<Token> tokens = tokenize(command);
        int codecTokenIndex = findEncoderCodecToken(tokens);
        if (codecTokenIndex < 1) {
            throw new HardwareCodecException("The MediaCodec encoder changed during validation");
        }

        int lastInputValueIndex = -1;
        for (int i = 0; i < codecTokenIndex - 1; i++) {
            if (!"-i".equals(tokens.get(i).value)) {
                continue;
            }
            if (i + 1 >= tokens.size()) {
                throw new HardwareCodecException("-i is missing its input");
            }
            lastInputValueIndex = ++i;
        }

        int insertionIndex = tokens.get(codecTokenIndex - 1).start;
        List<Range> removals = pixelFormatRanges(
                command, tokens, lastInputValueIndex + 1);
        int removedBeforeInsertion = 0;
        for (Range range : removals) {
            if (range.end <= insertionIndex) {
                removedBeforeInsertion += range.end - range.start;
            }
        }
        StringBuilder result = new StringBuilder(command);
        removals.sort(Comparator.comparingInt((Range range) -> range.start).reversed());
        for (Range range : removals) {
            result.delete(range.start, range.end);
        }
        result.insert(insertionIndex - removedBeforeInsertion, "-pix_fmt yuv420p ");
        return result.toString().trim();
    }

    private static List<Range> pixelFormatRanges(String command, List<Token> tokens,
                                                 int outputStartTokenIndex)
            throws HardwareCodecException {
        List<Range> ranges = new ArrayList<>();
        for (int i = outputStartTokenIndex; i < tokens.size(); i++) {
            Token option = tokens.get(i);
            String lowerOption = option.value.toLowerCase(Locale.US);
            int equals = lowerOption.indexOf('=');
            String optionName = equals >= 0
                    ? lowerOption.substring(0, equals) : lowerOption;
            if (!"-pix_fmt".equals(optionName) &&
                    !optionName.startsWith("-pix_fmt:")) {
                continue;
            }

            int end;
            if (equals >= 0) {
                if (equals + 1 >= option.value.length()) {
                    throw new HardwareCodecException("-pix_fmt is missing its value");
                }
                end = option.end;
            } else {
                if (i + 1 >= tokens.size() || tokens.get(i + 1).value.startsWith("-")) {
                    throw new HardwareCodecException("-pix_fmt is missing its value");
                }
                end = tokens.get(++i).end;
            }
            int start = option.start;
            while (start > 0 && Character.isWhitespace(command.charAt(start - 1))) {
                start--;
            }
            ranges.add(new Range(start, end));
        }
        return ranges;
    }

    static String lockToComponent(String command, String codec, String component)
            throws HardwareCodecException {
        if (component == null || !SAFE_COMPONENT.matcher(component).matches()) {
            throw new HardwareCodecException("Android returned an invalid codec component name");
        }

        List<Token> tokens = tokenize(command);
        int codecTokenIndex = findEncoderCodecToken(tokens);
        if (codecTokenIndex < 0 || !codec.equals(tokens.get(codecTokenIndex).value)) {
            throw new HardwareCodecException("The MediaCodec encoder changed during validation");
        }

        int insertionIndex = tokens.get(codecTokenIndex).end;
        List<Range> removals = codecNameRanges(command, tokens);
        int removedBeforeInsertion = 0;
        for (Range range : removals) {
            if (range.end <= insertionIndex) {
                removedBeforeInsertion += range.end - range.start;
            }
        }

        StringBuilder result = new StringBuilder(command);
        removals.sort(Comparator.comparingInt((Range range) -> range.start).reversed());
        for (Range range : removals) {
            result.delete(range.start, range.end);
        }
        result.insert(insertionIndex - removedBeforeInsertion,
                " -codec_name " + component);
        return result.toString().trim();
    }

    private static int findEncoderCodecToken(List<Token> tokens)
            throws HardwareCodecException {
        int found = -1;
        int videoOrAmbiguousSelectors = 0;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            CodecOptionKind optionKind = classifyCodecSelectionOption(
                    tokens.get(i).value);
            if (optionKind == CodecOptionKind.NONE) {
                continue;
            }
            if (optionKind == CodecOptionKind.VIDEO ||
                    optionKind == CodecOptionKind.AMBIGUOUS) {
                videoOrAmbiguousSelectors++;
            }
            String codec = tokens.get(i + 1).value;
            if (CodecSpec.forFfmpegName(codec) == null) {
                continue;
            }
            if (optionKind == CodecOptionKind.NON_VIDEO) {
                throw new HardwareCodecException(
                        "A MediaCodec video encoder cannot use an audio or subtitle selector");
            }
            if (optionKind == CodecOptionKind.AMBIGUOUS) {
                throw new HardwareCodecException(
                        "Use an explicit -c:v selector for a MediaCodec video encoder");
            }
            if (found >= 0) {
                throw new HardwareCodecException(
                        "Only one MediaCodec video encoder is supported per command");
            }
            found = i + 1;
        }
        if (found >= 0 && videoOrAmbiguousSelectors != 1) {
            throw new HardwareCodecException(
                    "Only one video codec selector is allowed with a MediaCodec encoder");
        }
        return found;
    }

    static String mimeForEncoder(String codec) {
        CodecSpec spec = CodecSpec.forFfmpegName(codec);
        return spec == null ? null : spec.mime;
    }

    static boolean requestsSystemSoftwareEncoder(String command)
            throws HardwareCodecException {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        return parseEncoderPolicy(command, tokenize(command)).policy
                == EncoderPolicy.SYSTEM_SOFTWARE;
    }

    private static CodecOptionKind classifyCodecSelectionOption(String option) {
        String lower = option.toLowerCase(Locale.US);
        if ("-vcodec".equals(lower)) {
            return CodecOptionKind.VIDEO;
        }
        if (lower.startsWith("-vcodec:")) {
            String specifier = lower.substring("-vcodec:".length());
            int separator = specifier.indexOf(':');
            String streamType = separator >= 0
                    ? specifier.substring(0, separator) : specifier;
            if ("a".equals(streamType) || "s".equals(streamType)
                    || "d".equals(streamType) || "t".equals(streamType)) {
                return CodecOptionKind.NON_VIDEO;
            }
            return streamType.matches("v|[0-9]+")
                    ? CodecOptionKind.VIDEO : CodecOptionKind.AMBIGUOUS;
        }
        if ("-c".equals(lower) || "-codec".equals(lower)) {
            return CodecOptionKind.AMBIGUOUS;
        }

        String streamSpecifier;
        if (lower.startsWith("-c:")) {
            streamSpecifier = lower.substring(3);
        } else if (lower.startsWith("-codec:")) {
            streamSpecifier = lower.substring(7);
        } else {
            return CodecOptionKind.NONE;
        }

        int separator = streamSpecifier.indexOf(':');
        String streamType = separator >= 0
                ? streamSpecifier.substring(0, separator) : streamSpecifier;
        if ("v".equals(streamType)) {
            return CodecOptionKind.VIDEO;
        }
        if ("a".equals(streamType) || "s".equals(streamType)
                || "d".equals(streamType) || "t".equals(streamType)) {
            return CodecOptionKind.NON_VIDEO;
        }
        return CodecOptionKind.AMBIGUOUS;
    }

    private static int findToken(List<Token> tokens, String value) {
        for (int i = 0; i < tokens.size(); i++) {
            if (value.equals(tokens.get(i).value)) {
                return i;
            }
        }
        return -1;
    }

    private static ParsedPolicy parseEncoderPolicy(String command, List<Token> tokens)
            throws HardwareCodecException {
        ParsedPolicy parsed = new ParsedPolicy(EncoderPolicy.HARDWARE, false, null);
        for (int i = 0; i < tokens.size(); i++) {
            Token option = tokens.get(i);
            String lower = option.value.toLowerCase(Locale.US);
            if (!lower.startsWith(SOFTWARE_POLICY_OPTION)) {
                continue;
            }
            if (parsed.explicit) {
                throw new HardwareCodecException(
                        "Only one FFmpegGUI codec policy is allowed");
            }

            String value;
            int end;
            if (SOFTWARE_POLICY_OPTION.equals(lower)) {
                if (i + 1 >= tokens.size()) {
                    throw new HardwareCodecException(
                            "The FFmpegGUI codec policy is missing its value");
                }
                value = tokens.get(++i).value.toLowerCase(Locale.US);
                end = tokens.get(i).end;
            } else if (lower.startsWith(SOFTWARE_POLICY_OPTION + "=")) {
                value = lower.substring((SOFTWARE_POLICY_OPTION + "=").length());
                end = option.end;
            } else {
                throw new HardwareCodecException("Malformed FFmpegGUI codec policy");
            }

            if (!SOFTWARE_POLICY_VALUE.equals(value)) {
                throw new HardwareCodecException("Unknown FFmpegGUI codec policy");
            }
            int start = option.start;
            while (start > 0 && Character.isWhitespace(command.charAt(start - 1))) {
                start--;
            }
            parsed = new ParsedPolicy(EncoderPolicy.SYSTEM_SOFTWARE, true,
                    new Range(start, end));
        }
        return parsed;
    }

    static String removeEncoderPolicy(String command)
            throws HardwareCodecException {
        ParsedPolicy parsed = parseEncoderPolicy(command, tokenize(command));
        if (!parsed.explicit || parsed.range == null) {
            return command;
        }
        StringBuilder result = new StringBuilder(command);
        result.delete(parsed.range.start, parsed.range.end);
        return result.toString().trim();
    }

    private static String singleCodecName(List<Token> tokens) {
        String component = null;
        for (int i = 0; i < tokens.size(); i++) {
            Token option = tokens.get(i);
            String lowerOption = option.value.toLowerCase(Locale.US);
            int equals = lowerOption.indexOf('=');
            String optionName = equals >= 0
                    ? lowerOption.substring(0, equals) : lowerOption;
            if (!"-codec_name".equals(optionName) &&
                    !optionName.startsWith("-codec_name:")) {
                continue;
            }
            if (component != null) {
                return null;
            }

            String candidate;
            if (equals >= 0) {
                candidate = option.value.substring(equals + 1);
            } else if (i + 1 < tokens.size()) {
                candidate = tokens.get(++i).value;
            } else {
                return null;
            }
            if (!SAFE_COMPONENT.matcher(candidate).matches()) {
                return null;
            }
            component = candidate;
        }
        return component;
    }

    private static List<Range> codecNameRanges(String command, List<Token> tokens)
            throws HardwareCodecException {
        List<Range> ranges = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token option = tokens.get(i);
            String lowerOption = option.value.toLowerCase(Locale.US);
            int equals = lowerOption.indexOf('=');
            String optionName = equals >= 0
                    ? lowerOption.substring(0, equals) : lowerOption;
            if (!"-codec_name".equals(optionName) &&
                    !optionName.startsWith("-codec_name:")) {
                continue;
            }

            int end;
            if (equals >= 0) {
                end = option.end;
            } else {
                if (i + 1 >= tokens.size()) {
                    throw new HardwareCodecException("-codec_name is missing its component");
                }
                end = tokens.get(++i).end;
            }
            int start = option.start;
            while (start > 0 && Character.isWhitespace(command.charAt(start - 1))) {
                start--;
            }
            ranges.add(new Range(start, end));
        }
        return ranges;
    }

    private static List<Token> tokenize(String command) throws HardwareCodecException {
        List<Token> tokens = new ArrayList<>();
        int length = command.length();
        int index = 0;
        while (index < length) {
            while (index < length && Character.isWhitespace(command.charAt(index))) {
                index++;
            }
            if (index >= length) {
                break;
            }
            int start = index;
            boolean quoted = false;
            StringBuilder value = new StringBuilder();
            while (index < length) {
                char c = command.charAt(index);
                if (c == '"') {
                    quoted = !quoted;
                    index++;
                } else if (!quoted && Character.isWhitespace(c)) {
                    break;
                } else {
                    value.append(c);
                    index++;
                }
            }
            if (quoted) {
                throw new HardwareCodecException("The command contains an unmatched quote");
            }
            tokens.add(new Token(value.toString(), start, index));
        }
        return tokens;
    }

    private static final class Token {
        final String value;
        final int start;
        final int end;

        Token(String value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    private static final class Range {
        final int start;
        final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    interface ComponentResolver {
        String selectHardware(String mime, int width, int height, double frameRate);

        String selectSystemSoftware(String mime, int width, int height, double frameRate);

        boolean isHardware(String mime, String component, int width, int height,
                           double frameRate);

        boolean isSystemSoftware(String mime, String component, int width, int height,
                                 double frameRate);
    }

    private static final class ParsedPolicy {
        final EncoderPolicy policy;
        final boolean explicit;
        final Range range;

        ParsedPolicy(EncoderPolicy policy, boolean explicit, Range range) {
            this.policy = policy;
            this.explicit = explicit;
            this.range = range;
        }
    }

    private enum EncoderPolicy {
        HARDWARE,
        SYSTEM_SOFTWARE
    }

    private enum CodecOptionKind {
        NONE,
        VIDEO,
        NON_VIDEO,
        AMBIGUOUS
    }

    private enum CodecSpec {
        H264("h264_mediacodec", HardwareCodecSelector.MIME_H264, "H.264"),
        HEVC("hevc_mediacodec", HardwareCodecSelector.MIME_HEVC, "HEVC"),
        VP9("vp9_mediacodec", HardwareCodecSelector.MIME_VP9, "VP9"),
        AV1("av1_mediacodec", HardwareCodecSelector.MIME_AV1, "AV1");

        final String ffmpegName;
        final String mime;
        final String label;

        CodecSpec(String ffmpegName, String mime, String label) {
            this.ffmpegName = ffmpegName;
            this.mime = mime;
            this.label = label;
        }

        static CodecSpec forFfmpegName(String codec) {
            for (CodecSpec spec : values()) {
                if (spec.ffmpegName.equals(codec)) {
                    return spec;
                }
            }
            return null;
        }
    }

    public static final class HardwareCodecException extends Exception {
        public HardwareCodecException(String message) {
            super(message);
        }
    }

    private HardwareCodecCommand() {
        throw new AssertionError("No instances");
    }
}
