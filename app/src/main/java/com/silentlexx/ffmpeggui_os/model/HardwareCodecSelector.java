package com.silentlexx.ffmpeggui_os.model;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only view of the device's regular MediaCodec components.
 *
 * FFmpeg 8.1.2 accepts a selected encoder component through
 * {@code -c:v <codec>_mediacodec -codec_name <component>}. Its MediaCodec
 * decoders use Android's codec selection after {@code -hwaccel mediacodec};
 * the decoder wrapper has no {@code codec_name} private option in this FFmpeg
 * release.
 */
public final class HardwareCodecSelector {

    public static final String MIME_H264 = MediaFormat.MIMETYPE_VIDEO_AVC;
    public static final String MIME_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    public static final String MIME_VP9 = "video/x-vnd.on2.vp9";
    public static final String MIME_AV1 = "video/av01";

    private HardwareCodecSelector() {
    }

    public static List<String> getH264EncoderNames() {
        return getHardwareCodecNames(MIME_H264, true);
    }

    public static List<String> getH264DecoderNames() {
        return getHardwareCodecNames(MIME_H264, false);
    }

    public static List<String> getH264SoftwareEncoderNames() {
        return getSystemSoftwareEncoderNames(MIME_H264);
    }

    public static List<String> getHevcEncoderNames() {
        return getHardwareCodecNames(MIME_HEVC, true);
    }

    public static List<String> getHevcDecoderNames() {
        return getHardwareCodecNames(MIME_HEVC, false);
    }

    public static List<String> getHevcSoftwareEncoderNames() {
        return getSystemSoftwareEncoderNames(MIME_HEVC);
    }

    public static List<String> getVp9EncoderNames() {
        return getHardwareCodecNames(MIME_VP9, true);
    }

    public static List<String> getVp9DecoderNames() {
        return getHardwareCodecNames(MIME_VP9, false);
    }

    public static List<String> getVp9SoftwareEncoderNames() {
        return getSystemSoftwareEncoderNames(MIME_VP9);
    }

    public static List<String> getAv1EncoderNames() {
        return getHardwareCodecNames(MIME_AV1, true);
    }

    public static List<String> getAv1DecoderNames() {
        return getHardwareCodecNames(MIME_AV1, false);
    }

    public static List<String> getAv1SoftwareEncoderNames() {
        return getSystemSoftwareEncoderNames(MIME_AV1);
    }

