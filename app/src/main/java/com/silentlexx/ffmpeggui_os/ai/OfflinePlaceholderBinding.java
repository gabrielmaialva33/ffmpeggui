package com.silentlexx.ffmpeggui_os.ai;

import java.security.SecureRandom;

public final class OfflinePlaceholderBinding {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 16;

    public static String newSessionToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder(TOKEN_BYTES * 2);
        for (byte value : bytes) {
            token.append(Character.forDigit((value >>> 4) & 0xf, 16));
            token.append(Character.forDigit(value & 0xf, 16));
        }
        return token.toString();
    }

    public static String inputPlaceholder(String token) {
        return "{{INPUT_1_SID_" + requireToken(token) + "}}";
    }

    public static String outputPlaceholder(String token) {
        return "{{OUTPUT_1_SID_" + requireToken(token) + "}}";
    }

    public static boolean isValidToken(String token) {
        return token != null && token.matches("[a-f0-9]{32}");
    }

    private static String requireToken(String token) {
        if (!isValidToken(token)) {
            throw new IllegalArgumentException("Invalid offline prompt session token");
        }
        return token;
    }

    private OfflinePlaceholderBinding() {
        throw new AssertionError("No instances");
    }
}
