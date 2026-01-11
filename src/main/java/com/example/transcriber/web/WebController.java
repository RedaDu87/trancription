package com.example.transcriber.web;

import com.example.transcriber.job.JobInfo;
import com.example.transcriber.job.JobStatus;
import com.example.transcriber.job.JobStore;
import com.example.transcriber.job.JobRunner;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Controller
public class WebController {

    private final JobStore store;
    private final JobRunner runner;

    public WebController(JobStore store, JobRunner runner) {
        this.store = store;
        this.runner = runner;
    }

    @GetMapping("/")
    public String app() {
        return "app";
    }

    @PostMapping("/upload")
    @ResponseBody
    public String upload(@RequestParam("file") MultipartFile file) throws Exception {

        String jobId = UUID.randomUUID().toString();
        store.create(jobId);

        Path temp = Files.createTempDirectory("job-" + jobId);
        Path input = temp.resolve(file.getOriginalFilename());
        file.transferTo(input);

        runner.start(jobId, input, null);

        return jobId;
    }
}
