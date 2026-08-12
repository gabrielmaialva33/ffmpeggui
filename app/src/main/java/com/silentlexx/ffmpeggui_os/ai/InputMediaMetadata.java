package com.silentlexx.ffmpeggui_os.ai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InputMediaMetadata {

    private static final int MAX_METADATA_CHARS = 4096;
    private static final Pattern FORMAT = Pattern.compile(
            "(?m)^Input #\\d+,\\s*([A-Za-z0-9_,.+ -]{1,120}),\\s+from\\s+");
    private static final Pattern DURATION = Pattern.compile(
            "Duration:\\s*([0-9]{1,3}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?)");
    private static final Pattern BIT_RATE = Pattern.compile(
            "bitrate:\\s*([0-9]+(?:\\.[0-9]+)?\\s*[kKmMgG]?b/s|N/A)");
    private static final Pattern STREAM_LINE = Pattern.compile(
            "(?m)^\\s*Stream #.*?:\\s*(Video|Audio|Subtitle):\\s*" +
                    "([A-Za-z0-9_]+)(?:\\s*\\(([A-Za-z0-9 ._+\\-]{1,60})\\))?([^\\r\\n]*)");
    private static final Pattern DIMENSIONS = Pattern.compile(
            "(?<![0-9])([0-9]{2,5})x([0-9]{2,5})(?![0-9])");
    private static final Pattern FRAME_RATE = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)\\s*fps");
    private static final Pattern SAMPLE_RATE = Pattern.compile("([0-9]{3,6})\\s*Hz");
    private static final Pattern PIXEL_FORMAT = Pattern.compile(
            "(?:^|,\\s*)((?:yuv|yuva|nv|p0|rgb|rgba|bgr|bgra|gray|gbr)[A-Za-z0-9_]{0,30})(?:[,\\s]|$)",
            Pattern.CASE_INSENSITIVE);

    private final String promptText;
    private final int width;
    private final int height;
    private final double frameRate;

    public static InputMediaMetadata fromProbe(String inputPath, long fileSize,
                                               String probeOutput) {
        String raw = probeOutput == null ? "" : probeOutput;
        StringBuilder summary = new StringBuilder("Input media metadata (identity withheld):\n");
        append(summary, "container/format", firstGroup(FORMAT, raw));
        append(summary, "file extension", extensionOnly(inputPath));
        if (fileSize >= 0) {
            append(summary, "file size", fileSize + " bytes");
        }
        append(summary, "duration", firstGroup(DURATION, raw));
        append(summary, "overall bitrate", firstGroup(BIT_RATE, raw));

        int parsedWidth = 0;
        int parsedHeight = 0;
        double parsedFrameRate = 0;
        int streamNumber = 0;
        Matcher streams = STREAM_LINE.matcher(raw);
        while (streams.find() && summary.length() < MAX_METADATA_CHARS) {
            streamNumber++;
            String type = streams.group(1).toLowerCase(Locale.US);
            String codec = safeToken(streams.group(2), 40);
            String profile = safeToken(streams.group(3), 60);
            String details = streams.group(4) == null ? "" : streams.group(4);
            StringBuilder line = new StringBuilder(type)
                    .append(" stream ").append(streamNumber)
                    .append(": codec=").append(codec);
            if (!profile.isEmpty()) {
                line.append(", profile=").append(profile);
            }

            if ("video".equals(type)) {
                Matcher dimensions = DIMENSIONS.matcher(details);
                if (dimensions.find()) {
                    int candidateWidth = parsePositiveInt(dimensions.group(1));
                    int candidateHeight = parsePositiveInt(dimensions.group(2));
                    line.append(", dimensions=")
                            .append(candidateWidth).append('x').append(candidateHeight);
                    if (parsedWidth == 0 && parsedHeight == 0) {
                        parsedWidth = candidateWidth;
                        parsedHeight = candidateHeight;
                    }
                }
                Matcher fps = FRAME_RATE.matcher(details);
                if (fps.find()) {
                    double candidateFrameRate = parsePositiveDouble(fps.group(1));
                    if (candidateFrameRate > 0) {
                        line.append(", frame_rate=").append(candidateFrameRate).append(" fps");
                        if (parsedFrameRate == 0) {
                            parsedFrameRate = candidateFrameRate;
                        }
                    }
                }
                Matcher pixelFormat = PIXEL_FORMAT.matcher(details);
                if (pixelFormat.find()) {
                    line.append(", pixel_format=")
                            .append(safeToken(pixelFormat.group(1), 40));
                }
            } else if ("audio".equals(type)) {
                Matcher sampleRate = SAMPLE_RATE.matcher(details);
                if (sampleRate.find()) {
                    line.append(", sample_rate=")
                            .append(sampleRate.group(1)).append(" Hz");
                }
            }
            summary.append(line).append('\n');
        }
        if (streamNumber == 0) {
            summary.append("recognized streams: none\n");
        }

        String cleaned = clean(summary.toString(), MAX_METADATA_CHARS);
        return new InputMediaMetadata(cleaned, parsedWidth, parsedHeight, parsedFrameRate);
    }

    public String toPromptText() {
        return promptText;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getFrameRate() {
        return frameRate;
    }

    private InputMediaMetadata(String promptText, int width, int height, double frameRate) {
        this.promptText = promptText;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
    }

    private static String extensionOnly(String inputPath) {
        if (inputPath == null) {
            return "";
        }
        String withoutQuery = inputPath;
        int query = withoutQuery.indexOf('?');
        if (query >= 0) {
            withoutQuery = withoutQuery.substring(0, query);
        }
        int slash = Math.max(withoutQuery.lastIndexOf('/'), withoutQuery.lastIndexOf(':'));
        int dot = withoutQuery.lastIndexOf('.');
        if (dot <= slash || dot + 1 >= withoutQuery.length()) {
            return "";
        }
        String extension = withoutQuery.substring(dot + 1);
        return extension.matches("[A-Za-z0-9]{1,10}")
                ? "." + extension.toLowerCase(Locale.US) : "";
    }

    private static String firstGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? safeToken(matcher.group(1), 120) : "";
    }

    private static void append(StringBuilder target, String label, String value) {
        if (value != null && !value.isEmpty()) {
            target.append(label).append(": ").append(value).append('\n');
        }
    }

    private static String safeToken(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < value.length() && safe.length() < maxChars; i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') || " _,.+-:/".indexOf(c) >= 0) {
                safe.append(c);
            }
        }
        return safe.toString().trim();
    }

    private static String clean(String value, int maxChars) {
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < value.length() && safe.length() < maxChars; i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\t' || (c >= 0x20 && c != 0x7f)) {
                safe.append(c);
            }
        }
        return safe.toString().trim();
    }

    private static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parsePositiveDouble(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0 && Double.isFinite(parsed) ? parsed : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
