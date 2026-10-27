package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** real HTTP + designated synthetic PostgreSQL16; no test DB execution by the author. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessLogIT {
    private static final ObjectMapper JSON=IngestionJson.mapper();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final String FAULT_FUNCTION="atlas.test_g5_log_audit_failure";
    private static final String FAULT_TRIGGER="test_g5_log_audit_failure";

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("ATLAS_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {
            throw new IllegalStateException("Designated synthetic test DB required.");
        }
        SchemaMigration.run(System.getenv());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired com.aldercrest.atlas.workflow.WorkflowService workflow;
    @LocalServerPort int port;
    private JdbcTemplate owner;
    private String patient,otherPatient,clinician,proxy,staff;

    @BeforeEach void fixture() {
        String url=System.getenv("ATLAS_DB_URL");
        owner=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"),System.getenv("ATLAS_MIGRATION_PASSWORD")));
        patient=id();otherPatient=id();clinician=id();proxy=id();staff=id();
        owner.update("INSERT INTO actor(id,role) VALUES (?,'PATIENT'),(?,'PATIENT'),(?,'CLINICIAN'),(?,'PROXY'),(?,'STAFF')",
                patient,otherPatient,clinician,proxy,staff);
        owner.update("INSERT INTO patient(id,sex,birth_date) VALUES (?,'female','2010-01-01'),(?,'male','2000-01-01')",patient,otherPatient);
        // These legitimate clinical relationships deliberately do not confer access-log rights.
        owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id) VALUES (?,?,'AMB',?)",id(),patient,clinician);
        owner.update("INSERT INTO proxy_link(patient_id,proxy_id) VALUES (?,?)",patient,proxy);
    }

    @Test void selfPatientAndStaffReadOnlyTheTargetsMinimalAuditFields() throws Exception {
        seed(patient,clinician,"RESULT_DETAIL","ALLOWED","2026-01-01T00:00:00Z");
        seed(otherPatient,clinician,"RESULT_DETAIL","ALLOWED","2026-01-01T00:00:00Z");
        var self=get(patient,"",patient,"PATIENT");
        assertThat(self.statusCode()).isEqualTo(200);
        JsonNode selfBody=JSON.readTree(self.body());
        assertThat(selfBody.path("total").asLong()).isEqualTo(1);
        assertThat(selfBody.path("items")).hasSize(1);
        assertThat(fields(selfBody.path("items").get(0))).containsExactlyInAnyOrder(
                "id","actorId","actorRole","patientId","action","outcome","source","at","actorRosterVerified","targetProxyId");
        assertThat(selfBody.at("/items/0/patientId").asText()).isEqualTo(patient);
        assertThat(selfBody.at("/items/0/action").asText()).isEqualTo("RESULT_DETAIL");
        assertThat(selfBody.at("/items/0/actorRosterVerified").asBoolean()).isTrue();
        assertThat(self.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(auditCount(patient)).isEqualTo(2); // Seed + committed self read, omitted from its response.

        var byStaff=get(patient,"",staff,"STAFF");
        assertThat(byStaff.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(byStaff.body()).path("total").asLong()).isEqualTo(2);
        assertThat(auditCount(patient)).isEqualTo(3);
        assertAudit(staff,"STAFF",true,"ALLOWED");
        assertThat(auditCount(otherPatient)).isEqualTo(1);
    }

    @Test void clinicalRightsNeverGrantClinicianProxyOrOtherPatientLogRights() throws Exception {
        for(String[] actor:List.of(new String[]{clinician,"CLINICIAN"},new String[]{proxy,"PROXY"},new String[]{otherPatient,"PATIENT"})) {
            long before=auditCount(patient);
            var response=get(patient,"",actor[0],actor[1]);
            assertUnavailable(response,404);
            assertThat(auditCount(patient)).isEqualTo(before+1);
            assertAudit(actor[0],actor[1],true,"DENIED");
        }
        var existing=get(patient,"",otherPatient,"PATIENT");
        var missing=get(id(),"",otherPatient,"PATIENT");
        assertThat(existing.statusCode()).isEqualTo(missing.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(existing.body())).isEqualTo(JSON.readTree(missing.body()));
    }

    @Test void unknownAndRosterMismatchedPairsRemainUnverifiedAndUnavailable() throws Exception {
        String unknown=id();
        for(String[] actor:List.of(new String[]{unknown,"PATIENT"},new String[]{staff,"PATIENT"})) {
            long before=auditCount(patient);
            var response=get(patient,"",actor[0],actor[1]);
            assertUnavailable(response,404);
            assertThat(auditCount(patient)).isEqualTo(before+1);
            assertAudit(actor[0],actor[1],false,"DENIED");
        }
    }

    @Test void malformedAndDuplicateHeadersAreAuditedWithoutInventingAnIdentity() throws Exception {
        List<List<String>> headers=List.of(
                List.of(),
                List.of("X-Test-Actor-Id",patient),
                List.of("X-Test-Actor-Role","PATIENT"),
                List.of("X-Test-Actor-Id",patient,"X-Test-Actor-Role","patient"),
                List.of("X-Test-Actor-Id",patient,"X-Test-Actor-Id",otherPatient,"X-Test-Actor-Role","PATIENT"),
                List.of("X-Test-Actor-Id",patient,"X-Test-Actor-Role","PATIENT","X-Test-Actor-Role","STAFF"),
                List.of("X-Test-Actor-Id","bad!id","X-Test-Actor-Role","PATIENT"));
        for(List<String> claimed:headers) {
            long before=auditCount(patient);
            var response=send(patient,"",claimed);
            assertUnavailable(response,400);
            assertThat(auditCount(patient)).isEqualTo(before+1);
            var event=latestAudit();
            assertThat(event.path("actorId").isNull()).isTrue();
            assertThat(event.path("actorRole").isNull()).isTrue();
            assertThat(event.path("actorRosterVerified").asBoolean()).isFalse();
            assertThat(event.path("outcome").asText()).isEqualTo("DENIED");
        }
    }

    @Test void eachPageCountsItsReadSnapshotExcludesOwnEventAndOrdersTimestampTiesByIdDescending() throws Exception {
        for(int n=0;n<5;n++) {seed(patient,clinician,"RESULT_DETAIL","ALLOWED","2026-01-01T00:00:00Z");}
        List<String> original=orderedIds(patient);
        assertThat(original).hasSize(5);
        // Numeric identity order, not lexical string order, defines the SQL tie-break.
        for(int index=1;index<original.size();index++) {
            assertThat(Long.parseLong(original.get(index-1))).isGreaterThan(Long.parseLong(original.get(index)));
        }
        var first=get(patient,"?offset=1&limit=2",staff,"STAFF");
        JsonNode firstBody=JSON.readTree(first.body());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(firstBody.path("total").asLong()).isEqualTo(5);
        assertThat(firstBody.path("offset").asInt()).isEqualTo(1);
        assertThat(firstBody.path("limit").asInt()).isEqualTo(2);
        assertThat(itemIds(firstBody)).containsExactlyElementsOf(original.subList(1,3));
        assertThat(auditCount(patient)).isEqualTo(6);

        // Every request adds one row. A later offset page is checked against its new snapshot,
        // rather than claiming offset pagination freezes history across concurrent writes.
        List<String> beforeSecond=orderedIds(patient);
        var second=get(patient,"?offset=2&limit=2",staff,"STAFF");
        JsonNode secondBody=JSON.readTree(second.body());
        assertThat(secondBody.path("total").asLong()).isEqualTo(6);
        assertThat(itemIds(secondBody)).containsExactlyElementsOf(beforeSecond.subList(2,4));
        assertThat(auditCount(patient)).isEqualTo(7);

        var beyond=get(patient,"?offset=1000000&limit=200",staff,"STAFF");
        assertThat(beyond.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(beyond.body()).path("items")).isEmpty();
        assertThat(JSON.readTree(beyond.body()).path("total").asLong()).isEqualTo(7);
        assertThat(auditCount(patient)).isEqualTo(8);
    }

    @Test void emptyReadablePatientReturnsEmptyPageAndTheNextReadSeesItsFirstReadAudit() throws Exception {
        var first=get(patient,"",patient,"PATIENT");
        JsonNode body=JSON.readTree(first.body());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(body.path("items")).isEmpty();
        assertThat(body.path("total").asLong()).isZero();
        assertThat(body.path("offset").asInt()).isZero();
        assertThat(body.path("limit").asInt()).isEqualTo(50);
        var next=get(patient,"",patient,"PATIENT");
        JsonNode nextBody=JSON.readTree(next.body());
        assertThat(nextBody.path("total").asLong()).isEqualTo(1);
        assertThat(nextBody.path("items")).hasSize(1);
        assertThat(nextBody.at("/items/0/action").asText()).isEqualTo("ACCESS_LOG");
        assertThat(nextBody.at("/items/0/actorId").asText()).isEqualTo(patient);
        assertThat(auditCount(patient)).isEqualTo(2);
    }

    @Test void invalidPagingAndDuplicateOrUnknownParametersProduceCommittedDenialAudits() throws Exception {
        for(String query:List.of("?offset=-1","?offset=1000001","?offset=2147483648","?offset=1.0",
                "?limit=0","?limit=201","?limit=","?limit=one","?offset=0&offset=1",
                "?limit=10&limit=20","?unknown=1")) {
            long before=auditCount(patient);
            var response=get(patient,query,staff,"STAFF");
            assertUnavailable(response,400);
            assertThat(auditCount(patient)).as(query).isEqualTo(before+1);
            assertAudit(staff,"STAFF",true,"DENIED");
            assertThat(response.body()).doesNotContain(query);
        }
        // Query validation is existence-independent for an otherwise known actor.
        var present=get(patient,"?limit=0",staff,"STAFF");
        var absent=get(id(),"?limit=0",staff,"STAFF");
        assertThat(present.statusCode()).isEqualTo(absent.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(present.body())).isEqualTo(JSON.readTree(absent.body()));
    }

    @Test void auditInsertFailureWithholdsAlreadyReadLogContentAndRecoversAfterFaultRemoval() throws Exception {
        seed(patient,clinician,"RESULT_DETAIL","ALLOWED","2026-01-01T00:00:00Z");
        long before=auditCount(patient);
        try {
            installFault(false,staff);
            var failed=get(patient,"",staff,"STAFF");
            assertUnavailable(failed,503);
            assertThat(failed.body()).doesNotContain(patient,clinician,"RESULT_DETAIL","test_g5_log_audit_failure");
            assertThat(auditCount(patient)).isEqualTo(before);
        } finally {removeFault();}
        var recovered=get(patient,"",staff,"STAFF");
        assertThat(recovered.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(recovered.body()).path("total").asLong()).isEqualTo(before);
        assertThat(auditCount(patient)).isEqualTo(before+1);
    }

    @Test void deferredAuditCommitFailureNeverReturnsMaterializedContentAndRollsBackTheAudit() throws Exception {
        seed(patient,clinician,"RESULT_DETAIL","ALLOWED","2026-01-01T00:00:00Z");
        long before=auditCount(patient);
        try {
            installFault(true,staff);
            var failed=get(patient,"",staff,"STAFF");
            assertUnavailable(failed,503);
            assertThat(failed.body()).doesNotContain(patient,clinician,"RESULT_DETAIL","test_g5_log_audit_failure");
            assertThat(auditCount(patient)).isEqualTo(before);
        } finally {removeFault();}
        var recovered=get(patient,"",staff,"STAFF");
        assertThat(recovered.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(recovered.body()).path("total").asLong()).isEqualTo(before);
        assertThat(auditCount(patient)).isEqualTo(before+1);
    }

    @Test void aDenialWhoseAuditCannotCommitReturnsUnavailableRatherThanAnUnauditedDenial() throws Exception {
        long before=auditCount(patient);
        try {
            installFault(true,proxy);
            assertUnavailable(get(patient,"",proxy,"PROXY"),503);
            assertThat(auditCount(patient)).isEqualTo(before);
        } finally {removeFault();}
        assertUnavailable(get(patient,"",proxy,"PROXY"),404);
        assertThat(auditCount(patient)).isEqualTo(before+1);
        assertAudit(proxy,"PROXY",true,"DENIED");
    }

    @Test void actualIndividualRevocationAppearsInThePermittedPatientLogWithItsTarget() throws Exception {
        var result=workflow.revokeProxy(new com.aldercrest.atlas.security.TestActor(patient,"PATIENT",true,false),patient,proxy);
        assertThat(result.status()).isEqualTo(200);
        var response=get(patient,"",patient,"PATIENT");assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body=JSON.readTree(response.body());assertThat(body.path("total").asLong()).isEqualTo(1);
        assertThat(body.at("/items/0/actorId").asText()).isEqualTo(patient);
        assertThat(body.at("/items/0/action").asText()).isEqualTo("PROXY_REVOKE");
        assertThat(body.at("/items/0/targetProxyId").asText()).isEqualTo(proxy);
        assertThat(body.at("/items/0/source").asText()).isEqualTo("LOCAL_COMMAND");
        assertThat(body.at("/items/0/outcome").asText()).isEqualTo("ALLOWED");
        assertThat(jdbc.queryForObject("SELECT active FROM proxy_link WHERE patient_id=? AND proxy_id=?",Boolean.class,patient,proxy)).isFalse();
    }
    private HttpResponse<String> get(String target,String query,String actorId,String actorRole) throws Exception {
        return send(target,query,List.of("X-Test-Actor-Id",actorId,"X-Test-Actor-Role",actorRole));
    }
    private HttpResponse<String> send(String target,String query,List<String> headers) throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/patients/"+target+"/access-log"+query))
                .timeout(Duration.ofSeconds(20)).GET();
        for(int index=0;index<headers.size();index+=2) {request.header(headers.get(index),headers.get(index+1));}
        return HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private void assertUnavailable(HttpResponse<String> response,int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        JsonNode body=JSON.readTree(response.body());
        assertThat(body.path("resourceType").asText()).isEqualTo("OperationOutcome");
        assertThat(body.has("items")).isFalse();assertThat(body.has("total")).isFalse();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }
    private void assertAudit(String actorId,String role,boolean rosterVerified,String outcome) {
        JsonNode event=latestAudit();
        assertThat(event.path("actorId").asText()).isEqualTo(actorId);
        assertThat(event.path("actorRole").asText()).isEqualTo(role);
        assertThat(event.path("actorRosterVerified").asBoolean()).isEqualTo(rosterVerified);
        assertThat(event.path("action").asText()).isEqualTo("ACCESS_LOG");
        assertThat(event.path("outcome").asText()).isEqualTo(outcome);
        assertThat(event.path("source").asText()).isEqualTo("HTTP");
    }
    private JsonNode latestAudit() {
        return jdbc.queryForObject("SELECT actor_id,actor_role,actor_roster_verified,action,outcome,source FROM access_log WHERE patient_id=? ORDER BY id DESC LIMIT 1",
                (rs,n) -> {
                    var row=JSON.createObjectNode();row.put("actorId",rs.getString("actor_id"));row.put("actorRole",rs.getString("actor_role"));
                    row.put("actorRosterVerified",rs.getBoolean("actor_roster_verified"));row.put("action",rs.getString("action"));
                    row.put("outcome",rs.getString("outcome"));row.put("source",rs.getString("source"));return row;
                },patient);
    }
    private long auditCount(String target) {return jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=?",Long.class,target);}
    private List<String> orderedIds(String target) {
        return jdbc.query("SELECT id FROM access_log WHERE patient_id=? ORDER BY at DESC,id DESC",(rs,n) -> Long.toString(rs.getLong(1)),target);
    }
    private void seed(String target,String actor,String action,String outcome,String at) {
        owner.update("INSERT INTO access_log(actor_id,actor_role,patient_id,action,outcome,source,at,actor_roster_verified) VALUES (?,'CLINICIAN',?,?,?,'HTTP',CAST(? AS timestamptz),true)",
                actor,target,action,outcome,at);
    }
    private void installFault(boolean deferred,String faultActor) {
        if(!faultActor.matches("[0-9a-f-]{36}")) {throw new IllegalArgumentException("Synthetic actor UUID required.");}
        owner.execute("CREATE FUNCTION "+FAULT_FUNCTION+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id=TG_ARGV[0] AND NEW.action='ACCESS_LOG' THEN RAISE EXCEPTION 'private synthetic audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        String timing=deferred?"CREATE CONSTRAINT TRIGGER "+FAULT_TRIGGER+" AFTER INSERT ON access_log DEFERRABLE INITIALLY DEFERRED":"CREATE TRIGGER "+FAULT_TRIGGER+" BEFORE INSERT ON access_log";
        owner.execute(timing+" FOR EACH ROW EXECUTE FUNCTION "+FAULT_FUNCTION+"('"+faultActor+"')");
    }
    private void removeFault() {
        try {owner.execute("DROP TRIGGER IF EXISTS "+FAULT_TRIGGER+" ON access_log");}
        finally {owner.execute("DROP FUNCTION IF EXISTS "+FAULT_FUNCTION+"()");}
    }
    private static Set<String> fields(JsonNode node) {Set<String> fields=new HashSet<>();node.fieldNames().forEachRemaining(fields::add);return fields;}
    private static List<String> itemIds(JsonNode page) {List<String> result=new ArrayList<>();page.path("items").forEach(row -> result.add(row.path("id").asText()));return result;}
    private static String id() {return UUID.randomUUID().toString();}
}
