package com.silentlexx.ffmpeggui_os.ai;

import android.content.Context;

import com.silentlexx.ffmpeggui_os.parts.Bin;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and retains the authoritative filter inventory reported by the bundled FFmpeg. */
public final class FfmpegFilterInventory {

    private static final Pattern FILTER_LINE = Pattern.compile(
            "^\\s*[T.][S.](?:[C.])?\\s+([A-Za-z0-9_]+)(?:\\s|$)");
    /*
     * AI commands get a deliberately conservative subset of the runtime inventory.
     * These filters operate on in-memory frames/samples and have no filename, model,
     * command-socket, or side-file contract. Manual Console commands can still use every
     * filter compiled into FFmpeg, including ass/subtitles/drawtext.
     */
    private static final Set<String> AI_SAFE_FILTERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "acompressor", "acrossfade", "adelay", "afade", "aformat",
                    "agate", "alimiter", "amerge", "amix", "anull", "apad",
                    "aresample", "areverse", "asetrate", "asetpts", "asplit",
                    "atempo", "atrim", "bandpass", "bandreject", "bass",
                    "biquad", "channelmap", "channelsplit", "compand", "dynaudnorm",
                    "equalizer", "highpass", "lowpass", "pan",
                    "silencedetect", "silenceremove", "treble", "volume",
                    "alphaextract", "alphamerge", "avgblur", "blend", "boxblur",
                    "bwdif", "chromakey", "color", "colorbalance", "colorchannelmixer",
                    "colorkey", "concat", "crop", "cropdetect", "deband", "decimate",
                    "deflicker", "delogo", "eq", "fade", "fieldorder", "format",
                    "fps", "framerate", "gblur", "hflip", "hstack", "hue",
                    "lut", "lutrgb", "lutyuv", "minterpolate", "mpdecimate",
                    "negate", "noise", "normalize", "null", "overlay", "pad",
                    "perspective", "pullup", "reverse", "rotate", "scale",
                    "scale2ref", "select", "setdar", "setpts", "setsar", "smartblur",
                    "split", "stereo3d", "tblend", "thumbnail", "tile", "tinterlace",
                    "tonemap", "tpad", "transpose", "trim", "unsharp", "vflip",
                    "vstack", "xstack", "yadif", "zscale")));
    private static volatile Set<String> cachedNames = Collections.emptySet();
    private static volatile String cachedBinaryKey = "";

    /** Loads the current binary's inventory when this process has not loaded it yet. */
    public static Set<String> currentOrLoad(Context context) {
        final String binaryKey;
        try {
            String[] command = Bin.getBin(context);
            if (command == null || command.length == 0 || command[0] == null ||
                    command[0].isEmpty()) {
                return Collections.emptySet();
            }
            File binary = new File(command[0]);
            String prefix = command.length > 1 && command[1] != null ? command[1] : "";
            binaryKey = command[0] + '\n' + prefix + '\n' + binary.length() + '\n' +
                    binary.lastModified();
        } catch (RuntimeException e) {
            return Collections.emptySet();
        }
        Set<String> names = cachedNames;
        if (!names.isEmpty() && binaryKey.equals(cachedBinaryKey)) {
            return names;
        }
        synchronized (FfmpegFilterInventory.class) {
            if (!cachedNames.isEmpty() && binaryKey.equals(cachedBinaryKey)) {
                return cachedNames;
            }
            try {
                String listing = Bin.getBinFilters(context);
                if (Thread.currentThread().isInterrupted()) {
                    return Collections.emptySet();
                }
                names = publish(listing);
                if (!names.isEmpty()) {
                    cachedBinaryKey = binaryKey;
                }
                return names;
            } catch (RuntimeException e) {
                return Collections.emptySet();
            }
        }
    }

    static Set<String> publish(String listing) {
        Set<String> names = parse(listing);
        if (!names.isEmpty()) {
            cachedNames = names;
        }
        return names;
    }

    static Set<String> parse(String listing) {
        if (listing == null || listing.trim().isEmpty()) {
            return Collections.emptySet();
        }
        TreeSet<String> names = new TreeSet<>();
        for (String line : listing.split("\\r?\\n")) {
            Matcher matcher = FILTER_LINE.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return Collections.unmodifiableSet(names);
    }

    /** Returns the intersection of the runtime inventory and the side-effect-free AI policy. */
    public static Set<String> safeForAi(Set<String> compiledNames) {
        if (compiledNames == null || compiledNames.isEmpty()) {
            return Collections.emptySet();
        }
        TreeSet<String> safe = new TreeSet<>();
        for (String name : compiledNames) {
            if (AI_SAFE_FILTERS.contains(name)) {
                safe.add(name);
            }
        }
        return Collections.unmodifiableSet(safe);
    }

    /** Loads the current binary inventory and applies the in-memory-only AI policy. */
    public static Set<String> currentSafeOrLoad(Context context) {
        return safeForAi(currentOrLoad(context));
    }

    static String toPromptList(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return "Unavailable. Reject generated commands that use filters and regenerate " +
                    "the prompt after checking the bundled FFmpeg binary.";
        }
        return String.join(", ", names);
    }

    private FfmpegFilterInventory() {
        throw new AssertionError("No instances");
    }
}
