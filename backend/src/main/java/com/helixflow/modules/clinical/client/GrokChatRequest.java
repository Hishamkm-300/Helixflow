package com.helixflow.modules.clinical.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrokChatRequest {

    private String model;

    @Builder.Default
    private double temperature = 0.1;

    @Builder.Default
    @JsonProperty("max_tokens")
    private int maxTokens = 1200;

    @Builder.Default
    @JsonProperty("response_format")
    private Map<String, String> responseFormat = Map.of("type", "json_object");

    private List<ChatMessage> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
