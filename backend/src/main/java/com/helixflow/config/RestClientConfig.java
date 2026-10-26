package com.helixflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${helixflow.llm.grok.base-url:https://api.x.ai/v1}")
    private String grokBaseUrl;

    @Value("${helixflow.llm.grok.api-key:}")
    private String grokApiKey;

    @Value("${helixflow.llm.grok.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${helixflow.llm.grok.read-timeout-ms:6000}")
    private int readTimeoutMs;

    @Bean(name = "xaiRestClient")
    public RestClient xaiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(grokBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json");

        if (grokApiKey != null && !grokApiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + grokApiKey);
        }

        return builder.build();
    }
}
