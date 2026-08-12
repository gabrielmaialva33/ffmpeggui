package com.silentlexx.ffmpeggui_os.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class OfflineAiResultResolverTest {

    private static final String INPUT_A = "/storage/emulated/0/A/source-a.mp4";
    private static final String OUTPUT_A = "/storage/emulated/0/A/result-a.mp4";
    private static final String INPUT_B = "/storage/emulated/0/B/source-b.mp4";
    private static final String OUTPUT_B = "/storage/emulated/0/B/result-b.mp4";
    private static final String TOKEN_A = "0123456789abcdef0123456789abcdef";
    private static final String TOKEN_B = "abcdef0123456789abcdef0123456789";
    private static final String TEMPLATE =
            "ffmpeg -y -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"";
    private static final String JSON =
            "{\"command\":\"ffmpeg -y -i \\\"{{INPUT_1}}\\\" -c copy " +
                    "\\\"{{OUTPUT_1}}\\\"\"}";
    private static final String BOUND_TEMPLATE_A =
            "ffmpeg -y -i \"{{INPUT_1_SID_" + TOKEN_A + "}}\" -c copy " +
                    "\"{{OUTPUT_1_SID_" + TOKEN_A + "}}\"";
    private static final String BOUND_JSON_A =
            "{\"command\":\"ffmpeg -y -i \\\"{{INPUT_1_SID_" + TOKEN_A +
                    "}}\\\" -c copy \\\"{{OUTPUT_1_SID_" + TOKEN_A +
                    "}}\\\"\"}";
    private static final String BOUND_ENVELOPE_A =
            "FFMPEGGUI_COMMAND_V1_BEGIN\n" + BOUND_TEMPLATE_A +
                    "\nFFMPEGGUI_COMMAND_V1_END";

    @Test
    public void copiedSessionWinsAfterUiPathsChange() throws Exception {
        OfflinePromptSession copiedSession =
                new OfflinePromptSession(1234L, TOKEN_A, INPUT_A, OUTPUT_A);

        String command = OfflineAiResultResolver.resolve(
                BOUND_JSON_A, copiedSession, INPUT_B, OUTPUT_B);

        assertTrue(command.contains("\"" + INPUT_A + "\""));
        assertTrue(command.endsWith("\"" + OUTPUT_A + "\""));
        assertFalse(command.contains(INPUT_B));
        assertFalse(command.contains(OUTPUT_B));
    }

    @Test
    public void boundPlainCommandUsesCopiedSession() throws Exception {
        OfflinePromptSession copiedSession =
                new OfflinePromptSession(1234L, TOKEN_A, INPUT_A, OUTPUT_A);

        String command = OfflineAiResultResolver.resolve(
                BOUND_TEMPLATE_A, copiedSession, INPUT_B, OUTPUT_B);

        assertTrue(command.contains(INPUT_A));
        assertFalse(command.contains(INPUT_B));
    }

    @Test
    public void boundEnvelopeUsesCopiedSession() throws Exception {
        OfflinePromptSession copiedSession =
                new OfflinePromptSession(1234L, TOKEN_A, INPUT_A, OUTPUT_A);

        String command = OfflineAiResultResolver.resolve(
                BOUND_ENVELOPE_A, copiedSession, INPUT_B, OUTPUT_B);

        assertTrue(command.contains(INPUT_A));
        assertFalse(command.contains(INPUT_B));
    }

    @Test
    public void newerSessionRejectsOldResultAndConsumedResultCannotReplay() {
        OfflinePromptSession newerSession =
                new OfflinePromptSession(1234L, TOKEN_B, INPUT_B, OUTPUT_B);
        assertResultRejected(BOUND_JSON_A, newerSession, INPUT_B, OUTPUT_B);
        assertResultRejected(BOUND_JSON_A, null, INPUT_B, OUTPUT_B);
        assertResultRejected(BOUND_ENVELOPE_A, newerSession, INPUT_B, OUTPUT_B);
    }

    @Test
    public void missingSessionUsesCurrentUiPaths() throws Exception {
        String command = OfflineAiResultResolver.resolve(TEMPLATE, null, INPUT_B, OUTPUT_B);

        assertTrue(command.contains("\"" + INPUT_B + "\""));
        assertTrue(command.endsWith("\"" + OUTPUT_B + "\""));
    }

    @Test
    public void clipboardAcceptsJsonAndPlainCommand() throws Exception {
        assertEquals(JSON, OfflineAiResultResolver.requireClipboardText(true, JSON));
        assertTrue(OfflineAiResultResolver.resolve(
                OfflineAiResultResolver.requireClipboardText(true, JSON),
                null, INPUT_B, OUTPUT_B).contains(INPUT_B));
        assertTrue(OfflineAiResultResolver.resolve(
                OfflineAiResultResolver.requireClipboardText(true, TEMPLATE),
                null, INPUT_B, OUTPUT_B).contains(INPUT_B));
    }

    @Test
    public void clipboardRejectsNonTextEmptyOversizeAndInvalidResult() {
        assertClipboardRejected(false, "ffmpeg");
        assertClipboardRejected(true, null);
        assertClipboardRejected(true, "  \n");
        assertClipboardRejected(true, "x".repeat(32_769));

        try {
            OfflineAiResultResolver.resolve("model prose", null, INPUT_B, OUTPUT_B);
            fail("Expected invalid clipboard result rejection");
        } catch (AiPlaceholderCommand.ParseException |
                 FfmpegCommandValidator.ValidationException expected) {
            // expected
        }
    }

    private static void assertClipboardRejected(boolean hasText, CharSequence text) {
        try {
            OfflineAiResultResolver.requireClipboardText(hasText, text);
            fail("Expected clipboard rejection");
        } catch (OfflineAiResultResolver.ClipboardException expected) {
            // expected
        }
    }

    private static void assertResultRejected(String result, OfflinePromptSession session,
                                             String currentInput, String currentOutput) {
        try {
            OfflineAiResultResolver.resolve(result, session, currentInput, currentOutput);
            fail("Expected bound result rejection");
        } catch (AiPlaceholderCommand.ParseException |
                 FfmpegCommandValidator.ValidationException expected) {
            // expected
        }
    }
}
