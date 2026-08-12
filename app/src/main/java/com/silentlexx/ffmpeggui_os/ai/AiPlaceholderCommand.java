package com.silentlexx.ffmpeggui_os.ai;

import com.silentlexx.ffmpeggui_os.model.FfmpegCommandParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiPlaceholderCommand {

    public static final String INPUT_1 = "{{INPUT_1}}";
    public static final String OUTPUT_1 = "{{OUTPUT_1}}";
    static final String COMMAND_BEGIN = "FFMPEGGUI_COMMAND_V1_BEGIN";
    static final String COMMAND_END = "FFMPEGGUI_COMMAND_V1_END";

    private static final int MAX_RESULT_LENGTH = 32_768;
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([A-Za-z0-9_]+)\\}\\}");
    private static final Pattern SCHEME =
            Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    private static final Pattern PATH_LIKE_FILE = Pattern.compile(
            "(?i).+\\.(?:mp4|mkv|mov|avi|webm|mp3|m4a|aac|wav|flac|ogg|opus|ts|m3u8|" +
                    "txt|json|xml|log|srt|ass|ssa|vtt|ttf|otf|jpg|jpeg|png|gif|bin|key)$");
    private static final Pattern MATH_DIVISION = Pattern.compile(
            "(?:iw|ih|ow|oh|in_w|in_h|out_w|out_h|dar|sar|[0-9]+(?:\\.[0-9]+)?)/" +
                    "(?:iw|ih|ow|oh|in_w|in_h|out_w|out_h|dar|sar|[0-9]+(?:\\.[0-9]+)?)");
    private static final Set<String> NO_VALUE_OPTIONS = new HashSet<>(Arrays.asList(
            "-hide_banner", "-y", "-n", "-nostdin", "-an", "-vn", "-sn", "-dn",
            "-shortest", "-copyts", "-start_at_zero", "-bitexact", "-re", "-copyinkf",
            "-autorotate", "-noautorotate", "-accurate_seek", "-noaccurate_seek",
            "-benchmark", "-benchmark_all", "-stats", "-nostats"
    ));
    private static final Set<String> VALUE_OPTIONS = new HashSet<>(Arrays.asList(
            "-ss", "-sseof", "-t", "-to", "-itsoffset", "-stream_loop", "-f", "-map",
            "-map_channel", "-map_metadata", "-map_chapters", "-hwaccel", "-hwaccel_device",
            "-hwaccel_output_format", "-vf", "-af", "-filter_complex",
            "-filter_complex_threads", "-lavfi", "-vcodec", "-acodec", "-scodec",
            "-pix_fmt", "-r", "-s", "-aspect", "-preset", "-crf", "-qp", "-g", "-bf",
            "-ac", "-ar", "-movflags", "-threads", "-thread_queue_size", "-codec_name",
            "-ndk_codec", "-ndk_async", "-bitrate_mode", "-global_quality", "-maxrate",
            "-minrate", "-bufsize", "-max_muxing_queue_size", "-fps_mode", "-vsync",
            "-async", "-shortest_buf_duration", "-muxdelay", "-muxpreload",
            "-cpu-used", "-row-mt", "-deadline", "-usage", "-tile-columns",
            "-tile-rows", "-aq-mode", "-tune", "-compression_level"
    ));
    private static final Set<String> SAFE_SINGLE_FILE_FORMATS = new HashSet<>(Arrays.asList(
            "3g2", "3gp", "aac", "ac3", "adts", "aiff", "alaw", "amr", "asf",
            "au", "avi", "caf", "dts", "eac3", "flac", "flv", "g722", "g723_1",
            "h261", "h263", "h264", "hevc", "ipod", "m4v", "matroska", "mjpeg",
            "mov", "mp2", "mp3", "mp4", "mpeg", "mpeg1video", "mpeg2video",
            "mpegts", "mulaw", "nut", "oga", "ogg", "ogv", "opus", "rawvideo",
            "rm", "sox", "spdif", "truehd", "tta", "vc1", "voc", "wav", "webm",
            "wv", "yuv4mpegpipe"
    ));

    public static String parseJsonOnly(String modelResult) throws ParseException {
        return new CommandJsonParser(requireText(modelResult)).parse();
    }

    public static String parsePastedResult(String pastedResult) throws ParseException {
        return parseModelResult(pastedResult);
    }

    public static String parseModelResult(String modelResult) throws ParseException {
        String value = normalizeModelResult(requireText(modelResult));
        if (containsCommandMarker(value)) {
            return parseCommandEnvelope(value);
        }
        if (value.startsWith("{")) {
            return new CommandJsonParser(value).parse();
        }
        if (value.startsWith("```")) {
            return parseMarkdownFence(value);
        }
        if (!value.startsWith("ffmpeg ")) {
            throw new ParseException(
                    "Paste the command envelope, JSON object, or a plain command beginning " +
                            "with ffmpeg");
        }
        return requireSingleLineCommand(value);
    }

    private static String parseCommandEnvelope(String value) throws ParseException {
        String[] lines = value.split("\n", -1);
        int begin = -1;
        int end = -1;
        int beginCount = 0;
        int endCount = 0;
        for (int i = 0; i < lines.length; i++) {
            if (COMMAND_BEGIN.equals(lines[i])) {
                begin = i;
                beginCount++;
            } else if (COMMAND_END.equals(lines[i])) {
                end = i;
                endCount++;
            }
        }
        if (beginCount != 1 || endCount != 1 || end <= begin) {
            throw new ParseException(
                    "The command envelope must contain one command between exact V1 markers");
        }
        String command = null;
        for (int i = begin + 1; i < end; i++) {
            String candidate = lines[i].trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (command != null) {
                throw new ParseException(
                        "The command envelope must contain exactly one non-empty line");
            }
            command = candidate;
        }
        if (command == null) {
            throw new ParseException("The command envelope is empty");
        }
        return requireSingleLineCommand(command);
    }

    private static String parseMarkdownFence(String value) throws ParseException {
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0 || !("```".equals(value.substring(0, firstLineEnd)) ||
                "```bash".equalsIgnoreCase(value.substring(0, firstLineEnd)) ||
                "```sh".equalsIgnoreCase(value.substring(0, firstLineEnd)))) {
            throw new ParseException("The command fence must be plain, bash, or sh");
        }
        if (!value.endsWith("\n```") || count(value, "```") != 2) {
            throw new ParseException("The command fence must contain exactly one code block");
        }
        String command = value.substring(firstLineEnd + 1, value.length() - 4);
        return requireSingleLineCommand(command);
    }

    private static String requireSingleLineCommand(String command) throws ParseException {
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 ||
                !command.startsWith("ffmpeg ")) {
            throw new ParseException("The result must contain one line beginning with ffmpeg");
        }
        validateUnicodeCharacters(command);
        return command;
    }

    private static boolean containsCommandMarker(String value) {
        return value.contains(COMMAND_BEGIN) || value.contains(COMMAND_END);
    }

    private static String normalizeModelResult(String value) {
        String normalized = value.trim();
        if (!normalized.isEmpty() && normalized.charAt(0) == '\ufeff') {
            normalized = normalized.substring(1).trim();
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static void validateUnicodeCharacters(String value) throws ParseException {
        for (int i = 0; i < value.length(); ) {
            char first = value.charAt(i);
            if (Character.isSurrogate(first) &&
                    (!Character.isHighSurrogate(first) || i + 1 >= value.length() ||
                            !Character.isLowSurrogate(value.charAt(i + 1)))) {
                throw new ParseException("The command contains an invalid Unicode surrogate");
            }
            int codePoint = Character.codePointAt(value, i);
            int type = Character.getType(codePoint);
            if (codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0xfeff ||
                    type == Character.FORMAT) {
                throw new ParseException(
                        "The command contains a hidden Unicode format character");
            }
            i += Character.charCount(codePoint);
        }
    }

    public static String materialize(String placeholderCommand, String inputPath,
                                     String outputPath)
            throws ParseException, FfmpegCommandValidator.ValidationException {
        return materialize(placeholderCommand, inputPath, outputPath, INPUT_1, OUTPUT_1);
    }

    public static String materialize(String placeholderCommand, String inputPath,
                                     String outputPath, Set<String> compiledFilters)
            throws ParseException, FfmpegCommandValidator.ValidationException {
        return materialize(placeholderCommand, inputPath, outputPath, INPUT_1, OUTPUT_1,
                compiledFilters);
    }

    public static String materialize(String placeholderCommand, String inputPath,
                                     String outputPath, String inputPlaceholder,
                                     String outputPlaceholder)
            throws ParseException, FfmpegCommandValidator.ValidationException {
        return materialize(placeholderCommand, inputPath, outputPath, inputPlaceholder,
                outputPlaceholder, null);
    }

    public static String materialize(String placeholderCommand, String inputPath,
                                     String outputPath, String inputPlaceholder,
                                     String outputPlaceholder, Set<String> compiledFilters)
            throws ParseException, FfmpegCommandValidator.ValidationException {
        String command = requireText(placeholderCommand);
        requireExpectedPlaceholder(inputPlaceholder);
        requireExpectedPlaceholder(outputPlaceholder);
        if (inputPlaceholder.equals(outputPlaceholder)) {
            throw new ParseException("Input and output placeholders must be distinct");
        }
        requireKnownPlaceholders(command, inputPlaceholder, outputPlaceholder);
        requireSingleDoubleQuotedPlaceholder(command, inputPlaceholder);
        requireSingleDoubleQuotedPlaceholder(command, outputPlaceholder);
        if (!command.endsWith("\"" + outputPlaceholder + "\"")) {
            throw new ParseException(outputPlaceholder + " must be the final command argument");
        }
        validateTemplateIsolation(command, inputPlaceholder, outputPlaceholder);
        command = normalizeOverwritePolicy(command);

        String materialized = command
                .replace(inputPlaceholder, inputPath)
                .replace(outputPlaceholder, outputPath);
        if (materialized.contains("{{") || materialized.contains("}}")) {
            throw new ParseException("The command contains an unresolved placeholder");
        }
        return FfmpegCommandValidator.validate(
                materialized, inputPath, outputPath, compiledFilters);
    }

    private static String normalizeOverwritePolicy(String command)
            throws ParseException {
        List<RawToken> tokens = tokenizeRaw(command);
        StringBuilder normalized = new StringBuilder("ffmpeg -y");
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i).value;
            String lower = token.toLowerCase(Locale.US);
            if ("-n".equals(lower)) {
                throw new ParseException(
                        "AI-generated commands cannot disable output overwriting with -n");
            }
            if ("-y".equals(lower)) {
                continue;
            }
            if (!token.startsWith("-")) {
                normalized.append(' ').append(tokens.get(i).raw);
                continue;
            }
            normalized.append(' ').append(tokens.get(i).raw);
            int arity = "-i".equals(lower) ? 1 : optionArity(lower);
            if (arity == 1) {
                normalized.append(' ').append(tokens.get(++i).raw);
            }
        }
        return normalized.toString();
    }

    private static List<RawToken> tokenizeRaw(String command) throws ParseException {
        List<RawToken> tokens = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean tokenStarted = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                raw.append(c);
                tokenStarted = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (tokenStarted) {
                    tokens.add(new RawToken(value.toString(), raw.toString()));
                    raw.setLength(0);
                    value.setLength(0);
                    tokenStarted = false;
                }
            } else {
                raw.append(c);
                value.append(c);
                tokenStarted = true;
            }
        }
        if (quoted) {
            throw new ParseException("The placeholder command contains an unmatched quote");
        }
        if (tokenStarted) {
            tokens.add(new RawToken(value.toString(), raw.toString()));
        }
        return tokens;
    }

    private static final class RawToken {
        private final String value;
        private final String raw;

        private RawToken(String value, String raw) {
            this.value = value;
            this.raw = raw;
        }
    }

    private static String requireText(String value) throws ParseException {
        if (value == null) {
            throw new ParseException("The model result is empty");
        }
        if (value.length() > MAX_RESULT_LENGTH) {
            throw new ParseException("The model result is too large");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ParseException("The model result is empty");
        }
        return trimmed;
    }

    private static void requireExpectedPlaceholder(String placeholder) throws ParseException {
        if (placeholder == null || !PLACEHOLDER.matcher(placeholder).matches()) {
            throw new ParseException("The expected placeholder is invalid");
        }
    }

    private static void requireKnownPlaceholders(String command, String inputPlaceholder,
                                                 String outputPlaceholder)
            throws ParseException {
        Matcher matcher = PLACEHOLDER.matcher(command);
        while (matcher.find()) {
            String found = matcher.group();
            if (!inputPlaceholder.equals(found) && !outputPlaceholder.equals(found)) {
                throw new ParseException("Unknown placeholder: " + found);
            }
        }
        String withoutKnown = command.replace(inputPlaceholder, "")
                .replace(outputPlaceholder, "");
        if (withoutKnown.contains("{{") || withoutKnown.contains("}}")) {
            throw new ParseException("The command contains a malformed or unknown placeholder");
        }
    }

    private static void requireSingleDoubleQuotedPlaceholder(String command, String placeholder)
            throws ParseException {
        if (count(command, placeholder) != 1) {
            throw new ParseException(placeholder + " must appear exactly once");
        }
        if (count(command, "\"" + placeholder + "\"") != 1) {
            throw new ParseException(placeholder + " must be a complete double-quoted argument");
        }
    }

    private static int count(String value, String part) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(part, offset)) >= 0) {
            count++;
            offset += part.length();
        }
        return count;
    }

    private static void validateTemplateIsolation(String command, String inputPlaceholder,
                                                  String outputPlaceholder)
            throws ParseException {
        List<String> tokens = tokenize(command);
        if (tokens.size() < 4 || !"ffmpeg".equals(tokens.get(0)) ||
                !outputPlaceholder.equals(tokens.get(tokens.size() - 1))) {
            throw new ParseException("The placeholder command has an invalid command shape");
        }

        int inputCount = 0;
        boolean inputSeen = false;
        for (int i = 1; i < tokens.size() - 1; i++) {
            String option = tokens.get(i);
            if ("-i".equals(option)) {
                if (++inputCount != 1 || i + 1 >= tokens.size() - 1 ||
                        !inputPlaceholder.equals(tokens.get(++i))) {
                    throw new ParseException("Exactly one placeholder input is required");
                }
                inputSeen = true;
                continue;
            }
            if (!option.startsWith("-")) {
                throw new ParseException("Extra positional input or output is forbidden");
            }
            String lowerOption = option.toLowerCase(Locale.US);
            if (isSideFileOption(lowerOption)) {
                throw new ParseException("Side-file option is forbidden: " + option);
            }
            int arity = optionArity(lowerOption);
            if (arity < 0) {
                throw new ParseException("Unsupported FFmpeg option in AI command: " + option);
            }
            if ("-hwaccel".equals(lowerOption) && inputSeen) {
                throw new ParseException("-hwaccel must appear before -i");
            }
            if (arity == 1) {
                if (i + 1 >= tokens.size() - 1) {
                    throw new ParseException("Missing value for FFmpeg option: " + option);
                }
                String optionValue = tokens.get(++i);
                validateDataToken(optionValue);
                if ("-f".equals(lowerOption) &&
                        !SAFE_SINGLE_FILE_FORMATS.contains(optionValue.toLowerCase(Locale.US))) {
                    throw new ParseException(
                            "The requested format can create multiple or indirect outputs");
                }
            }
        }
        if (inputCount != 1) {
            throw new ParseException("Exactly one placeholder input is required");
        }
    }

    private static int optionArity(String option) {
        if (NO_VALUE_OPTIONS.contains(option)) {
            return 0;
        }
        if (VALUE_OPTIONS.contains(option) ||
                option.matches("-(?:c|codec|b|filter|bsf|profile|level|tag|disposition)(?::[A-Za-z0-9:]+)?") ||
                option.matches("-metadata(?::[A-Za-z0-9:]+)?") ||
                option.matches("-(?:maxrate|minrate|bufsize|fps_mode|r)(?::[A-Za-z0-9:]+)?")) {
            return 1;
        }
        return -1;
    }

    private static void validateDataToken(String token) throws ParseException {
        String lower = token.toLowerCase(Locale.US);
        if (INPUT_1.equals(token) || OUTPUT_1.equals(token) || "-".equals(token) ||
                token.startsWith("/") || token.contains("=/") ||
                token.contains("../") || "..".equals(token) ||
                SCHEME.matcher(token).matches() || token.contains("://") ||
                containsForbiddenProtocol(lower) || PATH_LIKE_FILE.matcher(token).matches() ||
                containsSideFileFilter(lower) ||
                (token.contains("/") && !hasOnlyMathDivisions(token))) {
            throw new ParseException(
                    "The placeholder command contains a hidden file or protocol reference");
        }
    }

    private static boolean hasOnlyMathDivisions(String token) {
        String remaining = token;
        String previous;
        do {
            previous = remaining;
            remaining = MATH_DIVISION.matcher(remaining).replaceAll("ratio");
        } while (!remaining.equals(previous));
        return !remaining.contains("/");
    }

    private static boolean isSideFileOption(String option) {
        return option.startsWith("-filter_script") ||
                option.startsWith("-filter_complex_script") ||
                option.startsWith("-attach") ||
                option.startsWith("-dump_attachment") ||
                option.startsWith("-progress") ||
                option.startsWith("-passlogfile") ||
                option.startsWith("-vstats_file") ||
                option.startsWith("-sdp_file") ||
                option.startsWith("-segment_list") ||
                option.startsWith("-hls_key_info_file") ||
                option.startsWith("-hls_segment_filename") ||
                option.startsWith("-stats_mux_pre") ||
                option.startsWith("-stats_enc_pre") ||
                option.startsWith("-stats_enc_post") ||
                option.startsWith("-fpre") || option.startsWith("-apre") ||
                option.startsWith("-vpre");
    }

    private static boolean containsSideFileFilter(String value) {
        String compact = value.replaceAll("\\s+", "");
        return compact.contains("movie=") || compact.contains("amovie=") ||
                compact.contains("subtitles=") || compact.contains("ass=") ||
                compact.contains("textfile=") || compact.contains("fontfile=") ||
                compact.contains("filename=") || compact.contains("file=") ||
                compact.contains("log_path=") || compact.contains("stats_file=") ||
                compact.contains("statsfile=") || compact.contains("sendcmd=") ||
                compact.contains("asendcmd=") || compact.contains("zmq") ||
                compact.contains("azmq") || compact.contains("plot=") ||
                compact.contains("custom_shader_path=") ||
                compact.contains("shader_path=") ||
                compact.contains("vidstabdetect=result=") ||
                compact.contains("vidstabtransform=input=");
    }

    private static boolean containsForbiddenProtocol(String value) {
        String[] protocols = {
                "file:", "http:", "https:", "tcp:", "udp:", "rtp:", "rtsp:",
                "rtmp:", "rtmps:", "ftp:", "sftp:", "ssh:", "tls:", "srt:",
                "rist:", "unix:", "tee:", "data:", "cache:", "subfile:", "zmq:",
                "pipe:", "fd:", "concat:", "crypto:"
        };
        for (String protocol : protocols) {
            if (value.contains(protocol)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tokenize(String command) throws ParseException {
        try {
            return FfmpegCommandParser.parseArguments(command);
        } catch (FfmpegCommandParser.ParseException e) {
            throw new ParseException(e.getMessage());
        }
    }

    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    private static final class CommandJsonParser {
        private final String source;
        private int position;

        private CommandJsonParser(String source) {
            this.source = source;
        }

        private String parse() throws ParseException {
            skipWhitespace();
            expect('{');
            skipWhitespace();
            String key = parseString();
            if (!"command".equals(key)) {
                throw error("The JSON object must contain only the command field");
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            String command = parseString();
            skipWhitespace();
            expect('}');
            skipWhitespace();
            if (position != source.length()) {
                throw error("Unexpected data after the JSON object");
            }
            return command;
        }

        private String parseString() throws ParseException {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') {
                    return value.toString();
                }
                if (c < 0x20) {
                    throw error("Unescaped control character in JSON string");
                }
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (position >= source.length()) {
                    throw error("Incomplete JSON escape");
                }
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        value.append(escaped);
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'u':
                        value.append(parseUnicode());
                        break;
                    default:
                        throw error("Unsupported JSON escape");
                }
            }
            throw error("Unterminated JSON string");
        }

        private char parseUnicode() throws ParseException {
            if (position + 4 > source.length()) {
                throw error("Incomplete Unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(source.charAt(position++), 16);
                if (digit < 0) {
                    throw error("Invalid Unicode escape");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private void skipWhitespace() {
            while (position < source.length() &&
                    Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private void expect(char expected) throws ParseException {
            if (position >= source.length() || source.charAt(position) != expected) {
                throw error("Expected '" + expected + "'");
            }
            position++;
        }

        private ParseException error(String message) {
            return new ParseException(message + " at character " + position);
        }
    }

    private AiPlaceholderCommand() {
        throw new AssertionError("No instances");
    }
}
