package com.aldercrest.atlas.results;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReadQueriesTest {
    @Test void pagingDefaultsLimitsAndDuplicateUnknownParametersAreExplicit() {
        assertThat(ReadQueries.page(Map.of())).isEqualTo(new ReadQueries.Page(0,50));
        assertThat(ReadQueries.page(Map.of("offset",new String[]{"1000000"},"limit",new String[]{"200"}))).isEqualTo(new ReadQueries.Page(1000000,200));
        for(Map<String,String[]> values:java.util.List.of(Map.of("offset",new String[]{"-1"}),Map.of("offset",new String[]{"1000001"}),
                Map.of("offset",new String[]{"0","1"}),Map.of("limit",new String[]{"0"}),Map.of("limit",new String[]{"201"}),
                Map.of("limit",new String[]{""}),Map.of("limit",new String[]{"1e2"}),Map.of("limit",new String[]{" 1"}),
                Map.of("limit",new String[]{"9999999999999999999999"}),Map.of("extra",new String[]{"private-marker"}))) {
            assertThatThrownBy(() -> ReadQueries.page(values)).isInstanceOf(IllegalArgumentException.class).hasMessage("INVALID_QUERY");
        }
    }
    @Test void utcWholeDaysInclusiveOffsetsNanosecondsAndOpenBoundsArePreserved() {
        var day=ReadQueries.trend(Map.of("code",new String[]{"29463-7"},"from",new String[]{"2026-01-01"},"to",new String[]{"2026-01-01"}));
        assertThat(day.from()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(day.to()).isEqualTo(Instant.parse("2026-01-01T23:59:59.999999999Z"));
        assertThat(day.contains(day.from())).isTrue();assertThat(day.contains(day.to())).isTrue();assertThat(day.contains(day.to().plusNanos(1))).isFalse();
        var precise=ReadQueries.trend(Map.of("code",new String[]{"8480-6"},"from",new String[]{"2026-01-01T05:30:00.123456789+05:30"}));
        assertThat(precise.from()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456789Z"));assertThat(precise.to()).isNull();
        assertThat(ReadQueries.trend(Map.of("code",new String[]{"39156-5"})).from()).isNull();
    }
    @Test void malformedOrAmbiguousTrendQueriesNeverReceiveInventedDefaults() {
        for(Map<String,String[]> values:java.util.List.of(Map.<String,String[]>of(),Map.of("code",new String[]{"unknown"}),Map.of("code",new String[]{"29463-7","29463-7"}),
                Map.of("code",new String[]{"29463-7"},"from",new String[]{"2026-01-01T00:00:00"}),
                Map.of("code",new String[]{"29463-7"},"from",new String[]{"2026-02-30"}),
                Map.of("code",new String[]{"29463-7"},"from",new String[]{"2026-01-02"},"to",new String[]{"2026-01-01"}))) {
            assertThatThrownBy(() -> ReadQueries.trend(values)).isInstanceOf(IllegalArgumentException.class).hasMessage("INVALID_QUERY");
        }
        assertThatThrownBy(() -> ReadQueries.none(Map.of("code",new String[]{"29463-7"}))).isInstanceOf(IllegalArgumentException.class);
    }
}
