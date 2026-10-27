package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.aldercrest.atlas.results.ClinicalViewRepository;
import com.aldercrest.atlas.security.TestActor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** deterministic adopted Repeatable Read revocation boundary, not workflow evidence. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(ReadSnapshotIT.FixedClock.class)
class ReadSnapshotIT {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        Clock assessmentClock() {return Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"),ZoneOffset.UTC);}
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("ATLAS_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {
            throw new IllegalStateException("Designated synthetic test DB required.");
        }
        SchemaMigration.run(System.getenv());
    }
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ClinicalViewRepository views;
    @LocalServerPort int port;
    private JdbcTemplate owner;
    private String patient,proxy,clinician,observation;

    @BeforeEach void fixture() {
        String url=System.getenv("ATLAS_DB_URL");
        owner=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"),System.getenv("ATLAS_MIGRATION_PASSWORD")));
        patient=id();proxy=id();clinician=id();observation=id();String encounter=id();
        owner.update("INSERT INTO actor(id,role) VALUES (?,'PATIENT'),(?,'PROXY'),(?,'CLINICIAN')",patient,proxy,clinician);
        owner.update("INSERT INTO patient(id,sex,birth_date) VALUES (?,'female','2010-01-01')",patient);
        owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id) VALUES (?,?,'AMB',?)",encounter,patient,clinician);
        owner.update("INSERT INTO proxy_link(patient_id,proxy_id) VALUES (?,?)",patient,proxy);
        owner.update("""
                INSERT INTO observation(id,patient_id,encounter_id,code,unit_code,value_num,effective,status,birth_date_context,sex_context)
                VALUES (?,?,?,'29463-7','kg',70,'2026-01-01T00:00:00Z','final','2010-01-01','female')
                """,observation,patient,encounter);
        // Explicit test-only released state isolates snapshot behavior. This is not proof that
        // the actual clinician classification/release command was invoked by the dataset loader.
        owner.update("""
                INSERT INTO observation_review(observation_id,criticality,reviewed_by,reviewed_at,released_by,released_at)
                VALUES (?,'NONCRITICAL',?,clock_timestamp(),?,clock_timestamp())
                """,observation,clinician,clinician);
    }

    @Test void revocationCommittedBeforeTheRequestSnapshotDeniesAndPersistsTheDenial() throws Exception {
        revoke();
        var response=detail();
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain(observation,"70");
        assertThat(audits("DENIED")).isEqualTo(1);
        assertThat(audits("ALLOWED")).isZero();
    }

    @Test void revocationAfterTheRosterSnapshotDoesNotRetractInflightContentAndTheFollowingRequestDenies() throws Exception {
        CountDownLatch rosterSnapshotEstablished=new CountDownLatch(1);
        CountDownLatch revocationCommitted=new CountDownLatch(1);
        AtomicBoolean pauseFirst=new AtomicBoolean(true);
        doAnswer(invocation -> {
            String requestedPatient=invocation.getArgument(0);
            TestActor actor=invocation.getArgument(1);
            if(patient.equals(requestedPatient) && proxy.equals(actor.id()) && pauseFirst.compareAndSet(true,false)) {
                // AuditedReadAction's successful roster SELECT has already established the RR
                // snapshot before invoking access(). Its returned actor is roster-verified.
                assertThat(actor.rosterVerified()).isTrue();
                rosterSnapshotEstablished.countDown();
                if(!revocationCommitted.await(10,TimeUnit.SECONDS)) {throw new IllegalStateException("Synthetic gate timed out.");}
            }
            return invocation.callRealMethod();
        }).when(views).access(anyString(),any(TestActor.class));

        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(this::detail);
            try {
                assertThat(rosterSnapshotEstablished.await(10,TimeUnit.SECONDS)).as("Read reached access after its roster snapshot").isTrue();
                revoke(); // Separate owner's auto-commit transaction completes before gate release.
            } finally {revocationCommitted.countDown();}
            var inFlight=pending.get(20,TimeUnit.SECONDS);
            assertThat(inFlight.statusCode()).isEqualTo(200);
            var body=IngestionJson.mapper().readTree(inFlight.body());
            assertThat(body.path("id").asText()).isEqualTo(observation);
            assertThat(body.path("value").decimalValue()).isEqualByComparingTo("70");
            assertThat(audits("ALLOWED")).isEqualTo(1);
        }
        var following=detail();
        assertThat(following.statusCode()).isEqualTo(404);
        assertThat(following.body()).doesNotContain(observation);
        assertThat(audits("DENIED")).isEqualTo(1);
    }

    private void revoke() {
        assertThat(owner.update("UPDATE proxy_link SET active=false,revoked_at=clock_timestamp() WHERE patient_id=? AND proxy_id=?",patient,proxy)).isEqualTo(1);
    }
    private HttpResponse<String> detail() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/patients/"+patient+"/results/"+observation))
                .timeout(Duration.ofSeconds(20)).header("X-Test-Actor-Id",proxy).header("X-Test-Actor-Role","PROXY").GET().build();
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return client.send(request,HttpResponse.BodyHandlers.ofString());
        }
    }
    private long audits(String outcome) {
        return jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND actor_id=? AND action='RESULT_DETAIL' AND outcome=?",Long.class,
                patient,proxy,outcome);
    }
    private static String id() {return UUID.randomUUID().toString();}
}
