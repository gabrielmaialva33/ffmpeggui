package com.silentlexx.ffmpeggui_os.ai;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class OfflinePromptSessionCodec {

    static final long MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L;
    static final int MAX_ENCODED_BYTES = 40 * 1024;

    private static final int MAGIC = 0x46465053; // FFPS
    private static final int VERSION = 1;
    private static final int MAX_PATH_BYTES = 16 * 1024;
    private static final long MAX_FUTURE_SKEW_MILLIS = 5L * 60L * 1000L;

    static byte[] encode(OfflinePromptSession session) throws SessionException {
        if (session == null || session.getCreatedAtMillis() <= 0) {
            throw invalid();
        }
        byte[] token = encodeToken(session.getSessionToken());
        byte[] input = encodePath(session.getInputPath());
        byte[] output = encodePath(session.getOutputPath());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    24 + input.length + output.length);
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                data.writeInt(MAGIC);
                data.writeInt(VERSION);
                data.writeLong(session.getCreatedAtMillis());
                writeBytes(data, token);
                writeBytes(data, input);
                writeBytes(data, output);
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_ENCODED_BYTES) {
                throw invalid();
            }
            return encoded;
        } catch (IOException e) {
            throw new SessionException("Could not encode the offline prompt session", e);
        }
    }

    static OfflinePromptSession decode(byte[] encoded, long nowMillis)
            throws SessionException {
        if (encoded == null || encoded.length == 0 ||
                encoded.length > MAX_ENCODED_BYTES || nowMillis <= 0) {
            throw invalid();
        }
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (data.readInt() != MAGIC || data.readInt() != VERSION) {
                throw invalid();
            }
            long createdAt = data.readLong();
            if (createdAt <= 0 || createdAt > nowMillis + MAX_FUTURE_SKEW_MILLIS) {
                throw invalid();
            }
            if (nowMillis - createdAt > MAX_AGE_MILLIS) {
                throw new SessionException(
                        "The copied offline prompt session expired; copy a new prompt");
            }
            String token = decodeToken(readBytes(data, 32));
            String input = decodePath(readBytes(data, MAX_PATH_BYTES));
            String output = decodePath(readBytes(data, MAX_PATH_BYTES));
            if (data.read() != -1) {
                throw invalid();
            }
            return new OfflinePromptSession(createdAt, token, input, output);
        } catch (EOFException e) {
            throw invalid(e);
        } catch (IOException e) {
            throw invalid(e);
        }
    }

    private static byte[] encodePath(String path) throws SessionException {
        validatePath(path);
        byte[] encoded = path.getBytes(StandardCharsets.UTF_8);
        if (encoded.length == 0 || encoded.length > MAX_PATH_BYTES) {
            throw invalid();
        }
        return encoded;
    }

    private static byte[] encodeToken(String token) throws SessionException {
        if (!OfflinePlaceholderBinding.isValidToken(token)) {
            throw invalid();
        }
        return token.getBytes(StandardCharsets.US_ASCII);
    }

    private static String decodeToken(byte[] encoded) throws SessionException {
        String token = new String(encoded, StandardCharsets.US_ASCII);
        if (!OfflinePlaceholderBinding.isValidToken(token)) {
            throw invalid();
        }
        return token;
    }

    private static String decodePath(byte[] encoded) throws SessionException {
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
            validatePath(value);
            return value;
        } catch (CharacterCodingException e) {
            throw invalid(e);
        }
    }

    private static void validatePath(String path) throws SessionException {
        if (path == null || path.isEmpty()) {
            throw invalid();
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw invalid();
            }
        }
    }

    private static void writeBytes(DataOutputStream data, byte[] value) throws IOException {
        data.writeInt(value.length);
        data.write(value);
    }

    private static byte[] readBytes(DataInputStream data, int maxLength)
            throws IOException, SessionException {
        int length = data.readInt();
        if (length <= 0 || length > maxLength) {
            throw invalid();
        }
        byte[] value = new byte[length];
        data.readFully(value);
        return value;
    }

    static final class SessionException extends Exception {
        SessionException(String message) {
            super(message);
        }

        SessionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static SessionException invalid() {
        return new SessionException(
                "The saved offline prompt session is invalid; copy a new prompt");
    }

    private static SessionException invalid(Throwable cause) {
        return new SessionException(
                "The saved offline prompt session is invalid; copy a new prompt", cause);
    }

    private OfflinePromptSessionCodec() {
        throw new AssertionError("No instances");
    }
}