    /**
     * Returns hardware component names in Android's preference order.
     */
    public static List<String> getHardwareCodecNames(String mime, boolean encoder) {
        if (mime == null || mime.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> names = new LinkedHashSet<>();
        for (MediaCodecInfo info : getRegularCodecInfos()) {
            if (info.isEncoder() != encoder || !isHardwareAccelerated(info)) {
                continue;
            }
            if (supportsType(info, mime) &&
                    (!encoder || supportsPlanarEncoderInput(info, mime))) {
                names.add(info.getName());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    /** Returns Android system software encoders in the framework's preference order. */
    public static List<String> getSystemSoftwareEncoderNames(String mime) {
        if (mime == null || mime.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> names = new LinkedHashSet<>();
        for (MediaCodecInfo info : getRegularCodecInfos()) {
            if (!info.isEncoder() || !isSystemSoftwareEncoder(info)) {
                continue;
            }
            if (supportsType(info, mime) && supportsPlanarEncoderInput(info, mime)) {
                names.add(info.getName());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    /**
     * Selects the first hardware encoder that advertises the requested size
     * and frame rate. Pass non-positive dimensions/rate to skip that check.
     */
    public static String selectHardwareEncoder(
            String mime,
            int width,
            int height,
            double frameRate
    ) {
        if (mime == null || mime.trim().isEmpty()) {
            return null;
        }

        for (MediaCodecInfo info : getRegularCodecInfos()) {
            if (!info.isEncoder() || !isHardwareAccelerated(info) || !supportsType(info, mime)) {
                continue;
            }
            if (supportsPlanarEncoderInput(info, mime) &&
                    supportsVideoShape(info, mime, width, height, frameRate)) {
                return info.getName();
            }
        }
        return null;
    }

    /** Selects a system MediaCodec software encoder for the requested video shape. */
    public static String selectSystemSoftwareEncoder(
            String mime,
            int width,
            int height,
            double frameRate
    ) {
        if (mime == null || mime.trim().isEmpty()) {
            return null;
        }

        for (MediaCodecInfo info : getRegularCodecInfos()) {
            if (!info.isEncoder() || !isSystemSoftwareEncoder(info) ||
                    !supportsType(info, mime)) {
                continue;
            }
            if (supportsPlanarEncoderInput(info, mime) &&
                    supportsVideoShape(info, mime, width, height, frameRate)) {
                return info.getName();
            }
        }
        return null;
    }

    public static boolean isHardwareEncoderComponent(
            String mime,
            String component,
            int width,
            int height,
            double frameRate
    ) {
        return isEncoderComponent(mime, component, width, height, frameRate, false);
    }

    public static boolean isSystemSoftwareEncoderComponent(
            String mime,
            String component,
            int width,
            int height,
            double frameRate
    ) {
        return isEncoderComponent(mime, component, width, height, frameRate, true);
    }

    public static String buildDiagnosticSummary() {
        StringBuilder summary = new StringBuilder();
        appendSummary(summary, "H.264 hardware encoders", getH264EncoderNames());
        appendSummary(summary, "H.264 system software encoders",
                getH264SoftwareEncoderNames());
        appendSummary(summary, "H.264 hardware decoders", getH264DecoderNames());
        appendSummary(summary, "HEVC hardware encoders", getHevcEncoderNames());
        appendSummary(summary, "HEVC system software encoders",
                getHevcSoftwareEncoderNames());
        appendSummary(summary, "HEVC hardware decoders", getHevcDecoderNames());
        appendSummary(summary, "VP9 hardware encoders", getVp9EncoderNames());
        appendSummary(summary, "VP9 system software encoders",
                getVp9SoftwareEncoderNames());
        appendSummary(summary, "VP9 hardware decoders", getVp9DecoderNames());
        appendSummary(summary, "AV1 hardware encoders", getAv1EncoderNames());
        appendSummary(summary, "AV1 system software encoders",
                getAv1SoftwareEncoderNames());
        appendSummary(summary, "AV1 hardware decoders", getAv1DecoderNames());
        return summary.toString();
    }

    private static MediaCodecInfo[] getRegularCodecInfos() {
        try {
            return new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
        } catch (RuntimeException e) {
            return new MediaCodecInfo[0];
        }
    }

    private static boolean isEncoderComponent(
            String mime,
            String component,
            int width,
            int height,
            double frameRate,
            boolean systemSoftware
    ) {
        if (mime == null || mime.trim().isEmpty() || component == null ||
                component.trim().isEmpty()) {
            return false;
        }
        for (MediaCodecInfo info : getRegularCodecInfos()) {
            if (!info.isEncoder() || !component.equals(info.getName()) ||
                    !supportsType(info, mime)) {
                continue;
            }
            boolean matchingClass = systemSoftware
                    ? isSystemSoftwareEncoder(info) : isHardwareAccelerated(info);
            return matchingClass && supportsPlanarEncoderInput(info, mime) &&
                    supportsVideoShape(info, mime, width, height, frameRate);
        }
        return false;
    }

    private static boolean supportsType(MediaCodecInfo info, String requestedMime) {
        for (String supportedType : info.getSupportedTypes()) {
            if (supportedType.equalsIgnoreCase(requestedMime)) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsVideoShape(
            MediaCodecInfo info,
            String mime,
            int width,
            int height,
            double frameRate
    ) {
        if (width <= 0 || height <= 0) {
            return true;
        }

        try {
            MediaCodecInfo.VideoCapabilities video = info
                    .getCapabilitiesForType(mime)
                    .getVideoCapabilities();
            if (frameRate > 0) {
                return video.areSizeAndRateSupported(width, height, frameRate);
            }
            return video.isSizeSupported(width, height);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private static boolean supportsPlanarEncoderInput(MediaCodecInfo info, String mime) {
        try {
            return supportsPlanarEncoderInput(
                    info.getCapabilitiesForType(mime).colorFormats);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    static boolean supportsPlanarEncoderInput(int[] colorFormats) {
        if (colorFormats == null) {
            return false;
        }
        for (int colorFormat : colorFormats) {
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar ||
                    colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHardwareAccelerated(MediaCodecInfo info) {
        String name = info.getName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isUsableHardwareComponentName(name, info.isHardwareAccelerated());
        }

        return !isLegacySystemSoftwareComponentName(name);
    }

    private static boolean isSystemSoftwareEncoder(MediaCodecInfo info) {
        String name = info.getName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isUsableSoftwareComponentName(name, info.isSoftwareOnly());
        }
        return isLegacySystemSoftwareComponentName(name);
    }

    /** Combines Android's API 29+ hardware flag with a narrow software denylist. */
    static boolean isUsableHardwareComponentName(String componentName,
                                                  boolean frameworkHardwareFlag) {
        return componentName != null && !componentName.trim().isEmpty()
                && frameworkHardwareFlag && !isKnownSoftwareComponentName(componentName);
    }

    /** Accepts API 29 software-only components and known Android reference codecs. */
    static boolean isUsableSoftwareComponentName(String componentName,
                                                  boolean frameworkSoftwareOnlyFlag) {
        return componentName != null && !componentName.trim().isEmpty()
                && (frameworkSoftwareOnlyFlag ||
                isKnownSoftwareComponentName(componentName));
    }

    /** API 26-28 has no software-only flag; c2.google was reference software there. */
    static boolean isLegacySystemSoftwareComponentName(String componentName) {
        return componentName != null && !componentName.trim().isEmpty()
                && (isKnownSoftwareComponentName(componentName)
                || componentName.toLowerCase(Locale.US).startsWith("c2.google."));
    }

    /** Rejects Android/reference software components even if a vendor reports bad flags. */
    static boolean isKnownSoftwareComponentName(String componentName) {
        if (componentName == null) {
            return true;
        }
        String name = componentName.toLowerCase(Locale.US);
        return name.startsWith("omx.google.")
                || name.startsWith("omx.ffmpeg.")
                || name.startsWith("c2.android.")
                || name.contains(".sw.")
                || name.contains("software");
    }

    private static void appendSummary(StringBuilder target, String label, List<String> names) {
        if (target.length() > 0) {
            target.append('\n');
        }
        target.append(label).append(": ");
        if (names.isEmpty()) {
            target.append("none");
        } else {
            target.append(String.join(", ", names));
        }
    }
}
