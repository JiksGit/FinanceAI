package com.finance.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.dashboard.config.OpenAiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final OpenAiConfig openAiConfig;
    private final RestClient restClient = RestClient.create();

    public boolean isEnabled() {
        return openAiConfig.isEnabled();
    }

    /**
     * @return GPT 응답 텍스트. 키 미설정이거나 호출 실패 시 null.
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            return null;
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", openAiConfig.model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.4
            );

            JsonNode response = restClient.post()
                    .uri(openAiConfig.baseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + openAiConfig.apiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return null;
            }

            return response.path("choices").path(0).path("message").path("content").asText(null);
        } catch (RestClientException e) {
            log.error("OpenAI API 호출 실패", e);
            return null;
        }
    }
}
