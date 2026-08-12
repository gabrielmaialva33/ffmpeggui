package com.silentlexx.ffmpeggui_os.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiCommandClient {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 90_000;
    private static final int MAX_RESPONSE_CHARS = 1_048_576;

    public String generate(String endpoint, String apiKey, String model,
                           String systemPrompt, String userPrompt) throws AiException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(chatCompletionsUrl(endpoint));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");

            byte[] request = buildRequest(model, systemPrompt, userPrompt)
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(request.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : readResponse(stream);
            if (status < 200 || status >= 300) {
                String detail = serverError(response);
                if (apiKey != null && apiKey.length() >= 6) {
                    detail = detail.replace(apiKey, "[redacted]");
                }
                throw new AiException("Server returned HTTP " + status +
                        (detail.isEmpty() ? "" : ": " + detail));
            }
            return parseCommand(response);
        } catch (SocketTimeoutException e) {
            throw new AiException("The AI request timed out", e);
        } catch (JSONException e) {
            throw new AiException("The AI server returned an invalid JSON response", e);
        } catch (IOException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new AiException("The AI request was cancelled", e);
            }
            throw new AiException("Network error: " + safeMessage(e.getMessage()), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String chatCompletionsUrl(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        return value + "/chat/completions";
    }

    private static String buildRequest(String model, String systemPrompt, String userPrompt)
            throws JSONException {
        return new JSONObject(buildRequestFields(model, systemPrompt, userPrompt)).toString();
    }

    static Map<String, Object> buildRequestFields(String model, String systemPrompt,
                                                  String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", false);
        return request;
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String parseCommand(String response) throws JSONException, AiException {
        JSONObject root = new JSONObject(response);
        if (root.has("error")) {
            throw new AiException("AI server error: " + serverError(response));
        }
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new AiException("The AI response did not contain a completion");
        }
        JSONObject choice = choices.optJSONObject(0);
        JSONObject message = choice == null ? null : choice.optJSONObject("message");
        if (message == null) {
            throw new AiException("The AI response did not contain a message");
        }

        Object rawContent = message.opt("content");
        String content = contentText(rawContent).trim();
        if (content.isEmpty()) {
            throw new AiException("The AI response was empty");
        }

        return parseModelContent(content);
    }

    static String parseModelContent(String content) throws AiException {
        try {
            return AiPlaceholderCommand.parseModelResult(content);
        } catch (AiPlaceholderCommand.ParseException e) {
            throw new AiException("The model did not return the required FFmpeg command format", e);
        }
    }

    private static String contentText(Object content) throws JSONException {
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof JSONArray) {
            StringBuilder text = new StringBuilder();
            JSONArray parts = (JSONArray) content;
            for (int i = 0; i < parts.length(); i++) {
                Object part = parts.get(i);
                if (part instanceof JSONObject) {
                    Object value = ((JSONObject) part).opt("text");
                    if (value instanceof String) {
                        text.append(value);
                    }
                }
            }
            return text.toString();
        }
        return "";
    }

    private static String readResponse(InputStream input) throws IOException, AiException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
                if (result.length() > MAX_RESPONSE_CHARS) {
                    throw new AiException("The AI response was too large");
                }
            }
        }
        return result.toString();
    }

    private static String serverError(String response) {
        try {
            JSONObject root = new JSONObject(response);
            Object error = root.opt("error");
            if (error instanceof JSONObject) {
                return safeMessage(((JSONObject) error).optString("message", ""));
            }
            if (error instanceof String) {
                return safeMessage((String) error);
            }
            return safeMessage(root.optString("message", ""));
        } catch (JSONException e) {
            return safeMessage(response);
        }
    }

    private static String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < message.length() && safe.length() < 400; i++) {
            char c = message.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                safe.append(' ');
            } else if (c >= 0x20 && c != 0x7f) {
                safe.append(c);
            }
        }
        return safe.toString().trim();
    }

    public static final class AiException extends Exception {
        public AiException(String message) {
            super(message);
        }

        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
