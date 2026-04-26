package com.st4r4x.sync;

import java.time.Instant;

public class SyncResult {

    private final Instant startedAt;
    private final Instant completedAt;
    private final int rawRecords;
    private final int upsertedRestaurants;
    private final boolean success;
    private final String errorMessage;

    private SyncResult(Builder builder) {
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.rawRecords = builder.rawRecords;
        this.upsertedRestaurants = builder.upsertedRestaurants;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getRawRecords() { return rawRecords; }
    public int getUpsertedRestaurants() { return upsertedRestaurants; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Instant startedAt;
        private Instant completedAt;
        private int rawRecords;
        private int upsertedRestaurants;
        private boolean success;
        private String errorMessage;

        public Builder startedAt(Instant v) { this.startedAt = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }
        public Builder rawRecords(int v) { this.rawRecords = v; return this; }
        public Builder upsertedRestaurants(int v) { this.upsertedRestaurants = v; return this; }
        public Builder success(boolean v) { this.success = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public SyncResult build() { return new SyncResult(this); }
    }

    @Override
    public String toString() {
        return "SyncResult{success=" + success +
                ", rawRecords=" + rawRecords +
                ", upserted=" + upsertedRestaurants +
                ", duration=" + (startedAt != null && completedAt != null
                        ? (completedAt.toEpochMilli() - startedAt.toEpochMilli()) + "ms" : "?") +
                '}';
    }
}
