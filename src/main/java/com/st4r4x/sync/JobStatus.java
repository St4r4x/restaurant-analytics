package com.st4r4x.sync;

import java.time.Instant;
import java.util.Objects;

public class JobStatus {

    private final Instant lastRunAt;
    private final long durationMs;
    private final boolean success;
    private final String errorMessage;

    private JobStatus(Instant lastRunAt, long durationMs, boolean success, String errorMessage) {
        this.lastRunAt = Objects.requireNonNull(lastRunAt, "lastRunAt");
        this.durationMs = durationMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static JobStatus success(Instant lastRunAt, long durationMs) {
        return new JobStatus(lastRunAt, durationMs, true, null);
    }

    public static JobStatus failure(Instant lastRunAt, long durationMs, String errorMessage) {
        return new JobStatus(lastRunAt, durationMs, false, errorMessage);
    }

    public Instant getLastRunAt()    { return lastRunAt; }
    public long getDurationMs()      { return durationMs; }
    public boolean isSuccess()       { return success; }
    public String getErrorMessage()  { return errorMessage; }

    @Override
    public String toString() {
        return "JobStatus{success=" + success +
               ", durationMs=" + durationMs +
               ", lastRunAt=" + lastRunAt +
               ", errorMessage=" + errorMessage + '}';
    }
}
