package com.example.transcriber.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Service
public class FfmpegService {

    private final int chunkSeconds;

    public FfmpegService(@Value("${app.chunk.seconds}") int chunkSeconds) {
        this.chunkSeconds = chunkSeconds;
    }

    /**
     * Convertit en wav mono 16kHz et segmente en chunks de chunkSeconds.
     * Retourne la liste des fichiers chunk WAV triés.
     */
    public List<Path> convertAndSplitToWavChunks(Path input, Path outDir) throws Exception {
        Files.createDirectories(outDir);

        // pattern: chunk_000.wav, chunk_001.wav...
        Path pattern = outDir.resolve("chunk_%03d.wav");

        // -ac 1 mono, -ar 16000, segment_time chunkSeconds
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-hide_banner", "-loglevel", "error",
                "-i", input.toAbsolutePath().toString(),
                "-ac", "1",
                "-ar", "16000",
                "-f", "segment",
                "-segment_time", String.valueOf(chunkSeconds),
                pattern.toAbsolutePath().toString()
        );

        Process p = pb.inheritIO().start();
        int code = p.waitFor();
        if (code != 0) throw new IllegalStateException("ffmpeg failed with exit code " + code);

        try (var stream = Files.list(outDir)) {
            return stream
                    .filter(f -> f.getFileName().toString().startsWith("chunk_") && f.toString().endsWith(".wav"))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString()))
                    .toList();
        }
    }
}
