package com.silentlexx.ffmpeggui_os.ai;

import android.content.Context;

import com.silentlexx.ffmpeggui_os.model.HardwareCodecSelector;
import com.silentlexx.ffmpeggui_os.parts.Bin;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class FfmpegAiPrompt {

    private static final int MAX_HELP_CHARS = 7000;
    private static final int MAX_CAPABILITY_CHARS = 6000;
    static final String BUNDLED_ENCODER_POLICY =
            "Use only encoder names present in the compiled inventory. This build does not " +
            "bundle libx264, libx265, libvpx-vp9, or libaom-av1. AI-generated MediaCodec " +
            "commands use hardware policy and place -pix_fmt yuv420p before -c:v. System " +
            "software MediaCodec fallback is available " +
            "through the separate built-in fallback presets. Do not output the internal " +
            "-ffmpeggui-codec-policy option.";

    public static AiPromptBundle buildPromptBundle(Context context, String inputPath,
                                                   String outputPath,
                                                   String userRequirement) {
        return buildPromptBundle(context, inputPath, outputPath, userRequirement,
                AiPlaceholderCommand.INPUT_1, AiPlaceholderCommand.OUTPUT_1);
    }

    public static AiPromptBundle buildOfflinePromptBundle(Context context, String inputPath,
                                                          String outputPath,
                                                          String userRequirement,
                                                          String sessionToken) {
        return buildPromptBundle(context, inputPath, outputPath, userRequirement,
                OfflinePlaceholderBinding.inputPlaceholder(sessionToken),
                OfflinePlaceholderBinding.outputPlaceholder(sessionToken));
    }

    private static AiPromptBundle buildPromptBundle(Context context, String inputPath,
                                                    String outputPath,
                                                    String userRequirement,
                                                    String inputPlaceholder,
                                                    String outputPlaceholder) {
        long fileSize = -1;
        if (inputPath != null && !inputPath.contains("://")) {
            File input = new File(inputPath);
            if (input.isFile()) {
                fileSize = input.length();
            }
        }

        String rawProbe = Bin.getBinFileInfo(context, inputPath);
        InputMediaMetadata metadata = InputMediaMetadata.fromProbe(
                inputPath, fileSize, rawProbe);

        StringBuilder capabilities = new StringBuilder();
        capabilities.append("Current FFmpeg version:\n")
                .append(safeCommandOutput(context, CapabilityQuery.VERSION, 1200))
                .append("\n\nCompiled FFmpeg relevant encoders (authoritative):\n")
                .append(safeCommandOutput(context, CapabilityQuery.ENCODERS,
                        MAX_CAPABILITY_CHARS))
                .append("\n\nCompiled FFmpeg MediaCodec decoders:\n")
                .append(safeCommandOutput(context, CapabilityQuery.DECODERS,
                        MAX_CAPABILITY_CHARS))
                .append("\n\nAndroid hardware MediaCodec inventory:\n")
                .append(HardwareCodecSelector.buildDiagnosticSummary())
                .append("\n\nDevice-specific MediaCodec command guidance:\n")
                .append(buildHardwareGuidance(metadata, inputPlaceholder))
                .append("\n\nEncoder selection policy:\n")
                .append(BUNDLED_ENCODER_POLICY)
                .append("\n\nCurrent embedded FFmpeg help excerpt:\n")
                .append(safeCommandOutput(context, CapabilityQuery.HELP, MAX_HELP_CHARS));

        return AiPromptComposer.compose(capabilities.toString(), metadata.toPromptText(),
                userRequirement, inputPath, outputPath, inputPlaceholder, outputPlaceholder);
    }

    private static String buildHardwareGuidance(InputMediaMetadata metadata,
                                                String inputPlaceholder) {
        StringBuilder guidance = new StringBuilder();
        appendEncoderGuidance(guidance, "H.264", "h264_mediacodec",
                HardwareCodecSelector.selectHardwareEncoder(
                        HardwareCodecSelector.MIME_H264,
                        metadata.getWidth(), metadata.getHeight(), metadata.getFrameRate()),
                HardwareCodecSelector.getH264EncoderNames());
        appendEncoderGuidance(guidance, "HEVC", "hevc_mediacodec",
                HardwareCodecSelector.selectHardwareEncoder(
                        HardwareCodecSelector.MIME_HEVC,
                        metadata.getWidth(), metadata.getHeight(), metadata.getFrameRate()),
                HardwareCodecSelector.getHevcEncoderNames());
        appendEncoderGuidance(guidance, "VP9", "vp9_mediacodec",
                HardwareCodecSelector.selectHardwareEncoder(
                        HardwareCodecSelector.MIME_VP9,
                        metadata.getWidth(), metadata.getHeight(), metadata.getFrameRate()),
                HardwareCodecSelector.getVp9EncoderNames());
        appendEncoderGuidance(guidance, "AV1", "av1_mediacodec",
                HardwareCodecSelector.selectHardwareEncoder(
                        HardwareCodecSelector.MIME_AV1,
                        metadata.getWidth(), metadata.getHeight(), metadata.getFrameRate()),
                HardwareCodecSelector.getAv1EncoderNames());

        guidance.append("For hardware decoding, place `-hwaccel mediacodec` before " +
                "`-i \"").append(inputPlaceholder).append("\"`. The decoder wrapper lets " +
                "Android select the component; " +
                "do not add encoder-only -codec_name to a decoder. Hardware decoder inventory " +
                "above covers H.264, HEVC, VP9, and AV1.\n");
        guidance.append("Use a MediaCodec codec only when it appears in both the compiled FFmpeg " +
                "list and Android hardware inventory. When hardware is unavailable, direct the " +
                "user to a built-in system software fallback preset or stream copy.");
        return guidance.toString();
    }

    private static void appendEncoderGuidance(StringBuilder target, String label,
                                              String ffmpegCodec, String selected,
                                              List<String> available) {
        String safeSelected = safeComponentName(selected);
        if (!safeSelected.isEmpty()) {
            target.append(label).append(
                            " hardware encoder command fragment: `-pix_fmt yuv420p -c:v ")
                    .append(ffmpegCodec)
                    .append(" -codec_name ").append(safeSelected)
                    .append(" -ndk_codec 1 -ndk_async 0`. This component was selected for the " +
                            "input dimensions/frame rate when those values were available.\n");
        } else if (available.isEmpty()) {
            target.append(label).append(" hardware encoder: unavailable.\n");
        } else {
            target.append(label).append(" hardware encoders exist, but no component was confirmed " +
                    "for the probed dimensions/frame rate.\n");
        }
    }

    private static String safeComponentName(String component) {
        if (component == null || !component.matches("[A-Za-z0-9._-]{1,160}")) {
            return "";
        }
        return component;
    }

    private enum CapabilityQuery {
        VERSION,
        ENCODERS,
        DECODERS,
        HELP
    }

    private static String safeCommandOutput(Context context, CapabilityQuery query, int maxChars) {
        try {
            String output;
            switch (query) {
                case VERSION:
                    output = Bin.getBinVersion(context);
                    break;
                case ENCODERS:
                    output = relevantEncoderLines(Bin.getBinEncoders(context));
                    break;
                case DECODERS:
                    output = mediaCodecLines(Bin.getBinDecoders(context));
                    break;
                case HELP:
                    output = Bin.getBinHelp(context);
                    break;
                default:
                    output = "";
            }
            output = sanitize(output);
            if (output.isEmpty()) {
                return "Unavailable. Verify options against the fixed FFmpeg help essentials.";
            }
            return truncate(output, maxChars);
        } catch (RuntimeException e) {
            return "Unavailable. Verify options against the fixed FFmpeg help essentials.";
        }
    }

    private static String mediaCodecLines(String output) {
        if (output == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String line : output.split("\\r?\\n")) {
            if (line.toLowerCase(Locale.US).contains("mediacodec")) {
                result.append(line.trim()).append('\n');
            }
        }
        return result.toString().trim();
    }

    static String relevantEncoderLines(String output) {
        if (output == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String line : output.split("\\r?\\n")) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.contains("h264") || lower.contains("hevc") ||
                    lower.contains("vp9") || lower.contains("av1") ||
                    lower.matches(".*\\b(aac|opus|flac|mpeg4|pcm_[a-z0-9_]+)\\b.*")) {
                result.append(line.trim()).append('\n');
            }
        }
        return result.toString().trim();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\t' || (c >= 0x20 && c != 0x7f)) {
                result.append(c);
            }
        }
        return result.toString().trim();
    }

    private static String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n[truncated]";
    }

    private FfmpegAiPrompt() {
        throw new AssertionError("No instances");
    }
}
