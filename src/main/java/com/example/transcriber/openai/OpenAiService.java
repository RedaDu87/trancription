package com.example.transcriber.openai;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Service
public class OpenAiService {

    private final WebClient openai;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String transcriptionModel;
    private final String translationModel;

    public OpenAiService(
            WebClient openAiWebClient,
            @Value("${openai.transcription-model}") String transcriptionModel,
            @Value("${openai.translation-model}") String translationModel
    ) {
        this.openai = openAiWebClient;
        this.transcriptionModel = transcriptionModel;
        this.translationModel = translationModel;
    }

    /** /v1/audio/transcriptions :contentReference[oaicite:3]{index=3} */
    public String transcribe(Path wavChunk, String promptOrNull, String languageIso639OrNull) throws Exception {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("file", new FileSystemResource(wavChunk.toFile()));
        mb.part("model", transcriptionModel);
        mb.part("response_format", "json");

        if (promptOrNull != null && !promptOrNull.isBlank()) mb.part("prompt", promptOrNull);
        if (languageIso639OrNull != null && !languageIso639OrNull.isBlank()) mb.part("language", languageIso639OrNull);

        String json = openai.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(mb.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = mapper.readTree(json);
        String text = root.path("text").asText(null);
        if (text == null) throw new IllegalStateException("Missing 'text' in transcription response");
        return text;
    }

    /** /v1/responses :contentReference[oaicite:4]{index=4} */
    public String translateToJson(String transcript) throws Exception {
        String instructions =
                "Tu es un traducteur.\n" +
                        "Retourne STRICTEMENT un JSON valide (sans markdown) avec exactement ces clés: ar, fr, es, de.\n" +
                        "Valeurs: traduction du texte dans chaque langue.\n";

        var req = mapper.createObjectNode();
        req.put("model", translationModel);
        req.put("instructions", instructions);
        req.put("input", transcript);

        // Réponses API: format texte (on force “text”) :contentReference[oaicite:5]{index=5}
        var textNode = mapper.createObjectNode();
        textNode.set("format", mapper.createObjectNode().put("type", "text"));
        req.set("text", textNode);

        String resp = openai.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = mapper.readTree(resp);
        String outText = root.path("output").path(0).path("content").path(0).path("text").asText(null);
        if (outText == null) throw new IllegalStateException("Missing output text in responses result");

        // Validate JSON
        mapper.readTree(outText);
        return outText;
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
