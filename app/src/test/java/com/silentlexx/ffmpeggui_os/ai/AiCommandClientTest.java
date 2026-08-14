package com.silentlexx.ffmpeggui_os.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

public class AiCommandClientTest {

    @Test
    public void requestLeavesModelContentAsTextInsteadOfForcingJsonObject() {
        Map<String, Object> request = AiCommandClient.buildRequestFields(
                "gemini-3.6-flash", "system", "user");

        assertFalse(request.containsKey("response_format"));
        assertTrue(Boolean.FALSE.equals(request.get("stream")));
        List<?> messages = (List<?>) request.get("messages");
        assertTrue(messages.size() == 2);
        assertTrue("system".equals(((Map<?, ?>) messages.get(0)).get("content")));
        assertTrue("user".equals(((Map<?, ?>) messages.get(1)).get("content")));
    }

    @Test
    public void byokContentUsesTheSharedFlexibleParser() throws Exception {
        String command = "ffmpeg -i \"{{INPUT_1}}\" -c copy \"{{OUTPUT_1}}\"";
        assertEquals(command, AiCommandClient.parseModelContent(
                "FFMPEGGUI_COMMAND_V1_BEGIN\n" + command +
                        "\nFFMPEGGUI_COMMAND_V1_END"));
        assertEquals(command, AiCommandClient.parseModelContent(command));
        assertEquals(command, AiCommandClient.parseModelContent(
                "{\"command\":\"ffmpeg -i \\\"{{INPUT_1}}\\\" -c copy " +
                        "\\\"{{OUTPUT_1}}\\\"\"}"));
    }

    @Test
    public void byokContentDoesNotGuessMalformedJsonQuotes() {
        try {
            AiCommandClient.parseModelContent(
                    "{\"command\":\"ffmpeg -i \"{{INPUT_1}}\" -c copy " +
                            "\"{{OUTPUT_1}}\"\"}");
            fail("Expected malformed JSON rejection");
        } catch (AiCommandClient.AiException expected) {
            assertTrue(expected.getMessage().contains("required FFmpeg command format"));
        }
    }

    @Test
    public void modelsUrlNormalizesBaseAndChatCompletionEndpoints() {
        assertEquals("https://api.example/v1/models",
                AiCommandClient.modelsUrl("https://api.example/v1"));
        assertEquals("https://api.example/v1/models",
                AiCommandClient.modelsUrl("https://api.example/v1/chat/completions"));
        assertEquals("https://api.example/v1/models",
                AiCommandClient.modelsUrl("https://api.example/v1/models/"));
    }

    @Test
    public void modelListFiltersUnsafeOrDuplicateIds() {
        List<String> models = AiCommandClient.filterModelIds(Arrays.asList(
                "qwen2.5:7b", "qwen2.5:7b", "gpt-4o-mini", "bad model", "../../secret"));
        assertEquals(2, models.size());
        assertEquals("qwen2.5:7b", models.get(0));
        assertEquals("gpt-4o-mini", models.get(1));
    }

    @Test
    public void modelListFilterPreservesEndpointOrderAndRejectsEmptyList() {
        List<String> models = AiCommandClient.filterModelIds(
                Arrays.asList("llama-3.1", "mistral-small"));
        assertEquals(2, models.size());
        assertTrue(AiCommandClient.filterModelIds(Arrays.asList("bad model")).isEmpty());
    }
}
