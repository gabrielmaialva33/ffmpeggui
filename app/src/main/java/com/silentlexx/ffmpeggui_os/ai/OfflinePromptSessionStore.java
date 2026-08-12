package com.silentlexx.ffmpeggui_os.ai;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class OfflinePromptSessionStore {

    private static final String SESSION_FILE = "offline_ai_prompt_session_v1.bin";

    private final AtomicFile sessionFile;

    public OfflinePromptSessionStore(Context context) {
        File file = new File(context.getNoBackupFilesDir(), SESSION_FILE);
        sessionFile = new AtomicFile(file);
    }

    public void save(String sessionToken, String inputPath, String outputPath)
            throws IOException, SessionException {
        byte[] encoded;
        try {
            encoded = OfflinePromptSessionCodec.encode(new OfflinePromptSession(
                    System.currentTimeMillis(), sessionToken, inputPath, outputPath));
        } catch (OfflinePromptSessionCodec.SessionException e) {
            throw new SessionException(e.getMessage(), e);
        }
        FileOutputStream output = null;
        try {
            output = sessionFile.startWrite();
            output.write(encoded);
            sessionFile.finishWrite(output);
            output = null;
        } finally {
            if (output != null) {
                sessionFile.failWrite(output);
            }
        }
    }

    public OfflinePromptSession loadIfPresent()
            throws IOException, SessionException {
        File file = sessionFile.getBaseFile();
        if (!file.isFile()) {
            return null;
        }
        if (file.length() <= 0 || file.length() > OfflinePromptSessionCodec.MAX_ENCODED_BYTES) {
            sessionFile.delete();
            throw invalidSession();
        }
        byte[] encoded;
        try (FileInputStream input = sessionFile.openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[1024];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > OfflinePromptSessionCodec.MAX_ENCODED_BYTES) {
                    sessionFile.delete();
                    throw invalidSession();
                }
                output.write(buffer, 0, read);
            }
            encoded = output.toByteArray();
        }
        try {
            return OfflinePromptSessionCodec.decode(encoded, System.currentTimeMillis());
        } catch (OfflinePromptSessionCodec.SessionException e) {
            sessionFile.delete();
            throw new SessionException(e.getMessage(), e);
        }
    }

    public void clear() {
        sessionFile.delete();
    }

    private static SessionException invalidSession() {
        return new SessionException(
                "The saved offline prompt session is invalid; copy a new prompt");
    }

    public static final class SessionException extends Exception {
        public SessionException(String message) {
            super(message);
        }

        public SessionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
