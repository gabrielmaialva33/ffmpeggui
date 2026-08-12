package com.silentlexx.ffmpeggui_os.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class AiPlaceholderCommandTest {

    private static final String INPUT = "/storage/emulated/0/DCIM/My Clip.mp4";
    private static final String OUTPUT = "/storage/emulated/0/DCIM/My Clip-ai.mp4";

    @Test
    public void placeholderPatternEscapesClosingBracesForAndroidIcu() throws Exception {
        Field field = AiPlaceholderCommand.class.getDeclaredField("PLACEHOLDER");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);

        assertEquals("\\{\\{([A-Za-z0-9_]+)\\}\\}", pattern.pattern());
    }

    @Test
    public void jsonPlaceholderRoundTripMaterializesOnlyKnownPaths() throws Exception {
        String json = "{\"command\":\"ffmpeg -y -i \\\"{{INPUT_1}}\\\" " +
                "-vf scale=iw/2:ih/2 -c:v h264_mediacodec " +
                "\\\"{{OUTPUT_1}}\\\"\"}";

        String placeholder = AiPlaceholderCommand.parseJsonOnly(json);
        String command = AiPlaceholderCommand.materialize(placeholder, INPUT, OUTPUT);

        assertEquals("ffmpeg -y -i \"" + INPUT + "\" -vf scale=iw/2:ih/2 " +
                "-c:v h264_mediacodec \"" + OUTPUT + "\"", command);
    }

    @Test
    public void plainCommandPasteRoundTrips() throws Exception {
        String placeholder = "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"";
        assertEquals(placeholder, AiPlaceholderCommand.parsePastedResult(placeholder));
        assertTrue(AiPlaceholderCommand.materialize(placeholder, INPUT, OUTPUT)
                .endsWith("\"" + OUTPUT + "\""));
    }

    @Test
    public void boundPlaceholderRoundTripRejectsDifferentSession() throws Exception {
        String token = "0123456789abcdef0123456789abcdef";
        String other = "abcdef0123456789abcdef0123456789";
        String inputPlaceholder = OfflinePlaceholderBinding.inputPlaceholder(token);
        String outputPlaceholder = OfflinePlaceholderBinding.outputPlaceholder(token);
        String template = "ffmpeg -i \"" + inputPlaceholder +
                "\" -c copy \"" + outputPlaceholder + "\"";

        assertTrue(AiPlaceholderCommand.materialize(template, INPUT, OUTPUT,
                inputPlaceholder, outputPlaceholder).contains(INPUT));
        try {
            AiPlaceholderCommand.materialize(template, INPUT, OUTPUT,
                    OfflinePlaceholderBinding.inputPlaceholder(other),
                    OfflinePlaceholderBinding.outputPlaceholder(other));
            fail("Expected session mismatch rejection");
        } catch (AiPlaceholderCommand.ParseException expected) {
            // expected
        }
    }

    @Test
    public void rejectsUnknownMissingRepeatedAndMisplacedPlaceholders() {
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -metadata x={{EVIL}} " +
                "\"{{OUTPUT_1}}\"");
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -i \"{{INPUT_1}}\" " +
                "\"{{OUTPUT_1}}\"");
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -c copy /tmp/output.mp4");
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\" " +
                "/tmp/second-output.mp4");
        assertRejected("ffmpeg -i prefix\"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"");
    }

    @Test
    public void rejectsHiddenIoProtocolsAndSideFiles() {
        String[] fragments = {
                "-metadata x=/data/local/tmp/secret",
                "-metadata x=../secret",
                "-metadata x=file:/data/secret",
                "-metadata x=http://example.test/a",
                "-metadata x=https://example.test/a",
                "-metadata x=pipe:1",
                "-metadata x=fd:3",
                "-metadata x=concat:/data/list",
                "-metadata x=crypto:/data/key",
                "-metadata x=tcp://example.test:9000",
                "-metadata x=udp://example.test:9000",
                "-metadata x=rtmp://example.test/live",
                "-metadata x=rtp://example.test:9000",
                "-metadata x=srt://example.test:9000",
                "-metadata x=ftp://example.test/a",
                "-metadata x=ssh://example.test/a",
                "-metadata x=tls://example.test:9000",
                "-metadata x=unix:/data/socket",
                "-metadata x=tee:out.mp4",
                "-metadata x=data:text/plain,secret",
                "-metadata x=cache:http://example.test/a",
                "-metadata x=subfile:secret.srt",
                "-metadata x=zmq:tcp://example.test:5555",
                "-metadata -",
                "-filter_script:v /data/filter.txt",
                "-attach /data/cover.jpg",
                "-dump_attachment:t /data/font.ttf",
                "-progress pipe:1",
                "-passlogfile /data/passlog",
                "-stats_mux_pre /data/stats",
                "-stats_enc_pre /data/stats",
                "-stats_enc_post /data/stats",
                "-fpre /data/preset",
                "-apre /data/preset",
                "-vpre /data/preset"
        };
        for (String fragment : fragments) {
            assertRejected("ffmpeg -i \"{{INPUT_1}}\" " + fragment +
                    " \"{{OUTPUT_1}}\"");
        }
    }

    @Test
    public void rejectsExtraRelativeAndNetworkOutputs() {
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -c copy leak.mp4 \"{{OUTPUT_1}}\"");
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -c copy tcp://example.test:9000 " +
                "\"{{OUTPUT_1}}\"");
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -c copy leak.mp4 " +
                "udp://example.test:9000 \"{{OUTPUT_1}}\"");
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" -c copy - \"{{OUTPUT_1}}\"");
    }

    @Test
    public void rejectsMuxersAndFiltersThatCreateSideFiles() {
        String[] fragments = {
                "-f hls",
                "-f dash",
                "-f segment",
                "-af ametadata=mode=print:file=leak",
                "-vf metadata=mode=print:file=leak",
                "-vf psnr=stats_file=stats.log",
                "-vf ssim=stats_file=stats.log",
                "-vf libvmaf=log_path=report.json",
                "-vf zmq"
        };
        for (String fragment : fragments) {
            assertRejected("ffmpeg -i \"{{INPUT_1}}\" " + fragment +
                    " \"{{OUTPUT_1}}\"");
        }
    }

    @Test
    public void rejectsPresetOnlySystemSoftwarePolicyFromAiResults() {
        assertRejected("ffmpeg -i \"{{INPUT_1}}\" " +
                "-ffmpeggui-codec-policy system-software " +
                "-c:v av1_mediacodec \"{{OUTPUT_1}}\"");
    }

    @Test
    public void acceptsExplicitSingleFileMuxer() throws Exception {
        String command = "ffmpeg -i \"{{INPUT_1}}\" -f mp4 \"{{OUTPUT_1}}\"";
        assertTrue(AiPlaceholderCommand.materialize(command, INPUT, OUTPUT)
                .endsWith("\"" + OUTPUT + "\""));
    }

    @Test
    public void rejectsOutputPathTokenInjection() {
        String injectedOutput = "/storage/emulated/0/out\" -f hls " +
                "\"/storage/emulated/0/second.m3u8";
        try {
            AiPlaceholderCommand.materialize(
                    "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"",
                    INPUT, injectedOutput);
            fail("Expected output path rejection");
        } catch (AiPlaceholderCommand.ParseException |
                 FfmpegCommandValidator.ValidationException expected) {
            // expected
        }
    }

    @Test
    public void validatorRejectsPipeFdAndProgressOnAnyToken() {
        String[] commands = {
                "ffmpeg -i \"" + INPUT + "\" -metadata x=1 pipe:1 \"" + OUTPUT + "\"",
                "ffmpeg -i \"" + INPUT + "\" -metadata x=1 fd:3 \"" + OUTPUT + "\"",
                "ffmpeg -i \"" + INPUT + "\" -progress \"" + OUTPUT + "\"",
                "ffmpeg -i \"" + INPUT + "\" -progress pipe:1 \"" + OUTPUT + "\""
        };
        for (String command : commands) {
            try {
                FfmpegCommandValidator.validate(command, INPUT, OUTPUT);
                fail("Expected rejection: " + command);
            } catch (FfmpegCommandValidator.ValidationException expected) {
                // expected
            }
        }
    }

    private static void assertRejected(String command) {
        try {
            AiPlaceholderCommand.materialize(command, INPUT, OUTPUT);
            fail("Expected rejection: " + command);
        } catch (AiPlaceholderCommand.ParseException |
                 FfmpegCommandValidator.ValidationException expected) {
            // expected
        }
    }
}
