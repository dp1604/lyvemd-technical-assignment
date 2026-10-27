package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.security.ActorHeaders;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class InputBoundaryTest {
    @Test
    void testHeadersAreSingleBoundedExactAndExplicitlyUnverifiedWhenMalformed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(ActorHeaders.parse(request).valid()).isFalse();
        request.addHeader("X-Test-Actor-Id","clinician-1");
        request.addHeader("X-Test-Actor-Role","CLINICIAN");
        assertThat(ActorHeaders.parse(request).valid()).isTrue();
        request.addHeader("X-Test-Actor-Id","clinician-2");
        assertThat(ActorHeaders.parse(request).valid()).isFalse();
        request.removeHeader("X-Test-Actor-Id");
        request.addHeader("X-Test-Actor-Id","x".repeat(65));
        assertThat(ActorHeaders.parse(request).valid()).isFalse();
        request.removeHeader("X-Test-Actor-Id"); request.addHeader("X-Test-Actor-Id","safe");
        request.removeHeader("X-Test-Actor-Role"); request.addHeader("X-Test-Actor-Role","clinician");
        assertThat(ActorHeaders.parse(request).valid()).isFalse();
    }

    @Test
    void idempotencyKeysAreRequiredSingleAndBounded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(ActorHeaders.idempotencyKey(request)).isNull();
        request.addHeader("X-Idempotency-Key","A.1-_valid");
        assertThat(ActorHeaders.idempotencyKey(request)).isEqualTo("A.1-_valid");
        request.addHeader("X-Idempotency-Key","duplicate");
        assertThat(ActorHeaders.idempotencyKey(request)).isNull();
    }

    @Test
    void exactTimePreservesNanosecondsAndUtcAgeDateAcrossDatabasePrecision() {
        ExactTime input = ExactTime.parse("2013-12-31T23:59:59.999999999Z");
        assertThat(input.databaseTime().toInstant()).isEqualTo(Instant.parse("2013-12-31T23:59:59.999999Z"));
        assertThat(input.submicroNanos()).isEqualTo(999);
        assertThat(ExactTime.restore(input.databaseTime(),input.submicroNanos())).isEqualTo(input.instant());
        assertThat(ExactTime.parse("2014-01-01T00:59:59.999999999+01:00").instant()).isEqualTo(input.instant());
        for (String unsupported : List.of("2014","2014-01-01","2014-01-01T00:00:00","2014-01-01T00:00:00.1234567890Z")) {
            assertThatThrownBy(() -> ExactTime.parse(unsupported)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
