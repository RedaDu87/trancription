package com.example.transcriber.api;

import com.example.transcriber.job.JobInfo;
import com.example.transcriber.job.JobRunner;
import com.example.transcriber.job.JobStatus;
import com.example.transcriber.job.JobStore;
import com.example.transcriber.openai.OpenAiService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobStore store;
    private final JobRunner runner;

    public JobController(JobStore store, JobRunner runner) {
        this.store = store;
        this.runner = runner;
    }

    /** Upload -> jobId, traitement async */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createJob(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String languageIso639
    ) {
        if (file.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "File is empty");

        try {
            String jobId = UUID.randomUUID().toString();
            JobInfo job = store.create(jobId);

            // On écrit le fichier sur disque (évite de tout garder en RAM)
            Path tempDir = Files.createTempDirectory("audio-job-" + jobId + "-");
            String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "input.audio";
            Path input = tempDir.resolve(originalName);
            file.transferTo(input.toFile());

            runner.start(jobId, input, languageIso639);

            return Map.of(
                    "jobId", jobId,
                    "status", job.status.name()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to create job: " + e.getMessage(), e);
        }
    }

    /** Status / progress */
    @GetMapping("/{id}")
    public Map<String, Object> getJob(@PathVariable String id) {
        JobInfo job = store.get(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found"));

        return Map.of(
                "jobId", job.id,
                "status", job.status.name(),
                "progressPercent", job.progressPercent(),
                "doneChunks", job.doneChunks.get(),
                "totalChunks", job.totalChunks,
                "error", job.error
        );
    }

    /** Result JSON (quand DONE) */
    @GetMapping("/{id}/result")
    public Object getResult(@PathVariable String id) {
        JobInfo job = store.get(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found"));
        if (job.status == JobStatus.FAILED) throw new ResponseStatusException(BAD_REQUEST, "Job failed: " + job.error);
        if (job.status != JobStatus.DONE) throw new ResponseStatusException(ACCEPTED, "Job not finished yet");
        return job.result;
    }

    /** ZIP : ar.txt, fr.txt, es.txt, de.txt + original.txt */
    @GetMapping("/{id}/result.zip")
    public ResponseEntity<byte[]> getZip(@PathVariable String id) throws Exception {
        JobInfo job = store.get(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found"));
        if (job.status == JobStatus.FAILED) throw new ResponseStatusException(BAD_REQUEST, "Job failed: " + job.error);
        if (job.status != JobStatus.DONE) throw new ResponseStatusException(ACCEPTED, "Job not finished yet");

        byte[] zip = toZip(job);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transcript_" + id + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    private byte[] toZip(JobInfo job) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            add(zos, "original.txt", job.result.original());
        }
        return baos.toByteArray();
    }

    private void add(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(OpenAiService.utf8(content == null ? "" : content));
        zos.closeEntry();
    }


}
