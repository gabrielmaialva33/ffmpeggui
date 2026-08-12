package com.silentlexx.ffmpeggui_os.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HardwareCodecCommandTest {

    @Test
    public void detectsMediaCodecEncoderInArgumentList() throws Exception {
        assertEquals(true, HardwareCodecCommand.usesMediaCodecEncoder(
                "-map 0 -c:v h264_mediacodec -b:v 8M"));
        assertEquals(true, HardwareCodecCommand.usesMediaCodecEncoder(
                "-map 0 -codec:v:0 hevc_mediacodec -b:v 6M"));
        assertEquals(true, HardwareCodecCommand.usesMediaCodecEncoder(
                "-map 0 -c:v av1_mediacodec -b:v 4M"));
        assertEquals(true, HardwareCodecCommand.usesMediaCodecEncoder(
                "-map 0 -c:v vp9_mediacodec -b:v 5M"));
    }

    @Test
    public void ignoresMediaCodecTextInsideAnotherOptionValue() throws Exception {
        assertEquals(false, HardwareCodecCommand.usesMediaCodecEncoder(
                "-metadata \"comment=try -c:v h264_mediacodec\" -c:v mpeg4"));
    }

    @Test
    public void rejectsNonVideoAndAmbiguousMediaCodecSelectors() throws Exception {
        assertMediaCodecCommandRejected("-c:a vp9_mediacodec -c:v copy",
                "audio or subtitle");
        assertMediaCodecCommandRejected("-codec:s av1_mediacodec -c:v copy",
                "audio or subtitle");
        assertMediaCodecCommandRejected("-c:0 av1_mediacodec out.mp4",
                "explicit -c:v");
        assertMediaCodecCommandRejected("-codec:1 h264_mediacodec out.mp4",
                "explicit -c:v");
        assertMediaCodecCommandRejected("-c hevc_mediacodec out.mp4",
                "explicit -c:v");
        assertMediaCodecCommandRejected("-codec vp9_mediacodec out.webm",
                "explicit -c:v");
        assertMediaCodecCommandRejected("-vcodec:a h264_mediacodec out.mp4",
                "audio or subtitle");
    }

    @Test
    public void rejectsEveryVideoCodecOverrideAroundMediaCodec() throws Exception {
        assertMediaCodecCommandRejected(
                "-c:v h264_mediacodec -c:v mpeg4 out.mp4",
                "Only one video codec selector");
        assertMediaCodecCommandRejected(
                "-c:v mpeg4 -vcodec hevc_mediacodec out.mp4",
                "Only one video codec selector");
        assertMediaCodecCommandRejected(
                "-c:v av1_mediacodec -c:0 mpeg4 out.mp4",
                "Only one video codec selector");
        assertMediaCodecCommandRejected(
                "-c copy -c:v vp9_mediacodec out.webm",
                "Only one video codec selector");
    }

    @Test
    public void lockToComponentReplacesModelSuppliedSoftwareComponent() throws Exception {
        String command = "ffmpeg -y -i \"/storage/input.mp4\" " +
                "-c:v h264_mediacodec -codec_name c2.android.avc.encoder " +
                "\"/storage/output.mp4\"";

        assertEquals("ffmpeg -y -i \"/storage/input.mp4\" " +
                        "-c:v h264_mediacodec -codec_name c2.qti.avc.encoder " +
                        "\"/storage/output.mp4\"",
                HardwareCodecCommand.lockToComponent(
                        command, "h264_mediacodec", "c2.qti.avc.encoder"));
    }

    @Test
    public void lockToComponentPreservesWhitespaceInsideQuotedPaths() throws Exception {
        String command = "ffmpeg -i \"/storage/input  one.mp4\" " +
                "-c:v hevc_mediacodec \"/storage/output  one.mp4\"";

        assertEquals("ffmpeg -i \"/storage/input  one.mp4\" " +
                        "-c:v hevc_mediacodec -codec_name vendor.hevc.encoder " +
                        "\"/storage/output  one.mp4\"",
                HardwareCodecCommand.lockToComponent(
                        command, "hevc_mediacodec", "vendor.hevc.encoder"));
    }

    @Test(expected = HardwareCodecCommand.HardwareCodecException.class)
    public void lockToComponentRejectsUnsafeAndroidName() throws Exception {
        HardwareCodecCommand.lockToComponent(
                "-c:v hevc_mediacodec", "hevc_mediacodec", "codec name;unsafe");
    }

    @Test
    public void lockToComponentSupportsFfmpegVideoCodecAliases() throws Exception {
        assertEquals(
                "ffmpeg -i in.mp4 -vcodec h264_mediacodec -codec_name vendor.avc out.mp4",
                HardwareCodecCommand.lockToComponent(
                        "ffmpeg -i in.mp4 -vcodec h264_mediacodec out.mp4",
                        "h264_mediacodec", "vendor.avc"));
        assertEquals(
                "ffmpeg -i in.mp4 -codec:v:0 hevc_mediacodec -codec_name vendor.hevc out.mp4",
                HardwareCodecCommand.lockToComponent(
                        "ffmpeg -i in.mp4 -codec:v:0 hevc_mediacodec out.mp4",
                        "hevc_mediacodec", "vendor.hevc"));
        assertEquals(
                "ffmpeg -i in.mp4 -c:v av1_mediacodec -codec_name c2.google.av1.encoder out.mp4",
                HardwareCodecCommand.lockToComponent(
                        "ffmpeg -i in.mp4 -c:v av1_mediacodec out.mp4",
                        "av1_mediacodec", "c2.google.av1.encoder"));
        assertEquals(
                "ffmpeg -i in.webm -c:v vp9_mediacodec -codec_name vendor.vp9.encoder out.webm",
                HardwareCodecCommand.lockToComponent(
                        "ffmpeg -i in.webm -c:v vp9_mediacodec out.webm",
                        "vp9_mediacodec", "vendor.vp9.encoder"));
    }

    @Test
    public void lockToComponentRemovesStreamQualifiedOverrides() throws Exception {
        String command = "ffmpeg -i in.mp4 -c:v h264_mediacodec " +
                "-codec_name:v c2.android.avc.encoder " +
                "-CoDeC_NaMe:v:0=c2.android.avc.encoder out.mp4";

        assertEquals(
                "ffmpeg -i in.mp4 -c:v h264_mediacodec -codec_name c2.qti.avc.encoder out.mp4",
                HardwareCodecCommand.lockToComponent(
                        command, "h264_mediacodec", "c2.qti.avc.encoder"));
    }

    @Test
    public void mapsEveryBundledMediaCodecEncoderToAndroidMime() {
        assertEquals("video/avc",
                HardwareCodecCommand.mimeForEncoder("h264_mediacodec"));
        assertEquals("video/hevc",
                HardwareCodecCommand.mimeForEncoder("hevc_mediacodec"));
        assertEquals("video/x-vnd.on2.vp9",
                HardwareCodecCommand.mimeForEncoder("vp9_mediacodec"));
        assertEquals("video/av01",
                HardwareCodecCommand.mimeForEncoder("av1_mediacodec"));
        assertEquals(null,
                HardwareCodecCommand.mimeForEncoder("libaom-av1"));
    }

    @Test
    public void systemSoftwarePolicyIsExplicitAndRemovedBeforeExecution()
            throws Exception {
        String command = "ffmpeg -i in.mp4 " +
                HardwareCodecCommand.SOFTWARE_POLICY_OPTION + " " +
                HardwareCodecCommand.SOFTWARE_POLICY_VALUE +
                " -c:v av1_mediacodec -codec_name c2.android.av1.encoder out.mp4";

        assertTrue(HardwareCodecCommand.requestsSystemSoftwareEncoder(command));
        assertFalse(HardwareCodecCommand.requestsSystemSoftwareEncoder(
                "ffmpeg -i in.mp4 -c:v av1_mediacodec out.mp4"));
        assertEquals(
                "ffmpeg -i in.mp4 -c:v av1_mediacodec " +
                        "-codec_name c2.android.av1.encoder out.mp4",
                HardwareCodecCommand.removeEncoderPolicy(command));
    }

    @Test
    public void rejectsMalformedOrDuplicatedInternalPolicies() throws Exception {
        assertPolicyRejected("-ffmpeggui-codec-policy", "missing its value");
        assertPolicyRejected("-ffmpeggui-codec-policy hardware", "Unknown");
        assertPolicyRejected("-ffmpeggui-codec-policy=hardware", "Unknown");
        assertPolicyRejected("-ffmpeggui-codec-policy:0 system-software", "Malformed");
        assertPolicyRejected(
                "-ffmpeggui-codec-policy system-software " +
                        "-ffmpeggui-codec-policy=system-software",
                "Only one");
    }

    @Test
    public void finalPassPreservesShapeCompatibleComponentChosenByPreview()
            throws Exception {
        HardwareCodecCommand.ComponentResolver resolver = new FakeComponentResolver();
        String preview = HardwareCodecCommand.lockEncoder(
                "ffmpeg -i in.mp4 -c:v h264_mediacodec out.mp4",
                1920, 1080, 30, false, resolver);
        assertTrue(preview.contains("-codec_name vendor.second.avc.encoder"));

        String executable = HardwareCodecCommand.lockEncoder(
                preview, 0, 0, 0, true, resolver);
        assertTrue(executable.contains("-codec_name vendor.second.avc.encoder"));
        assertFalse(executable.contains("vendor.first.avc.encoder"));
    }

    @Test
    public void previewForcesPlanarYuv420pBeforeMediaCodecEncoder()
            throws Exception {
        String preview = HardwareCodecCommand.lockEncoder(
                "ffmpeg -i in.mp4 -c:v h264_mediacodec -b:v 8M out.mp4",
                1920, 1080, 30, false, new FakeComponentResolver());

        assertEquals(
                "ffmpeg -i in.mp4 -pix_fmt yuv420p -c:v h264_mediacodec " +
                        "-codec_name vendor.second.avc.encoder -b:v 8M out.mp4",
                preview);
    }

    @Test
    public void finalPassReplacesPixelFormatOverrideAfterMediaCodecEncoder()
            throws Exception {
        String executable = HardwareCodecCommand.lockEncoder(
                "ffmpeg -i in.mp4 -c:v hevc_mediacodec " +
                        "-pix_fmt nv12 -b:v 6M out.mp4",
                1920, 1080, 30, true, new FakeComponentResolver());

        assertEquals(
                "ffmpeg -i in.mp4 -pix_fmt yuv420p -c:v hevc_mediacodec " +
                        "-codec_name vendor.second.avc.encoder -b:v 6M out.mp4",
                executable);
    }

    @Test
    public void finalPassKeepsSoftwarePolicyAndStripsOnlyItsMarker()
            throws Exception {
        HardwareCodecCommand.ComponentResolver resolver = new FakeComponentResolver();
        String preview = HardwareCodecCommand.lockEncoder(
                "ffmpeg -i in.mp4 -ffmpeggui-codec-policy system-software " +
                        "-c:v av1_mediacodec " +
                        "-codec_name vendor.hardware.av1.encoder out.mp4",
                1280, 720, 30, false, resolver);
        assertTrue(preview.contains("-codec_name c2.android.av1.encoder"));
        assertTrue(preview.contains(HardwareCodecCommand.SOFTWARE_POLICY_OPTION));

        String executable = HardwareCodecCommand.lockEncoder(
                preview, 0, 0, 0, true, resolver);
        assertTrue(executable.contains("-codec_name c2.android.av1.encoder"));
        assertFalse(executable.contains(HardwareCodecCommand.SOFTWARE_POLICY_OPTION));
        assertFalse(executable.contains("vendor.hardware.av1.encoder"));
    }

    @Test
    public void hardwarePreviewReplacesEverySoftwareComponentOverride()
            throws Exception {
        String preview = HardwareCodecCommand.lockEncoder(
                "ffmpeg -i in.mp4 -c:v h264_mediacodec " +
                        "-codec_name c2.android.avc.encoder " +
                        "-codec_name:v:0=c2.android.avc.encoder out.mp4",
                1920, 1080, 30, false, new FakeComponentResolver());

        assertTrue(preview.contains("-codec_name vendor.second.avc.encoder"));
        assertFalse(preview.contains("c2.android.avc.encoder"));
    }

    private static void assertMediaCodecCommandRejected(String command,
                                                        String messagePart) {
        try {
            HardwareCodecCommand.usesMediaCodecEncoder(command);
            fail("Expected MediaCodec command rejection");
        } catch (HardwareCodecCommand.HardwareCodecException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }

    private static void assertPolicyRejected(String policy, String messagePart) {
        try {
            HardwareCodecCommand.requestsSystemSoftwareEncoder(policy);
            fail("Expected policy rejection");
        } catch (HardwareCodecCommand.HardwareCodecException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }

    private static final class FakeComponentResolver
            implements HardwareCodecCommand.ComponentResolver {
        @Override
        public String selectHardware(String mime, int width, int height,
                                     double frameRate) {
            return width > 0 ? "vendor.second.avc.encoder" :
                    "vendor.first.avc.encoder";
        }

        @Override
        public String selectSystemSoftware(String mime, int width, int height,
                                           double frameRate) {
            return "c2.android.av1.encoder";
        }

        @Override
        public boolean isHardware(String mime, String component, int width,
                                  int height, double frameRate) {
            return component.startsWith("vendor.") && component.endsWith(".encoder");
        }

        @Override
        public boolean isSystemSoftware(String mime, String component, int width,
                                        int height, double frameRate) {
            return "c2.android.av1.encoder".equals(component);
        }
    }
}
