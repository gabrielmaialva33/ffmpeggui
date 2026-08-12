package com.silentlexx.ffmpeggui_os.ai;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureApiKeyStore {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "ffmpeggui_ai_api_key_v1";
    private static final String SECRET_FILE = "ai_api_key_v1.bin";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int FILE_VERSION = 1;
    private static final int MAX_SECRET_FILE_SIZE = 16 * 1024;

    private final AtomicFile secretFile;

    public SecureApiKeyStore(Context context) {
        File file = new File(context.getNoBackupFilesDir(), SECRET_FILE);
        secretFile = new AtomicFile(file);
    }

    public boolean hasKey() {
        return secretFile.getBaseFile().isFile() && secretFile.getBaseFile().length() > 0;
    }

    public void save(String apiKey) throws GeneralSecurityException, IOException {
        String value = apiKey == null ? "" : apiKey.trim();
        if (value.isEmpty()) {
            throw new GeneralSecurityException("API key is empty");
        }

        byte[] clearText = value.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            encrypted = cipher.doFinal(clearText);
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length == 0 || iv.length > 255) {
                throw new GeneralSecurityException("Invalid encryption IV");
            }

            FileOutputStream output = null;
            try {
                output = secretFile.startWrite();
                output.write(FILE_VERSION);
                output.write(iv.length);
                output.write(iv);
                output.write(encrypted);
                secretFile.finishWrite(output);
                output = null;
            } finally {
                if (output != null) {
                    secretFile.failWrite(output);
                }
            }
        } finally {
            Arrays.fill(clearText, (byte) 0);
            if (encrypted != null) {
                Arrays.fill(encrypted, (byte) 0);
            }
        }
    }

    public String read() throws GeneralSecurityException, IOException {
        byte[] payload = readSecretFile();
        byte[] clearText = null;
        try {
            if (payload.length < 3 || (payload[0] & 0xff) != FILE_VERSION) {
                throw new GeneralSecurityException("Unsupported encrypted key format");
            }
            int ivLength = payload[1] & 0xff;
            if (ivLength < 12 || 2 + ivLength >= payload.length) {
                throw new GeneralSecurityException("Invalid encrypted key data");
            }

            byte[] iv = Arrays.copyOfRange(payload, 2, 2 + ivLength);
            byte[] cipherText = Arrays.copyOfRange(payload, 2 + ivLength, payload.length);
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), new GCMParameterSpec(128, iv));
                clearText = cipher.doFinal(cipherText);
                String value = new String(clearText, StandardCharsets.UTF_8);
                if (value.trim().isEmpty()) {
                    throw new GeneralSecurityException("Stored API key is empty");
                }
                return value;
            } finally {
                Arrays.fill(iv, (byte) 0);
                Arrays.fill(cipherText, (byte) 0);
            }
        } finally {
            Arrays.fill(payload, (byte) 0);
            if (clearText != null) {
                Arrays.fill(clearText, (byte) 0);
            }
        }
    }

    public void clear() throws GeneralSecurityException, IOException {
        secretFile.delete();
        KeyStore keyStore = loadKeyStore();
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS);
        }
    }

    private byte[] readSecretFile() throws IOException {
        if (!hasKey()) {
            throw new IOException("No API key is stored");
        }
        if (secretFile.getBaseFile().length() > MAX_SECRET_FILE_SIZE) {
            throw new IOException("Encrypted API key file is invalid");
        }

        try (FileInputStream input = secretFile.openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SECRET_FILE_SIZE) {
                    throw new IOException("Encrypted API key file is invalid");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore();
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return getExistingKey(keyStore);
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private SecretKey getExistingKey() throws GeneralSecurityException, IOException {
        return getExistingKey(loadKeyStore());
    }

    private SecretKey getExistingKey(KeyStore keyStore) throws GeneralSecurityException {
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (!(entry instanceof KeyStore.SecretKeyEntry)) {
            throw new GeneralSecurityException("Secure API key is unavailable; save it again");
        }
        return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
    }

    private KeyStore loadKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }
}
