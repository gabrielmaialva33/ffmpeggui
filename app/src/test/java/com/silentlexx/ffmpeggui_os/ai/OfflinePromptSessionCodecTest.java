package com.silentlexx.ffmpeggui_os.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public class OfflinePromptSessionCodecTest {

    private static final long NOW = 2_000_000_000_000L;
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    public void roundTripPreservesBoundPathsAndTimestamp() throws Exception {
        OfflinePromptSession original = new OfflinePromptSession(
                NOW - 1000, TOKEN, "/storage/emulated/0/影片/source.mp4",
                "/storage/emulated/0/影片/output.mp4");

        OfflinePromptSession decoded = OfflinePromptSessionCodec.decode(
                OfflinePromptSessionCodec.encode(original), NOW);

        assertEquals(original.getCreatedAtMillis(), decoded.getCreatedAtMillis());
        assertEquals(original.getSessionToken(), decoded.getSessionToken());
        assertEquals(original.getInputPath(), decoded.getInputPath());
        assertEquals(original.getOutputPath(), decoded.getOutputPath());
    }

    @Test
    public void unknownVersionAndTruncatedPayloadFailClosed() throws Exception {
        byte[] encoded = validEncoding();
        encoded[7] = 2;
        assertInvalid(encoded, NOW, "invalid");

        byte[] truncated = Arrays.copyOf(validEncoding(), 12);
        assertInvalid(truncated, NOW, "invalid");
    }

    @Test
    public void expiredAndFutureSessionsFailClosed() throws Exception {
        OfflinePromptSession expired = new OfflinePromptSession(
                NOW - OfflinePromptSessionCodec.MAX_AGE_MILLIS - 1,
                TOKEN,
                "/storage/emulated/0/a.mp4", "/storage/emulated/0/a-out.mp4");
        assertInvalid(OfflinePromptSessionCodec.encode(expired), NOW, "expired");

        OfflinePromptSession future = new OfflinePromptSession(
                NOW + 6L * 60L * 1000L,
                TOKEN,
                "/storage/emulated/0/a.mp4", "/storage/emulated/0/a-out.mp4");
        assertInvalid(OfflinePromptSessionCodec.encode(future), NOW, "invalid");
    }

    @Test
    public void controlCharactersAndEmptyPathsCannotBePersisted() {
        assertEncodeRejected(new OfflinePromptSession(
                NOW, TOKEN, "", "/storage/emulated/0/out.mp4"));
        assertEncodeRejected(new OfflinePromptSession(
                NOW, TOKEN, "/storage/emulated/0/in\njected.mp4",
                "/storage/emulated/0/out.mp4"));
        assertEncodeRejected(new OfflinePromptSession(
                NOW, "bad-token", "/storage/emulated/0/in.mp4",
                "/storage/emulated/0/out.mp4"));
    }

    private static byte[] validEncoding() throws Exception {
        return OfflinePromptSessionCodec.encode(new OfflinePromptSession(
                NOW - 1000, TOKEN, "/storage/emulated/0/a.mp4",
                "/storage/emulated/0/a-out.mp4"));
    }

    private static void assertInvalid(byte[] encoded, long now, String messageFragment) {
        try {
            OfflinePromptSessionCodec.decode(encoded, now);
            fail("Expected invalid session rejection");
        } catch (OfflinePromptSessionCodec.SessionException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains(messageFragment));
        }
    }

    private static void assertEncodeRejected(OfflinePromptSession session) {
        try {
            OfflinePromptSessionCodec.encode(session);
            fail("Expected session encoding rejection");
        } catch (OfflinePromptSessionCodec.SessionException expected) {
            assertTrue(expected.getMessage().contains("invalid"));
        }
    }
}
