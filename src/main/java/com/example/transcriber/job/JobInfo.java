package com.example.transcriber.job;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class JobInfo {
    public final String id;
    public volatile JobStatus status = JobStatus.QUEUED;
    public volatile String error = null;

    public final Instant createdAt = Instant.now();

    public volatile int totalChunks = 0;
    public final AtomicInteger doneChunks = new AtomicInteger(0);

    public volatile JobResult result = null;

    public JobInfo(String id) {
        this.id = id;
    }

    public int progressPercent() {
        if (totalChunks <= 0) return 0;
        return Math.min(100, (int)Math.round((doneChunks.get() * 100.0) / totalChunks));
    }
}
