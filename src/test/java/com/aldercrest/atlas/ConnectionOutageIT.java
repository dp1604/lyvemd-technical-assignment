package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.ingest.FhirDefinitions;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual HTTP/PG fixture; serving-connection-provider injection, not a stopped database cluster. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ConnectionOutageIT {
    private static final String PRIVATE_MARKER="PRIVATE_SYNTHETIC_CONNECTION_PROVIDER_FAILURE";
    @MockitoSpyBean DataSource servingDataSource;
    @LocalServerPort int port;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("ATLAS_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {
            throw new IllegalStateException("Designated synthetic test DB required.");
        }
        SchemaMigration.run(System.getenv());
    }

    @Test @Timeout(value=120,unit=TimeUnit.SECONDS)
    void servingConnectionAcquisitionFailureWithholdsAllFiveRoutesAndRecoversWithoutPoisoningAKey(CapturedOutput output) throws Exception {
        String url=System.getenv("ATLAS_DB_URL");
        JdbcTemplate owner=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"),System.getenv("ATLAS_MIGRATION_PASSWORD")));
        owner.setQueryTimeout(3);
        String patient=id(),clinician=id(),staff=id(),encounter=id(),observation=id();
        owner.update("INSERT INTO actor(id,role) VALUES (?,'PATIENT'),(?,'CLINICIAN'),(?,'STAFF')",patient,clinician,staff);
        owner.update("INSERT INTO patient(id,sex,birth_date) VALUES (?,'female','1980-01-01')",patient);
        owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id) VALUES (?,?,'AMB',?)",encounter,patient,clinician);
        // Explicit readable test state, not proof of the actual clinical release workflow.
        owner.update("""
                INSERT INTO observation(id,patient_id,encounter_id,code,unit_code,value_num,effective,status,birth_date_context,sex_context)
                VALUES (?,?,?,'2951-2','mmol/L',140.125,'2026-01-01T00:00:00Z','final','1980-01-01','female')
                """,observation,patient,encounter);
        owner.update("""
                INSERT INTO observation_review(observation_id,criticality,reviewed_by,reviewed_at,released_by,released_at)
                VALUES (?,'NONCRITICAL',?,clock_timestamp(),?,clock_timestamp())
                """,observation,clinician,clinician);
        String key="connection-outage-"+id(),body=patientBundle();
        Map<String,Object> before=inventory(owner);
        AtomicBoolean unavailable=new AtomicBoolean(false);AtomicInteger rejectedAcquisitions=new AtomicInteger();
        doAnswer(call -> {
            if(unavailable.get()) {
                rejectedAcquisitions.incrementAndGet();
                throw new SQLTransientConnectionException(PRIVATE_MARKER,"08001");
            }
            return call.callRealMethod();
        }).when(servingDataSource).getConnection();
        try(HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            try {
                unavailable.set(true);
                int prior=rejectedAcquisitions.get();
                assertSafeUnavailable(post(client,body,key,clinician),patient,clinician,staff,observation,key);
                assertThat(rejectedAcquisitions.get()).as("POST reached the actual serving connection provider").isGreaterThan(prior);
                for(String suffix:List.of("/results","/results/"+observation,"/trend?code=2951-2","/access-log")) {
                    prior=rejectedAcquisitions.get();
                    boolean log=suffix.equals("/access-log");
                    assertSafeUnavailable(get(client,patient,suffix,log?staff:patient,log?"STAFF":"PATIENT"),patient,clinician,staff,observation,key);
                    assertThat(rejectedAcquisitions.get()).as("GET reached the actual serving connection provider").isGreaterThan(prior);
                }
                assertThat(rejectedAcquisitions.get()).isGreaterThanOrEqualTo(5);
                verify(servingDataSource,atLeast(5)).getConnection();
                // The independent owner connection still works. None of these failed actions
                // can commit its clinical write, receipt or even its own audit without acquisition.
                assertThat(inventory(owner)).isEqualTo(before);
                assertThat(receipts(owner,clinician,key)).isZero();
                assertThat(audits(owner,patient)).isZero();
            } finally {
                unavailable.set(false);
                doCallRealMethod().when(servingDataSource).getConnection();
            }

            int rejectedBeforeRecovery=rejectedAcquisitions.get();
            var list=get(client,patient,"/results",patient,"PATIENT");assertThat(list.statusCode()).isEqualTo(200);
            assertThat(json(list).at("/items/0/id").asText()).isEqualTo(observation);
            var detail=get(client,patient,"/results/"+observation,patient,"PATIENT");assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(json(detail).path("value").decimalValue()).isEqualByComparingTo("140.125");
            var trend=get(client,patient,"/trend?code=2951-2",patient,"PATIENT");assertThat(trend.statusCode()).isEqualTo(200);
            assertThat(json(trend).at("/points/0/id").asText()).isEqualTo(observation);
            var log=get(client,patient,"/access-log",staff,"STAFF");assertThat(log.statusCode()).isEqualTo(200);
            assertThat(json(log).path("total").asLong()).isEqualTo(3); // Its own event follows materialization.
            assertThat(audits(owner,patient)).isEqualTo(4);
            for(String action:List.of("RESULT_LIST","RESULT_DETAIL","TREND","ACCESS_LOG")) {
                assertThat(owner.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND action=? AND outcome='ALLOWED'",Long.class,patient,action)).isEqualTo(1);
            }

            var created=post(client,body,key,clinician);assertThat(created.statusCode()).isEqualTo(200);
            String location=json(created).at("/entry/0/response/location").asText();
            assertThat(location).startsWith("Patient/");String newPatient=location.substring("Patient/".length());
            assertThat(owner.queryForObject("SELECT count(*) FROM patient WHERE id=?",Long.class,newPatient)).isEqualTo(1);
            assertThat(owner.queryForObject("SELECT count(*) FROM patient",Long.class)).isEqualTo(((Number)before.get("patients")).longValue()+1);
            assertThat(receipts(owner,clinician,key)).isEqualTo(1);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            assertThat(owner.queryForObject("SELECT request_hash FROM ingestion_receipt WHERE actor_id=? AND actor_role='CLINICIAN' AND idempotency_key=?",String.class,clinician,key)).isEqualTo(hash);
            assertThat(audits(owner,newPatient)).isEqualTo(1);
            var replay=post(client,body,key,clinician);assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.body()).isEqualTo(created.body());
            assertThat(json(replay).at("/entry/0/response/location").asText()).isEqualTo(location);
            assertThat(owner.queryForObject("SELECT count(*) FROM patient",Long.class)).isEqualTo(((Number)before.get("patients")).longValue()+1);
            assertThat(receipts(owner,clinician,key)).isEqualTo(1);assertThat(audits(owner,newPatient)).isEqualTo(2);
            assertThat(rejectedAcquisitions.get()).isEqualTo(rejectedBeforeRecovery);
            assertThat(output.getAll()).doesNotContain(PRIVATE_MARKER,patient,clinician,staff,observation,key,body,"140.125");
        } finally {
            unavailable.set(false);
            doCallRealMethod().when(servingDataSource).getConnection();
        }
    }

    private HttpResponse<String> get(HttpClient client,String patient,String suffix,String actor,String role) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/patients/"+patient+suffix))
                .timeout(Duration.ofSeconds(15)).header("X-Test-Actor-Id",actor).header("X-Test-Actor-Role",role).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private HttpResponse<String> post(HttpClient client,String body,String key,String clinician) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/fhir")).timeout(Duration.ofSeconds(25))
                .header("Content-Type","application/fhir+json").header("X-Test-Actor-Id",clinician).header("X-Test-Actor-Role","CLINICIAN")
                .header("X-Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static void assertSafeUnavailable(HttpResponse<String> response,String... identities) throws Exception {
        assertThat(response.statusCode()).isEqualTo(503);
        JsonNode result=json(response);assertThat(result.path("resourceType").asText()).isEqualTo("OperationOutcome");
        assertThat(result.path("issue")).hasSize(1);
        assertThat(result.at("/issue/0/severity").asText()).isEqualTo("error");
        assertThat(result.at("/issue/0/diagnostics").asText()).isIn("Service unavailable.","SERVICE_UNAVAILABLE");
        assertThat(response.body()).doesNotContain(PRIVATE_MARKER,"140.125","SQLException","Exception","jdbc:","SELECT","INSERT","password","requestHash");
        for(String identity:identities) {assertThat(response.body()).doesNotContain(identity);}
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    }
    private static JsonNode json(HttpResponse<String> response) throws Exception {return IngestionJson.mapper().readTree(response.body());}
    private static Map<String,Object> inventory(JdbcTemplate owner) {
        return owner.queryForMap("SELECT (SELECT count(*) FROM patient) AS patients,(SELECT count(*) FROM ingestion_receipt) AS receipts,(SELECT count(*) FROM access_log) AS audits");
    }
    private static long receipts(JdbcTemplate owner,String actor,String key) {
        return owner.queryForObject("SELECT count(*) FROM ingestion_receipt WHERE actor_id=? AND actor_role='CLINICIAN' AND idempotency_key=?",Long.class,actor,key);
    }
    private static long audits(JdbcTemplate owner,String patient) {return owner.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=?",Long.class,patient);}
    private static String patientBundle() {
        return """
                {"resourceType":"Bundle","type":"transaction","entry":[{
                  "fullUrl":"urn:uuid:%s",
                  "resource":{"resourceType":"Patient","id":"connection-outage-input","birthDate":"1980-01-01","gender":"female",
                    "extension":[{"url":"%s","extension":[{"url":"code","valueCode":"female"},{"url":"validFrom","valueDate":"1980-01-01"}]}]},
                  "request":{"method":"POST","url":"Patient"}}]}
                """.formatted(id(),FhirDefinitions.SEX_CONTEXT);
    }
    private static String id() {return UUID.randomUUID().toString();}
}
