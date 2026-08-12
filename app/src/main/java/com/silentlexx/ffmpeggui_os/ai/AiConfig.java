package com.silentlexx.ffmpeggui_os.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.MalformedURLException;
import java.net.URL;

public final class AiConfig {

    public static final String PREFS_NAME = "ffmpeggui-ai-settings";
    public static final String DEFAULT_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/openai";
    public static final String DEFAULT_MODEL = "gemini-3.6-flash";

    public static final String CUSTOM_MODEL_LABEL = "Custom model…";
    public static final String[] PRESET_MODELS = {
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview"
    };

    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_MODEL = "model";
    private static final String KEY_CLEARTEXT_ACK_ENDPOINT = "cleartext_ack_endpoint";

    private final SharedPreferences preferences;

    public AiConfig(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getEndpoint() {
        return preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
    }

    public String getModel() {
        return preferences.getString(KEY_MODEL, DEFAULT_MODEL);
    }

    public void save(String endpoint, String model, boolean cleartextAcknowledged) {
        String normalizedEndpoint = endpoint.trim();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_ENDPOINT, endpoint.trim())
                .putString(KEY_MODEL, model.trim());
        if (isCleartextEndpoint(normalizedEndpoint) && cleartextAcknowledged) {
            editor.putString(KEY_CLEARTEXT_ACK_ENDPOINT, normalizedEndpoint);
        } else {
            editor.remove(KEY_CLEARTEXT_ACK_ENDPOINT);
        }
        editor.apply();
    }

    public boolean isEndpointApproved(String endpoint) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        return !isCleartextEndpoint(normalized) || normalized.equals(
                preferences.getString(KEY_CLEARTEXT_ACK_ENDPOINT, ""));
    }

    public static boolean isCleartextEndpoint(String endpoint) {
        try {
            return "http".equalsIgnoreCase(new URL(endpoint == null ? "" : endpoint.trim())
                    .getProtocol());
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static String validateEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            return "Endpoint is required";
        }
        try {
            URL url = new URL(value);
            String protocol = url.getProtocol();
            if (!("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))) {
                return "Endpoint must use http or https";
            }
            if (url.getHost() == null || url.getHost().isEmpty()) {
                return "Endpoint must include a host";
            }
            if (url.getUserInfo() != null || url.getRef() != null || url.getQuery() != null) {
                return "Endpoint cannot contain credentials, a query, or a fragment";
            }
        } catch (MalformedURLException e) {
            return "Endpoint is not a valid URL";
        }
        return null;
    }

    public static String validateModel(String model) {
        String value = model == null ? "" : model.trim();
        if (value.isEmpty()) {
            return "Model is required";
        }
        if (value.length() > 160 || !value.matches("[A-Za-z0-9._:/-]+")) {
            return "Model contains unsupported characters";
        }
        return null;
    }

    public static int findPresetPosition(String model) {
        for (int i = 0; i < PRESET_MODELS.length; i++) {
            if (PRESET_MODELS[i].equals(model)) {
                return i;
            }
        }
        return PRESET_MODELS.length;
    }

}
