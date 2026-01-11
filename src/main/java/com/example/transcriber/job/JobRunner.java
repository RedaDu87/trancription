package com.example.transcriber.job;

import com.example.transcriber.media.FfmpegService;
import com.example.transcriber.openai.OpenAiService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Service
public class JobRunner {

    private final Path workdir;
    private final int concurrency;

    private final JobStore store;
    private final FfmpegService ffmpeg;
    private final OpenAiService openai;

    private final ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    public JobRunner(
            JobStore store,
            FfmpegService ffmpeg,
            OpenAiService openai,
            @Value("${app.workdir}") String workdir,
            @Value("${app.concurrency}") int concurrency
    ) throws Exception {
        this.store = store;
        this.ffmpeg = ffmpeg;
        this.openai = openai;
        this.workdir = Path.of(workdir);
        this.concurrency = Math.max(1, concurrency);
        Files.createDirectories(this.workdir);

        this.executor = Executors.newFixedThreadPool(this.concurrency);
    }

    public void start(String jobId, Path inputFile, String languageIso639OrNull) {
        JobInfo job = store.get(jobId).orElseThrow();

        // Run async (mais contrôlé par notre executor)
        CompletableFuture.runAsync(() -> runJob(job, inputFile, languageIso639OrNull), executor);
    }

    private void runJob(JobInfo job, Path inputFile, String languageIso639OrNull) {
        Path jobDir = workdir.resolve(job.id);
        Path chunksDir = jobDir.resolve("chunks");

        try {
            Files.createDirectories(jobDir);

            job.status = JobStatus.SPLITTING;
            List<Path> chunks = ffmpeg.convertAndSplitToWavChunks(inputFile, chunksDir);

            job.totalChunks = chunks.size();
            job.doneChunks.set(0);

            job.status = JobStatus.TRANSCRIBING;

            // On transcrit en parallèle mais on re-merge dans l’ordre.
            List<CompletableFuture<String>> futures = new ArrayList<>(chunks.size());

            // Pour continuité: on passera un mini “previous segment”,
            // mais comme c'est parallèle, on le fait en mode "semi-parallèle":
            // -> on transcrit en ordre, tout en utilisant un pool pour l’IO.
            // (ça garde la continuité et reste rapide)
            StringBuilder full = new StringBuilder();
            String prevTail = "";

            for (Path chunk : chunks) {
                final String prompt = prevTail.isBlank()
                        ? null
                        : ("Previous segment (context):\n" + prevTail);

                CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
                    try {
                        return openai.transcribe(chunk, prompt, languageIso639OrNull);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor);

                String text = f.join(); // en ordre -> continuité meilleure
                full.append(text).append("\n\n");

                job.doneChunks.incrementAndGet();

                prevTail = tail(text, 600);
            }

            job.result = new JobResult(
                    full.toString().trim()
            );

            job.status = JobStatus.DONE;
;

            job.status = JobStatus.DONE;

        } catch (Exception e) {
            job.status = JobStatus.FAILED;
            job.error = e.getMessage();
        } finally {
            // Optionnel: supprimer les chunks pour économiser disque
            // (tu peux commenter si tu veux garder les fichiers)
            try { deleteRecursive(jobDir); } catch (Exception ignored) {}
        }
    }

    private static String tail(String s, int maxChars) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= maxChars) return s;
        return s.substring(s.length() - maxChars);
    }

    private static void deleteRecursive(Path p) throws Exception {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            });
        }
    }
}
