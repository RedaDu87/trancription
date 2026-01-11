package com.example.transcriber.openai;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OpenAiClientConfig {


    @Bean
    public WebClient openAiWebClient(@Value("${openai.api-key}") String apiKey) {

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
