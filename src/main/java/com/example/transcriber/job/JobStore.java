package com.example.transcriber.job;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobStore {
    private final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();

    public JobInfo create(String id) {
        JobInfo j = new JobInfo(id);
        jobs.put(id, j);
        return j;
    }

    public Optional<JobInfo> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }
}
