package com.example.transcriber.openai;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class OpenAiClientConfig {

    @Value("${openai.api-key}")
    private String apiKeyOrFile;

    private String apiKey;

    @PostConstruct
    public void loadKey() throws Exception {
        if (apiKeyOrFile.startsWith("/")) {
            // Docker secret
            apiKey = Files.readString(Path.of(apiKeyOrFile)).trim();
        } else {
            // Env var ou valeur directe
            apiKey = apiKeyOrFile;
        }
        System.out.println("OpenAI key loaded: " + apiKey.substring(0, 6) + "...");
    }

    @Bean
    public WebClient openAiWebClient() {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60_000)
                .responseTimeout(Duration.ofMinutes(10))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(600))
                                .addHandlerLast(new WriteTimeoutHandler(600))
                );

        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
}
