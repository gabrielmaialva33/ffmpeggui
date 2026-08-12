package com.silentlexx.ffmpeggui_os.config;

/** Exact, one-time migration rules for previously shipped built-in preset arrays. */
final class BuiltInPresetSchema {

    static final int VERSION = 3;

    private static final String[][] LEGACY_SCHEMAS = {
            {
                    signature("-c:v libopenh264 -q:v 5 -c:a aac -ab 128k -ar 44100", "mp4"),
                    signature("-c:v libx264 -q:v 5 -c:a aac -ab 128k -ar 44100", "mp4"),
                    signature("-c:v mpeg4 -q:v 5 -c:a libmp3lame -ab 128k -ar 44100", "avi"),
                    signature("-c:v libx265 -q:v 5 -c:a aac -ab 128k -ar 44100", "mp4"),
                    signature("-c:v libxvid -q:v 5 -c:a libmp3lame -ab 128k -ar 44100", "avi"),
                    signature("-c:v theora -q:v 5 -c:a libvorbis -ab 128k -ar 44100", "ogv"),
                    signature("-c:v mjpeg -q:v 3 -c:a libmp3lame -ab 128k -ar 44100", "mov"),
                    signature("-s qcif -c:v h263 -q:v 3 -c:a aac -ac 1 -ar 64000 -r 25 -ab 32", "3gp"),
                    signature("-c:v libvpx-vp9 -q:v 5 -c:a libvorbis -ab 128k -ar 44100 -strict experimental", "webm"),
                    signature("-target pal-dvd -ps 1000000000 -aspect 16:9", "mpeg"),
                    signature("-c:v dv -s pal -r pal -aspect 4:3 -c:a pcm_s16le -ar 44100 -ac 2", "dv"),
                    signature("-vn -c:a aac -ab 128k -ar 44100", "m4a"),
                    signature("-vn -c:a libmp3lame -ab 192k -ar 44100 -f mp3", "mp3"),
                    signature("-vn -c:a libvorbis -ab 192k -ar 44100", "ogg"),
                    signature("-vn -c:a libopus -b:a 128k -vbr on -compression_level 10", "opus"),
                    signature("-vn -c:a flac -af aformat=s32:192000", "flac"),
                    signature("-vn -c:a pcm_u8 -ar 22050 -ac 2", "wav"),
                    signature("-vn -c:a libvo_amrwbenc -b:a 12.85k -ar 16000 -ac 1", "amr"),
                    signature("-vn -c:a libvorbis -ar 44100 -ab 192k -metadata ANDROID_LOOP=true -ac 1", "ogg"),
                    signature("-r 15", "gif")
            },
            {
                    signature("-c:v h264_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 8M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-c:v hevc_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-map 0 -c copy -movflags +faststart", "mp4"),
                    signature("-c:v mpeg4 -q:v 5 -c:a aac -b:a 160k", "avi"),
                    signature("-c:v mjpeg -q:v 3 -c:a pcm_s16le -ar 48000", "mov"),
                    signature("-s qcif -c:v h263 -q:v 3 -c:a aac -ac 1 -ar 16000 -r 25 -b:a 32k", "3gp"),
                    signature("-target pal-dvd -c:v mpeg2video -c:a mp2 -aspect 16:9", "mpeg"),
                    signature("-vn -c:a aac -b:a 192k -ar 48000", "m4a"),
                    signature("-vn -c:a opus -strict experimental -b:a 128k", "opus"),
                    signature("-vn -c:a flac", "flac"),
                    signature("-vn -c:a pcm_s16le -ar 48000 -ac 2", "wav"),
                    signature("-r 15", "gif")
            },
            {
                    signature("-c:v h264_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 8M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-c:v hevc_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-c:v av1_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 4M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-c:v vp9_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 5M -c:a opus -strict experimental -b:a 160k", "webm"),
                    signature("-map 0:v:0 -map 0:a:0? -c:v copy -c:a opus -strict experimental -b:a 160k", "webm"),
                    signature("-map 0 -c copy -movflags +faststart", "mp4"),
                    signature("-map 0 -c copy", "mkv"),
                    signature("-vn -c:a aac -b:a 192k -ar 48000", "m4a"),
                    signature("-vn -c:a opus -strict experimental -b:a 128k", "opus"),
                    signature("-vn -c:a flac", "flac")
            },
            {
                    signature("-c:v h264_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 8M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-ffmpeggui-codec-policy system-software -c:v h264_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 8M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-c:v hevc_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-ffmpeggui-codec-policy system-software -c:v hevc_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-c:v av1_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 4M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-ffmpeggui-codec-policy system-software -c:v av1_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 4M -c:a aac -b:a 192k -movflags +faststart", "mp4"),
                    signature("-c:v vp9_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 5M -c:a opus -strict experimental -b:a 160k", "webm"),
                    signature("-ffmpeggui-codec-policy system-software -c:v vp9_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 5M -c:a opus -strict experimental -b:a 160k", "webm"),
                    signature("-map 0:v:0 -map 0:a:0? -c:v copy -c:a opus -strict experimental -b:a 160k", "webm"),
                    signature("-map 0 -c copy -movflags +faststart", "mp4"),
                    signature("-map 0 -c copy", "mkv"),
                    signature("-vn -c:a aac -b:a 192k -ar 48000", "m4a"),
                    signature("-vn -c:a opus -strict experimental -b:a 128k", "opus"),
                    signature("-vn -c:a flac", "flac")
            }
    };

