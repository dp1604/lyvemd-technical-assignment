package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.config.DatabaseSafetyCheck;
import com.aldercrest.atlas.persistence.RequestTransaction;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FoundationIT.TestRoutes.class)
class FoundationIT {
    @DynamicPropertySource
    static void prepare(DynamicPropertyRegistry registry) {
        String url = System.getenv("ATLAS_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {
            throw new IllegalStateException("Foundation tests require the designated local atlas_test database.");
        }
        SchemaMigration.run(System.getenv());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RequestTransaction transaction;
    @LocalServerPort int port;

    @Test
    void requiredModelColumnsKeysNullableFieldsAndJsonbAreRetained() {
        Map<String, String> expected = Map.of(
                "patient", "id,sex,birth_date",
                "encounter", "id,patient_id,class,practitioner_id",
                "proxy_link", "patient_id,proxy_id",
                "observation", "id,patient_id,encounter_id,code,unit_code,value_num,effective,status,correction_of,component",
                "access_log", "id,actor_id,actor_role,patient_id,action,outcome,source,at");
        expected.forEach((table, columns) -> assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_schema = 'atlas' AND table_name = ?
                """, String.class, table)).contains(columns.split(",")));
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_schema = 'atlas'
                AND table_name = 'observation' AND is_nullable = 'YES'
                """, String.class)).contains("encounter_id", "value_num", "correction_of", "component");
        assertThat(jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns WHERE table_schema='atlas'
                AND table_name='observation' AND column_name='component'
                """, String.class)).isEqualTo("jsonb");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints WHERE table_schema='atlas'
                AND constraint_type='PRIMARY KEY' AND table_name IN
                ('patient','encounter','proxy_link','observation','access_log')
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.key_column_usage k
                JOIN information_schema.table_constraints c USING (constraint_catalog,constraint_schema,constraint_name)
                WHERE c.table_schema='atlas' AND c.table_name='proxy_link' AND c.constraint_type='PRIMARY KEY'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints WHERE table_schema='atlas'
                AND constraint_type='FOREIGN KEY' AND table_name IN ('encounter','observation')
                """, Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void requiredForeignKeysAndAppendOnlyPrivilegesAreEnforced() {
        assertThatThrownBy(() -> transaction.execute(() -> jdbc.update("""
                INSERT INTO encounter(id,patient_id,class,practitioner_id) VALUES (?,?,?,?)
                """, id(), "does-not-exist", "AMB", "does-not-exist"))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM observation WHERE id = ?", id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE access_log SET outcome = 'ALLOWED' WHERE id = ?", -1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE atlas.forbidden_table (id integer)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void migrationOwnerCannotPassTheServingSafetyCheck() {
        DriverManagerDataSource owner = new DriverManagerDataSource(System.getenv("ATLAS_DB_URL") + "&currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"), System.getenv("ATLAS_MIGRATION_PASSWORD"));
        assertThatThrownBy(() -> new DatabaseSafetyCheck(new JdbcTemplate(owner)).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unsafe");
    }

    @Test
    void contentAndAuditCommitTogetherUsingAConsistentReadWriteSnapshot() {
        String patientId = id();
        transaction.execute(() -> {
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("off");
            jdbc.update("INSERT INTO patient(id,birth_date) VALUES (?,?)", patientId, LocalDate.of(2000, 1, 1));
            assertThat(jdbc.queryForObject("SELECT id FROM patient WHERE id = ?", String.class, patientId)).isEqualTo(patientId);
            audit(patientId, "ALLOWED");
            return patientId;
        });
        assertThat(count("patient", "id", patientId)).isEqualTo(1);
        assertThat(count("access_log", "patient_id", patientId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient WHERE id = ?", Integer.class,
                patientId + "' OR '1'='1")).isZero();
    }

    @Test
    void auditFailureRollsBackClinicalWorkAndPreventsReturningContent() {
        String patientId = id();
        assertThatThrownBy(() -> transaction.execute(() -> {
            jdbc.update("INSERT INTO patient(id) VALUES (?)", patientId);
            String content = jdbc.queryForObject("SELECT id FROM patient WHERE id = ?", String.class, patientId);
            audit(patientId, "INVALID_OUTCOME");
            return content;
        })).isInstanceOf(DataAccessException.class);
        assertThat(count("patient", "id", patientId)).isZero();
        assertThat(count("access_log", "patient_id", patientId)).isZero();
    }

    @Test
    void expectedDenialCanCommitForAnUnverifiedActorAndUnknownPatient() {
        String target = id();
        String result = transaction.execute(() -> {
            jdbc.update("""
                    INSERT INTO access_log(actor_id,actor_role,patient_id,action,outcome,source)
                    VALUES (NULL,NULL,?,'RESULT_DETAIL','DENIED','HTTP')
                    """, target);
            return "DENIED";
        });
        assertThat(result).isEqualTo("DENIED");
        assertThat(count("access_log", "patient_id", target)).isEqualTo(1);
    }

    @Test
    void safeHttpErrorsDoNotEchoPathsOrQueriesAndDisableCaching() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/private-synthetic-marker?token=never-echo")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).contains("OperationOutcome", "Not available.")
                    .doesNotContain("private-synthetic-marker", "never-echo", "trace", "exception", "timestamp");
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
        }
    }

    @Test
    void frameworkClientErrorsKeepTheirStatusWithoutLeakingReasons() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/test-foundation?count=secret-marker", "/test-foundation"}) {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("OperationOutcome").doesNotContain("secret-marker", "count", "trace");
            }
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/test-foundation?count=1")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.body()).doesNotContain("secret-reason");
        }
    }

    @RestController
    static class TestRoutes {
        @GetMapping("/test-foundation")
        void clientError(@RequestParam int count) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "secret-reason");
        }
    }

    private void audit(String patientId, String outcome) {
        jdbc.update("""
                INSERT INTO access_log(actor_id,actor_role,patient_id,action,outcome,source)
                VALUES (?,'PATIENT',?,'RESULT_DETAIL',?,'HTTP')
                """, patientId, patientId, outcome);
    }

    private int count(String table, String column, String value) {
        // Identifiers are test-owned constants only; all supplied values remain bound parameters.
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
