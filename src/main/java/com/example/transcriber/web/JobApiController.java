package com.example.transcriber.web;

import com.example.transcriber.job.JobInfo;
import com.example.transcriber.job.JobStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class JobApiController {

    private final JobStore store;

    public JobApiController(JobStore store) {
        this.store = store;
    }

    @GetMapping("/job/{id}")
    public JobInfo getJob(@PathVariable String id) {
        return store.get(id).orElseThrow();
    }
}

