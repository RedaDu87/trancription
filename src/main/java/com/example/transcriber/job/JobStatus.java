package com.example.transcriber.job;

public enum JobStatus {
    QUEUED,
    SPLITTING,
    TRANSCRIBING,
    TRANSLATING,
    DONE,
    FAILED
}
