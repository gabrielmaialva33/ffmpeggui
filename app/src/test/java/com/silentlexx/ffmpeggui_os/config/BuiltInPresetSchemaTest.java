package com.silentlexx.ffmpeggui_os.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BuiltInPresetSchemaTest {

    private static final String DEFAULT_ARGS =
            "-pix_fmt yuv420p -c:v h264_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 8M " +
                    "-c:a aac -b:a 192k -movflags +faststart";
    private static final String LEGACY_H263 =
            "-s qcif -c:v h263 -q:v 3 -c:a aac -ac 1 -ar 16000 -r 25 -b:a 32k";
    private static final String[] CURRENT = {
            "Video MP4 (H.264 MediaCodec hardware)|" + DEFAULT_ARGS + "|mp4",
            "Video MP4 (H.264 MediaCodec system software fallback)|" +
                    "-ffmpeggui-codec-policy system-software " + DEFAULT_ARGS + "|mp4",
            "Video MP4 (HEVC MediaCodec hardware)|-pix_fmt yuv420p " +
                    "-c:v hevc_mediacodec " +
                    "-ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k " +
                    "-movflags +faststart|mp4",
            "Video MP4 (HEVC MediaCodec system software fallback)|" +
                    "-ffmpeggui-codec-policy system-software -pix_fmt yuv420p " +
                    "-c:v hevc_mediacodec " +
                    "-ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k " +
                    "-movflags +faststart|mp4",
            "Video MP4 (AV1 MediaCodec hardware; device support required)|" +
                    "-pix_fmt yuv420p -c:v av1_mediacodec " +
                    "-ndk_codec 1 -ndk_async 0 -b:v 4M " +
                    "-c:a aac -b:a 192k -movflags +faststart|mp4",
            "Video MP4 (AV1 MediaCodec system software fallback; device support required)|" +
                    "-ffmpeggui-codec-policy system-software -pix_fmt yuv420p " +
                    "-c:v av1_mediacodec " +
                    "-ndk_codec 1 -ndk_async 0 -b:v 4M -c:a aac -b:a 192k " +
                    "-movflags +faststart|mp4",
            "Video WebM (VP9 MediaCodec hardware; device support required)|" +
                    "-pix_fmt yuv420p -c:v vp9_mediacodec " +
                    "-ndk_codec 1 -ndk_async 0 -b:v 5M " +
                    "-c:a opus -strict experimental -b:a 160k|webm",
            "Video WebM (VP9 MediaCodec system software fallback; device support required)|" +
                    "-ffmpeggui-codec-policy system-software -pix_fmt yuv420p " +
                    "-c:v vp9_mediacodec " +
                    "-ndk_codec 1 -ndk_async 0 -b:v 5M -c:a opus " +
                    "-strict experimental -b:a 160k|webm"
    };

    @Test
    public void migratesScreenshotH263SelectionToSafeDefault() {
        assertTrue(shouldReset(0, 5, LEGACY_H263, "3gp"));
        assertEquals(DEFAULT_ARGS, BuiltInPresetSchema.defaultArgs(CURRENT));
        assertEquals("mp4", BuiltInPresetSchema.defaultExtension(CURRENT));
    }

    @Test
    public void migratesOriginalAndShiftedModernBuiltInsByExactIndex() {
        String originalH263 =
                "-s qcif -c:v h263 -q:v 3 -c:a aac -ac 1 -ar 64000 -r 25 -ab 32";
        assertTrue(shouldReset(0, 7, originalH263, "3gp"));

        String oldHevc = "-c:v hevc_mediacodec -ndk_codec 1 -ndk_async 0 " +
                "-b:v 6M -c:a aac -b:a 192k -movflags +faststart";
        assertTrue(shouldReset(0, 1, oldHevc, "mp4"));
    }

    @Test
    public void migratesSavedSchemaV1MediaCodecPresetWithoutPlanarInput() {
        String oldAv1Software =
                "-ffmpeggui-codec-policy system-software " +
                        "-c:v av1_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 4M " +
                        "-c:a aac -b:a 192k -movflags +faststart";

        assertTrue(shouldReset(1, 5, oldAv1Software, "mp4"));
        assertEquals(5, BuiltInPresetSchema.currentReplacementIndex(
                1, 5, oldAv1Software, "mp4", CURRENT));
        assertTrue(BuiltInPresetSchema.argsAt(CURRENT, 5)
                .contains("-pix_fmt yuv420p -c:v av1_mediacodec"));
        assertEquals("mp4", BuiltInPresetSchema.extensionAt(CURRENT, 5));
    }

    @Test
    public void preservesCurrentBuiltInSelection() {
        String[] fields = CURRENT[5].split("\\|", -1);
        assertFalse(shouldReset(0, 5, fields[1], fields[2]));
        assertFalse(shouldReset(0, 0, DEFAULT_ARGS, "mp4"));
    }

    @Test
    public void preservesCustomPresetEvenWhenItDuplicatesLegacyArguments() {
        assertEquals(CURRENT.length + 1,
                BuiltInPresetSchema.customTargetIndex(
                        5, LEGACY_H263, "3gp", CURRENT,
                        new String[]{"My legacy-compatible preset"},
                        new String[]{LEGACY_H263}, new String[]{"3gp"}));
    }

    @Test
    public void customIndexRemapSkipsNamesHiddenByCurrentBuiltIns() {
        assertEquals(CURRENT.length + 1,
                BuiltInPresetSchema.customTargetIndex(
                        20, "-c:v mpeg4 -b:v 3M", "mp4", CURRENT,
                        new String[]{"Video MP4 (H.264 MediaCodec hardware)", "Mine"},
                        new String[]{"ignored", "-c:v mpeg4 -b:v 3M"},
                        new String[]{"avi", "mp4"}));
        assertEquals(-1,
                BuiltInPresetSchema.customTargetIndex(
                        0, DEFAULT_ARGS, "mp4", CURRENT,
                        new String[]{"Duplicate"}, new String[]{DEFAULT_ARGS},
                        new String[]{"mp4"}));
    }

    @Test
    public void preservesManualCommandsAndNearMatches() {
        assertFalse(shouldReset(0, 5,
                "-c:v mpeg4 -b:v 3M -c:a aac", "mp4"));
        assertFalse(shouldReset(0, 5,
                LEGACY_H263 + " -metadata comment=mine", "3gp"));
        assertFalse(shouldReset(0, 5, LEGACY_H263, "mp4"));
        assertFalse(shouldReset(0, 4, LEGACY_H263, "3gp"));
    }

    @Test
    public void migrationVersionMakesDecisionOneTime() {
        assertFalse(shouldReset(BuiltInPresetSchema.VERSION, 5,
                LEGACY_H263, "3gp"));
    }

    private static boolean shouldReset(int version, int index, String args, String extension) {
        return BuiltInPresetSchema.shouldResetToDefault(
                version, index, args, extension, CURRENT);
    }
}
