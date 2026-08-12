package com.silentlexx.ffmpeggui_os.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class AiPlaceholderCommandTest {

    private static final String INPUT = "/storage/emulated/0/DCIM/My Clip.mp4";
    private static final String OUTPUT = "/storage/emulated/0/DCIM/My Clip-ai.mp4";
    private static final Set<String> COMPILED_FILTERS = new HashSet<>(Arrays.asList(
            "amovie", "aresample", "ass", "asendcmd", "azmq", "decimate", "format",
            "fps", "movie", "mpdecimate", "overlay", "scale", "sendcmd", "split",
            "subtitles", "volume", "zmq"));

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
    public void materializeAddsOneGlobalOverwriteFlagBeforeInput() throws Exception {
        String[] templates = {
                "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"",
                "ffmpeg -y -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"",
                "ffmpeg -i \"{{INPUT_1}}\" -y -c copy \"{{OUTPUT_1}}\"",
                "ffmpeg -y -i \"{{INPUT_1}}\" -y -c copy -y \"{{OUTPUT_1}}\""
        };
        for (String template : templates) {
            String command = AiPlaceholderCommand.materialize(template, INPUT, OUTPUT);
            assertTrue(command.startsWith("ffmpeg -y "));
            assertEquals(1, count(command, "-y"));
            assertTrue(command.indexOf("-y") < command.indexOf("-i"));
        }
    }

    @Test
    public void materializeRejectsNoOverwriteFlag() {
        assertRejected("ffmpeg -n -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"");
    }

    @Test
    public void overwriteNormalizationDoesNotTreatOptionValuesAsFlags() throws Exception {
        String command = AiPlaceholderCommand.materialize(
                "ffmpeg -i \"{{INPUT_1}}\" -metadata \"-y\" -metadata \"-n\" " +
                        "\"{{OUTPUT_1}}\"", INPUT, OUTPUT);

        assertTrue(command.startsWith("ffmpeg -y -i "));
        assertTrue(command.contains("-metadata \"-y\""));
        assertTrue(command.contains("-metadata \"-n\""));
    }

    @Test
    public void compiledFilterAllowlistCoversSimpleAndComplexOptionForms() throws Exception {
        String[] fragments = {
                "-vf scale=1280:-2,fps=30",
                "-filter:v format=yuv420p",
                "-filter:v:0 scale=640:-2",
                "-vf decimate",
                "-vf mpdecimate",
                "-af volume=0.8",
                "-filter:a:0 aresample=48000",
                "-filter_complex \"[0:v]split=2[a][b];[a]scale=640:-2[small];" +
                        "[b][small]overlay[out]\" -map [out]"
        };
        for (String fragment : fragments) {
            String command = "ffmpeg -i \"{{INPUT_1}}\" " + fragment +
                    " \"{{OUTPUT_1}}\"";
            assertTrue(AiPlaceholderCommand.materialize(
                    command, INPUT, OUTPUT, COMPILED_FILTERS).startsWith("ffmpeg -y "));
        }
    }

    @Test
    public void rejectsUnescapedExpressionCommaAsFilterChainSyntax() {
        assertFilterRejected("ffmpeg -i \"{{INPUT_1}}\" " +
                        "-vf \"scale=min(1920,iw):-2\" \"{{OUTPUT_1}}\"",
                COMPILED_FILTERS, "iw");
    }

    @Test
    public void compiledFilterAllowlistRejectsHallucinatedFilterInEveryOptionForm() {
        String[] fragments = {
                "-vf mpdeci",
                "-filter:v mpdeci=strength=1",
                "-af mpdeci",
                "-filter:a:0 mpdeci",
                "-filter_complex \"[0:v]scale=640:-2[x];[x]mpdeci[out]\""
        };
        for (String fragment : fragments) {
            assertFilterRejected("ffmpeg -i \"{{INPUT_1}}\" " + fragment +
                    " \"{{OUTPUT_1}}\"", COMPILED_FILTERS, "mpdeci");
        }
    }

    @Test
    public void filterUseFailsClosedWhenRuntimeInventoryIsUnavailable() {
        assertFilterRejected("ffmpeg -i \"{{INPUT_1}}\" -vf scale=640:-2 " +
                "\"{{OUTPUT_1}}\"", new HashSet<>(), "inventory");
    }

    @Test
    public void commandEnvelopeAvoidsNestedJsonQuoteEscaping() throws Exception {
        String command = "ffmpeg -i \"{{INPUT_1}}\" -vf \"scale=iw/2:ih/2\" " +
                "\"{{OUTPUT_1}}\"";
        String result = "FFMPEGGUI_COMMAND_V1_BEGIN\n" + command +
                "\nFFMPEGGUI_COMMAND_V1_END";

        assertEquals(command, AiPlaceholderCommand.parsePastedResult(result));
        assertTrue(AiPlaceholderCommand.materialize(command, INPUT, OUTPUT)
                .contains("-vf \"scale=iw/2:ih/2\""));
    }

    @Test
    public void singleMarkdownCommandFenceIsAcceptedAsCompatibilityFallback() throws Exception {
        String command = "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"";

        assertEquals(command, AiPlaceholderCommand.parsePastedResult(
                "```bash\n" + command + "\n```"));
    }

    @Test
    public void commandEnvelopeAcceptsBomCrlfAndOutsideProse() throws Exception {
        String command = "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"";
        String result = "\ufeffHere is the requested command:\r\n" +
                "FFMPEGGUI_COMMAND_V1_BEGIN\r\n" + command + "\r\n" +
                "FFMPEGGUI_COMMAND_V1_END\r\nDone.";

        assertEquals(command, AiPlaceholderCommand.parseModelResult(result));
        assertEquals(command, AiPlaceholderCommand.parseModelResult(
                "FFMPEGGUI_COMMAND_V1_BEGIN\n\n  " + command + "  \n\n" +
                        "FFMPEGGUI_COMMAND_V1_END"));
    }

    @Test
    public void rejectsAmbiguousOrMultilineCommandEnvelopes() {
        String command = "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"";
        assertParseRejected("FFMPEGGUI_COMMAND_V1_BEGIN\n" + command + "\n" +
                "FFMPEGGUI_COMMAND_V1_END\nFFMPEGGUI_COMMAND_V1_BEGIN\n" + command +
                "\nFFMPEGGUI_COMMAND_V1_END");
        assertParseRejected("FFMPEGGUI_COMMAND_V1_BEGIN\n" + command +
                "\nffmpeg -version\nFFMPEGGUI_COMMAND_V1_END");
        assertParseRejected("FFMPEGGUI_COMMAND_V1_BEGIN\n" + command);
        assertParseRejected("{\"command\":\"ffmpeg -i \"{{INPUT_1}}\" -c copy " +
                "\"{{OUTPUT_1}}\"\"}");
    }

    @Test
    public void rejectsHiddenUnicodeFormatCharacters() {
        assertParseRejected("ffmpeg -i \"{{INPUT_1}}\" -metadata title=x\u202Ey " +
                "\"{{OUTPUT_1}}\"");
        assertParseRejected("FFMPEGGUI_COMMAND_V1_BEGIN\nffmpeg -i \"{{INPUT_1}}\" " +
                "-c copy \"{{OUTPUT_1}}\"\u2028\nFFMPEGGUI_COMMAND_V1_END");
        assertParseRejected("ffmpeg -i \"{{INPUT_1}}\" -metadata title=x\uDB40\uDC01y " +
                "\"{{OUTPUT_1}}\"");
        assertParseRejected("ffmpeg -i \"{{INPUT_1}}\" -metadata title=x\uDB40\uDC61y " +
                "\"{{OUTPUT_1}}\"");
        assertParseRejected("ffmpeg -i \"{{INPUT_1}}\" -metadata title=x\uDB40y " +
                "\"{{OUTPUT_1}}\"");
    }

    @Test
    public void resultLimitCountsRawWhitespaceAndProse() {
        assertParseRejected(" ".repeat(32_769) +
                "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"");
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
                "-vf curves=plot=x",
                "-vf vidstabdetect=result=x",
                "-vf vidstabtransform=input=x",
                "-vf libvmaf=log_path=report.json",
                "-vf zmq"
        };
        for (String fragment : fragments) {
            assertRejected("ffmpeg -i \"{{INPUT_1}}\" " + fragment +
                    " \"{{OUTPUT_1}}\"");
        }
    }

    @Test
    public void rejectsInstancedFiltersThatCanReadSideFiles() {
        String[] graphs = {
                "sendcmd@commands=f=secret",
                "asendcmd@commands=f=secret",
                "movie@source=secret",
                "amovie@source=secret",
                "subtitles@caption=secret",
                "ass@caption=secret",
                "zmq@control",
                "azmq@control"
        };
        for (String graph : graphs) {
            try {
                FfmpegCommandValidator.validate(
                        "ffmpeg -y -i \"" + INPUT + "\" -vf \"" + graph +
                                "\" \"" + OUTPUT + "\"",
                        INPUT, OUTPUT, COMPILED_FILTERS);
                fail("Expected side-file filter rejection: " + graph);
            } catch (FfmpegCommandValidator.ValidationException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("side file"));
            }
        }
    }

    @Test
    public void aiFilterPolicyAllowsCommonFrameFiltersAndRejectsIoFilters() {
        Set<String> compiled = new HashSet<>(Arrays.asList(
                "scale", "overlay", "mpdecimate", "loudnorm", "fieldhint",
                "arnndn", "nnedi", "find_rect", "fsync", "removelogo", "cellauto",
                "life", "movie", "subtitles", "drawtext", "loudnorm"));
        Set<String> safe = FfmpegFilterInventory.safeForAi(compiled);

        assertEquals(new HashSet<>(Arrays.asList(
                "mpdecimate", "overlay", "scale")), safe);
        for (String forbidden : Arrays.asList(
                "fieldhint", "arnndn", "nnedi", "find_rect", "fsync", "removelogo",
                "cellauto", "life", "movie", "subtitles", "drawtext", "loudnorm")) {
            assertTrue("Unexpected AI filter: " + forbidden, !safe.contains(forbidden));
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

    @Test
    public void validatorRejectsSupplementaryFormatCharactersAndInvalidSurrogates() {
        String[] commands = {
                "ffmpeg -i \"" + INPUT + "\" -metadata title=x\uDB40\uDC01y \"" +
                        OUTPUT + "\"",
                "ffmpeg -i \"" + INPUT + "\" -metadata title=x\uDB40y \"" +
                        OUTPUT + "\""
        };
        for (String command : commands) {
            try {
                FfmpegCommandValidator.validate(command, INPUT, OUTPUT);
                fail("Expected Unicode format rejection: " + command);
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

    private static void assertParseRejected(String result) {
        try {
            AiPlaceholderCommand.parseModelResult(result);
            fail("Expected parse rejection: " + result);
        } catch (AiPlaceholderCommand.ParseException expected) {
            // expected
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

    private static void assertFilterRejected(String command, Set<String> compiledFilters,
                                             String messageFragment) {
        try {
            AiPlaceholderCommand.materialize(command, INPUT, OUTPUT, compiledFilters);
            fail("Expected filter rejection: " + command);
        } catch (AiPlaceholderCommand.ParseException e) {
            fail("Expected runtime filter validation: " + e.getMessage());
        } catch (FfmpegCommandValidator.ValidationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().toLowerCase()
                    .contains(messageFragment.toLowerCase()));
        }
    }
}
