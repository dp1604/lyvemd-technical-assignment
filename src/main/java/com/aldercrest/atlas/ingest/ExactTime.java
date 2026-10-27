package com.aldercrest.atlas.ingest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Reject partial/local time rather than guessing; preserve offset-time input to nanoseconds. */
public record ExactTime(Instant instant) {
    public static ExactTime parse(String input) {
        if (input == null || !input.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(Z|[+-][0-9]{2}:[0-9]{2})")) {
            throw new IllegalArgumentException("PRECISE_OFFSET_DATETIME_REQUIRED");
        }
        return new ExactTime(OffsetDateTime.parse(input).toInstant());
    }
    public OffsetDateTime databaseTime() { return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC); }
    public int submicroNanos() { return instant.getNano() % 1000; }
    public static Instant restore(OffsetDateTime databaseTime,int remainder) { return databaseTime.toInstant().plusNanos(remainder); }
}