    static boolean shouldResetToDefault(int storedVersion, int currentIndex,
                                        String savedArgs, String savedExtension,
                                        String[] currentBuiltIns) {
        if (storedVersion >= VERSION || savedArgs == null || savedArgs.isEmpty()) {
            return false;
        }
        String savedSignature = signature(savedArgs, savedExtension);
        if (matchesCurrent(currentIndex, savedSignature, currentBuiltIns)) {
            return false;
        }
        for (String[] schema : LEGACY_SCHEMAS) {
            if (currentIndex >= 0 && currentIndex < schema.length &&
                    schema[currentIndex].equals(savedSignature)) {
                return true;
            }
        }
        return false;
    }

    static String defaultArgs(String[] currentBuiltIns) {
        return argsAt(currentBuiltIns, 0);
    }

    static String defaultExtension(String[] currentBuiltIns) {
        return extensionAt(currentBuiltIns, 0);
    }

    static int currentReplacementIndex(int storedVersion, int currentIndex,
                                       String savedArgs, String savedExtension,
                                       String[] currentBuiltIns) {
        if (storedVersion >= VERSION || currentBuiltIns == null ||
                currentIndex < 0 || currentIndex >= currentBuiltIns.length) {
            return -1;
        }
        String[] previousBuiltIns = LEGACY_SCHEMAS[LEGACY_SCHEMAS.length - 1];
        if (storedVersion == VERSION - 1 && currentIndex == previousBuiltIns.length &&
                (savedArgs == null || savedArgs.trim().isEmpty())) {
            return currentBuiltIns.length;
        }
        if (savedArgs == null || savedArgs.isEmpty()) {
            return -1;
        }
        if (currentIndex >= previousBuiltIns.length) {
            return -1;
        }
        String savedSignature = signature(savedArgs, savedExtension);
        return previousBuiltIns[currentIndex].equals(savedSignature) &&
                !matchesCurrent(currentIndex, savedSignature, currentBuiltIns)
                ? currentIndex : -1;
    }

    static String argsAt(String[] builtIns, int index) {
        if (builtIns != null && index == builtIns.length) {
            return "";
        }
        String[] fields = presetFields(builtIns, index);
        return fields == null ? "" : fields[1];
    }

    static String extensionAt(String[] builtIns, int index) {
        if (builtIns != null && index == builtIns.length) {
            return "mp4";
        }
        String[] fields = presetFields(builtIns, index);
        return fields == null ? "mp4" : fields[2];
    }

    static int customTargetIndex(int currentIndex, String savedArgs, String savedExtension,
                                 String[] currentBuiltIns, String[] customNames,
                                 String[] customArgs, String[] customExtensions) {
        String savedSignature = signature(savedArgs, savedExtension);
        if (matchesCurrent(currentIndex, savedSignature, currentBuiltIns) ||
                customNames == null || customArgs == null || customExtensions == null) {
            return -1;
        }

        int count = Math.min(customNames.length,
                Math.min(customArgs.length, customExtensions.length));
        int visibleCustomIndex = 0;
        for (int i = 0; i < count; i++) {
            if (isBuiltInName(customNames[i], currentBuiltIns)) {
                continue;
            }
            if (signature(customArgs[i], customExtensions[i]).equals(savedSignature)) {
                return currentBuiltIns.length + 1 + visibleCustomIndex;
            }
            visibleCustomIndex++;
        }
        return -1;
    }

    private static boolean matchesCurrent(int index, String savedSignature,
                                          String[] currentBuiltIns) {
        String[] fields = presetFields(currentBuiltIns, index);
        return fields != null && signature(fields[1], fields[2]).equals(savedSignature);
    }

    private static boolean isBuiltInName(String name, String[] currentBuiltIns) {
        if (name == null || ConfigInterface.BLANK.equals(name)) {
            return true;
        }
        if (currentBuiltIns == null) {
            return false;
        }
        for (String preset : currentBuiltIns) {
            String[] fields = preset.split("\\|", -1);
            if (fields.length >= 1 && name.equals(fields[0])) {
                return true;
            }
        }
        return false;
    }

    private static String[] presetFields(String[] presets, int index) {
        if (presets == null || index < 0 || index >= presets.length) {
            return null;
        }
        String[] fields = presets[index].split("\\|", -1);
        return fields.length >= 3 ? fields : null;
    }

    private static String signature(String args, String extension) {
        return (args == null ? "" : args) + '\u0000' +
                (extension == null ? "" : extension);
    }

    private BuiltInPresetSchema() {
        throw new AssertionError("No instances");
    }
}
