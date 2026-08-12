package com.silentlexx.ffmpeggui_os.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

public class BuiltInPresetsTest {

    private static final List<String> EXPECTED_PRESETS = Arrays.asList(
            "Video MP4 (H.264 MediaCodec hardware)|-pix_fmt yuv420p -c:v h264_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 8M -c:a aac -b:a 192k -movflags +faststart|mp4",
            "Video MP4 (H.264 MediaCodec system software fallback)|-ffmpeggui-codec-policy system-software -pix_fmt yuv420p -c:v h264_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 8M -c:a aac -b:a 192k -movflags +faststart|mp4",
            "Video MP4 (HEVC MediaCodec hardware)|-pix_fmt yuv420p -c:v hevc_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k -movflags +faststart|mp4",
            "Video MP4 (HEVC MediaCodec system software fallback)|-ffmpeggui-codec-policy system-software -pix_fmt yuv420p -c:v hevc_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 6M -c:a aac -b:a 192k -movflags +faststart|mp4",
            "Video MP4 (AV1 MediaCodec hardware; device support required)|-pix_fmt yuv420p -c:v av1_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 4M -c:a aac -b:a 192k -movflags +faststart|mp4",
            "Video MP4 (AV1 MediaCodec system software fallback; device support required)|-ffmpeggui-codec-policy system-software -pix_fmt yuv420p -c:v av1_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 4M -c:a aac -b:a 192k -movflags +faststart|mp4",
            "Video WebM (VP9 MediaCodec hardware; device support required)|-pix_fmt yuv420p -c:v vp9_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 5M -c:a opus -strict experimental -b:a 160k|webm",
            "Video WebM (VP9 MediaCodec system software fallback; device support required)|-ffmpeggui-codec-policy system-software -pix_fmt yuv420p -c:v vp9_mediacodec -ndk_codec 1 -ndk_async 0 -b:v 5M -c:a opus -strict experimental -b:a 160k|webm",
            "Video WebM (VP9 video copy + Opus audio; VP9 input)|-map 0:v:0 -map 0:a:0? -c:v copy -c:a opus -strict experimental -b:a 160k|webm",
            "Video MP4 (stream copy)|-map 0 -c copy -movflags +faststart|mp4",
            "Video Matroska (stream copy)|-map 0 -c copy|mkv",
            "Audio M4A (AAC)|-vn -c:a aac -b:a 192k -ar 48000|m4a",
            "Audio Opus (native)|-vn -c:a opus -strict experimental -b:a 128k|opus",
            "Audio FLAC|-vn -c:a flac|flac",
            "Video MP4 (H.264 libx264 bundled software)|-c:v libx264 -preset medium " +
                    "-crf 23 -pix_fmt yuv420p -c:a aac -b:a 192k " +
                    "-movflags +faststart|mp4",
            "Video MP4 (HEVC libx265 bundled software)|-c:v libx265 -preset medium " +
                    "-crf 28 -pix_fmt yuv420p -tag:v hvc1 -c:a aac -b:a 192k " +
                    "-movflags +faststart|mp4",
            "Video WebM (VP9 libvpx bundled software)|-c:v libvpx-vp9 -b:v 0 " +
                    "-crf 30 -row-mt 1 -cpu-used 4 -c:a opus -strict experimental " +
                    "-b:a 160k|webm",
            "Video WebM (AV1 libaom bundled software)|-c:v libaom-av1 -b:v 0 " +
                    "-crf 32 -cpu-used 6 -row-mt 1 -c:a opus -strict experimental " +
                    "-b:a 160k|webm"
    );

    @Test
    public void builtInPresetArrayMatchesModernFfmpeg8SetExactly() throws Exception {
        List<String> presets = readPresetItems();
        assertEquals(EXPECTED_PRESETS, presets);

        String joined = String.join("\n", presets);
        for (String obsolete : Arrays.asList(
                "h263", "mpeg2video", "-target pal-dvd", "mjpeg", "Video AVI", "3GP")) {
            assertFalse("obsolete preset token: " + obsolete,
                    joined.toLowerCase().contains(obsolete.toLowerCase()));
        }
        for (String bundledSoftwareEncoder : Arrays.asList(
                "libx264", "libx265", "libvpx-vp9", "libaom-av1")) {
            assertTrue("bundled encoder: " + bundledSoftwareEncoder,
                    joined.contains("-c:v " + bundledSoftwareEncoder));
        }

        int hardwarePresets = 0;
        int softwareFallbackPresets = 0;
        for (String preset : presets) {
            String[] fields = preset.split("\\|", -1);
            assertEquals("preset fields: " + preset, 3, fields.length);
            if (!fields[1].contains("_mediacodec")) {
                continue;
            }
            int pixelFormat = fields[1].indexOf("-pix_fmt yuv420p");
            int encoder = fields[1].indexOf("-c:v ");
            assertTrue("MediaCodec preset must force planar input before the encoder: " + preset,
                    pixelFormat >= 0 && pixelFormat < encoder);
            assertTrue("MediaCodec preset must enter the runtime locker: " + preset,
                    HardwareCodecCommand.usesMediaCodecEncoder(fields[1]));
            if (fields[1].contains(HardwareCodecCommand.SOFTWARE_POLICY_OPTION)) {
                softwareFallbackPresets++;
                assertTrue(HardwareCodecCommand.requestsSystemSoftwareEncoder(fields[1]));
            } else {
                hardwarePresets++;
                assertFalse(HardwareCodecCommand.requestsSystemSoftwareEncoder(fields[1]));
            }
        }
        assertEquals(4, hardwarePresets);
        assertEquals(4, softwareFallbackPresets);
        assertEquals(18, presets.size());
    }

    private static List<String> readPresetItems() throws Exception {
        Path resource = Paths.get("src/main/res/values/array.xml");
        if (!Files.isRegularFile(resource)) {
            resource = Paths.get("app/src/main/res/values/array.xml");
        }

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resource.toFile());
        NodeList arrays = document.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (!"presets".equals(array.getAttribute("name"))) {
                continue;
            }
            List<String> values = new ArrayList<>();
            NodeList children = array.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        "item".equals(child.getNodeName())) {
                    values.add(child.getTextContent());
                }
            }
            return values;
        }
        throw new AssertionError("presets string-array is missing");
    }
}
