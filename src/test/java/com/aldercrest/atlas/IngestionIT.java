package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.ingest.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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

/** Real loopback HTTP + PostgreSQL16; only designated synthetic atlas_test is permitted. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(IngestionIT.FixedClock.class)
class IngestionIT {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        java.time.Clock assessmentClock() {return java.time.Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"),java.time.ZoneOffset.UTC);}
    }
    private static final ObjectMapper JSON=IngestionJson.mapper();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("ATLAS_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {throw new IllegalStateException("Designated synthetic test DB required.");}
        SchemaMigration.run(System.getenv());
    }
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean FhirValidation validator;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean IngestionRepository repository;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean BundlePlanner planner;
    @Autowired com.aldercrest.atlas.workflow.WorkflowService workflow;
    @LocalServerPort int port;
    private JdbcTemplate owner;
    private String clinician,otherClinician,patient,encounter,proxy,staff;

    @BeforeEach void fixture() {
        owner=new JdbcTemplate(new DriverManagerDataSource(System.getenv("ATLAS_DB_URL")+"&currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"),System.getenv("ATLAS_MIGRATION_PASSWORD")));
        clinician=id();otherClinician=id();patient=id();encounter=id();proxy=id();staff=id();
        owner.update("INSERT INTO actor(id,role) VALUES (?,'CLINICIAN'),(?,'CLINICIAN')",clinician,otherClinician);
        owner.update("INSERT INTO actor(id,role) VALUES (?,'PATIENT'),(?,'PROXY'),(?,'STAFF')",patient,proxy,staff);
        owner.update("INSERT INTO patient(id,sex,birth_date) VALUES (?,'male','2000-01-01')",patient);
        owner.update("INSERT INTO patient_sex_context(patient_id,valid_from,sex) VALUES (?,'2000-01-01','male')",patient);
        owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id,fhir_status) VALUES (?,?,'AMB',?,'finished')",encounter,patient,clinician);
    }

    @Test void resolvesForwardUuidReferencesNormalizesAndPreservesExactContextAndHeldState() throws Exception {
        String p=urn(),e=urn();ObjectNode obs=observation(p,"2160-0","umol/L",88.4);
        obs.put("id","client-observation");
        obs.set("encounter",ref(e));obs.put("effectiveDateTime","2026-01-01T00:00:00.123456789Z");
        obs.set("extension",JSON.valueToTree(List.of(Map.of("url",FhirDefinitions.STANDARDIZED,"valueBoolean",true))));
        String body=bundle(entry(obs,urn()),entry(encounter(p,"AMB",clinician),e),entry(newPatient(),p));
        var response=post(body,key());assertThat(response.statusCode()).isEqualTo(200);
        JsonNode reply=JSON.readTree(response.body());String obsId=created(reply,0),encId=created(reply,1),patId=created(reply,2);
        assertThat(patId).isNotEqualTo("client-patient");assertThat(obsId).isNotEqualTo("client-observation");
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM observation WHERE id=?",obsId);
        assertThat(row.get("patient_id")).isEqualTo(patId);assertThat(row.get("encounter_id")).isEqualTo(encId);
        assertThat((java.math.BigDecimal)row.get("value_num")).isEqualByComparingTo("1");
        assertThat(row.get("unit_code")).isEqualTo("mg/dL");assertThat(row.get("sex_context")).isEqualTo("female");
        assertThat(row.get("standardized_creatinine")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT effective_submicro_nanos FROM observation WHERE id=?",Integer.class,obsId)).isEqualTo(789);
        Instant restored=jdbc.queryForObject("SELECT effective,effective_submicro_nanos FROM observation WHERE id=?",
                (rs,n) -> ExactTime.restore(rs.getObject(1,OffsetDateTime.class),rs.getInt(2)),obsId);
        assertThat(restored).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456789Z"));
        assertThat(jdbc.queryForObject("SELECT released_at IS NULL AND reviewed_at IS NULL FROM observation_review WHERE observation_id=?",Boolean.class,obsId)).isTrue();
        assertThat(audits(patId,"ALLOWED")).isEqualTo(1);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test void invalidEntryNamesItsIndexRejectsValidSiblingsAndDurablyReplaysFailure() throws Exception {
        String p=urn();ObjectNode invalid=observation(p,"39156-5","kg/m2",22);
        String body=bundle(entry(newPatient(),p),entry(observation(p,"29463-7","kg",65),urn()),entry(invalid,urn()));
        int before=count("patient");String key=key();var response=post(body,key);
        assertThat(response.statusCode()).isEqualTo(400);assertThat(count("patient")).isEqualTo(before);
        JsonNode reply=JSON.readTree(response.body());assertThat(reply.path("type").asText()).isEqualTo("transaction-response");
        assertThat(reply.at("/entry/0/response/status").asText()).startsWith("424");
        assertThat(reply.at("/entry/1/response/status").asText()).startsWith("424");
        assertThat(reply.at("/entry/2/response/outcome/issue/0/diagnostics").asText()).isEqualTo("DERIVED_INPUT_UNSUPPORTED");
        assertThat(reply.at("/entry/2/response/outcome/issue/0/expression/0").asText()).isEqualTo("Bundle.entry[2]");
        assertThat(post(body,key).body()).isEqualTo(response.body());
        assertThat(jdbc.queryForObject("SELECT http_status FROM ingestion_receipt WHERE actor_id=? AND idempotency_key=?",Integer.class,clinician,key)).isEqualTo(400);
    }

    @Test void structuralAndUnitErrorsCannotLeavePartialClinicalRows() throws Exception {
        for(String kind:List.of("status","unit","modifier","conditional")) {
            String p=urn();ObjectNode bad=observation(p,"2160-0","mg/dL",1);
            if(kind.equals("status")) {bad.remove("status");}
            if(kind.equals("unit")) {((ObjectNode)bad.get("valueQuantity")).put("code","unsupported");}
            if(kind.equals("modifier")) {bad.set("modifierExtension",JSON.createArrayNode());}
            ObjectNode badEntry=entry(bad,urn());if(kind.equals("conditional")) {((ObjectNode)badEntry.get("request")).put("ifNoneExist","identifier=synthetic");}
            int before=count("patient");var response=post(bundle(entry(newPatient(),p),badEntry),key());
            assertThat(response.statusCode()).as(kind).isEqualTo(400);assertThat(count("patient")).as(kind).isEqualTo(before);
            assertThat(JSON.readTree(response.body()).at("/entry/1/response/status").asText()).startsWith("400");
        }
    }

    @Test void unavailableStoredPatientsHaveIdenticalFailureAndCannotBeSelfAuthorizedByNewEncounter() throws Exception {
        String unavailable=id();owner.update("INSERT INTO patient(id) VALUES (?)",unavailable);
        var hidden=post(bundle(entry(observation("Patient/"+unavailable,"29463-7","kg",60),urn())),key());
        var missing=post(bundle(entry(observation("Patient/"+id(),"29463-7","kg",60),urn())),key());
        assertThat(hidden.statusCode()).isEqualTo(400);assertThat(hidden.body()).isEqualTo(missing.body()).contains("REFERENCE_UNAVAILABLE");
        int before=count("encounter");
        var bootstrap=post(bundle(entry(encounter("Patient/"+unavailable,"AMB",clinician),urn()),
                entry(observation("Patient/"+unavailable,"29463-7","kg",60),urn())),key());
        assertThat(bootstrap.statusCode()).isEqualTo(400);assertThat(count("encounter")).isEqualTo(before);
        assertThat(audits(unavailable,"DENIED")).isEqualTo(2);
    }

    @Test void rejectsRemoteWrongTypeDuplicateAndUnresolvedReferencesWithoutFetching() throws Exception {
        for(String reference:List.of("https://example.org/private-patient","Encounter/"+encounter,urn(),"Patient/missing")) {
            var response=post(bundle(entry(observation(reference,"29463-7","kg",60),urn())),key());
            assertThat(response.statusCode()).as(reference).isEqualTo(400);assertThat(response.body()).doesNotContain(reference);
        }
        String duplicate=urn();int before=count("patient");
        var response=post(bundle(entry(newPatient(),duplicate),entry(newPatient(),duplicate)),key());
        assertThat(response.statusCode()).isEqualTo(400);assertThat(count("patient")).isEqualTo(before);
        assertThat(response.body()).contains("DUPLICATE_REFERENCE_IDENTITY");
        ObjectNode encounterMismatch=observation("Patient/"+patient,"29463-7","kg",60);
        String foreignPatient=id(),foreignEncounter=id();owner.update("INSERT INTO patient(id) VALUES (?)",foreignPatient);
        owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id) VALUES (?,?,'AMB',?)",foreignEncounter,foreignPatient,otherClinician);
        encounterMismatch.set("encounter",ref("Encounter/"+foreignEncounter));
        assertThat(post(bundle(entry(encounterMismatch,urn())),key()).body()).contains("REFERENCE_UNAVAILABLE");
    }

    @Test void exactBodyReplayDoesNotDuplicateAndChangedBytesConflict() throws Exception {
        String body=bundle(entry(newPatient(),urn())),key=key();int before=count("patient");
        var first=post(body,key);var replay=post(body,key);
        assertThat(first.statusCode()).isEqualTo(200);assertThat(replay.statusCode()).isEqualTo(200);assertThat(replay.body()).isEqualTo(first.body());
        assertThat(count("patient")).isEqualTo(before+1);
        assertThat(post(body+" ",key).statusCode()).isEqualTo(409);assertThat(count("patient")).isEqualTo(before+1);
        String patientId=created(JSON.readTree(first.body()),0);
        assertThat(audits(patientId,"ALLOWED")).isEqualTo(2);assertThat(audits(patientId,"DENIED")).isEqualTo(1);
    }

    @Test void keysAreActorScopedAndRosterIsRecheckedBeforeReplay() throws Exception {
        String body=bundle(entry(newPatient(),urn())),key=key();var first=post(body,key);
        var other=send(body.getBytes(StandardCharsets.UTF_8),key,otherClinician,"CLINICIAN","application/fhir+json");
        assertThat(first.statusCode()).isEqualTo(200);assertThat(other.statusCode()).isEqualTo(200);assertThat(other.body()).isNotEqualTo(first.body());
        owner.update("UPDATE actor SET role='STAFF' WHERE id=?",clinician);
        assertThat(post(body,key).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_receipt WHERE idempotency_key=?",Integer.class,key)).isEqualTo(2);
    }

    @Test void concurrentIdenticalRequestsContendInDatabaseAndReturnOneCommittedEffect() throws Exception {
        owner.setQueryTimeout(2); // Bound this test's operational lock/snapshot proof queries.
        String key=key(),body=bundle(entry(newPatient(),urn()));int before=count("patient");
        JsonNode expected=JSON.readTree(body);
        CountDownLatch validated=new CountDownLatch(2),enterTransactions=new CountDownLatch(1);
        CountDownLatch snapshotsReady=new CountDownLatch(2),enterDatabaseLock=new CountDownLatch(1);
        AtomicInteger lockCalls=new AtomicInteger();Map<Integer,String> snapshots=new ConcurrentHashMap<>();
        ExecutorService pool=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().factory());
        Future<HttpResponse<String>> a=null,b=null;
        org.mockito.Mockito.doAnswer(call -> {
            var checks=(BundlePlanner.StructuralChecks)call.callRealMethod();
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(checks.available()).as("Actual local prevalidation available").isTrue();
            assertThat(checks.invalidEntries()).isEmpty();
            validated.countDown();
            assertThat(enterTransactions.await(20,TimeUnit.SECONDS)).as("Release outside-transaction prevalidation rendezvous").isTrue();
            return checks;
        }).when(planner).prevalidate(org.mockito.ArgumentMatchers.argThat(input -> expected.equals(input)));
        org.mockito.Mockito.doAnswer(call -> {
            // Only the initial two real transactions rendezvous. A serialization/unique retry
            // must call the real lock immediately in its new transaction/snapshot.
            if(lockCalls.getAndIncrement()<2) {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                Map<String,Object> snapshot=jdbc.queryForMap("SELECT pg_backend_pid() AS pid, current_setting('transaction_isolation') AS isolation, pg_current_snapshot()::text AS snapshot");
                assertThat(snapshot.get("isolation")).isEqualTo("repeatable read");
                int pid=((Number)snapshot.get("pid")).intValue();String value=(String)snapshot.get("snapshot");
                assertThat(value).isNotBlank();assertThat(snapshots.putIfAbsent(pid,value)).isNull();
                snapshotsReady.countDown();
                assertThat(enterDatabaseLock.await(10,TimeUnit.SECONDS)).as("Release initial transaction lock rendezvous").isTrue();
            }
            call.callRealMethod();return null;
        }).when(repository).lockKey(org.mockito.ArgumentMatchers.argThat(actor -> actor!=null && clinician.equals(actor.id()) && "CLINICIAN".equals(actor.role())),org.mockito.ArgumentMatchers.eq(key));
        try(var gate=owner.getDataSource().getConnection()) {
            int gatePid;
            try(var statement=gate.createStatement()) {
                statement.setQueryTimeout(3);
                try(var result=statement.executeQuery("SELECT pg_backend_pid()")) {assertThat(result.next()).isTrue();gatePid=result.getInt(1);}
            }
            try(var lock=gate.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?,0))")) {lock.setQueryTimeout(3);lock.setString(1,clinician+":CLINICIAN:"+key);lock.execute();}
            boolean waiting=false;
            try {
                Callable<HttpResponse<String>> request=() -> concurrentPost(body,key);
                a=pool.submit(request);b=pool.submit(request);
                assertThat(validated.await(15,TimeUnit.SECONDS)).as("Both real prevalidations completed before transaction rendezvous").isTrue();
                enterTransactions.countDown();
                assertThat(snapshotsReady.await(5,TimeUnit.SECONDS)).as("Both initial roster reads established real RR snapshots").isTrue();
                assertThat(snapshots).hasSize(2);List<Integer> pids=new ArrayList<>(snapshots.keySet());
                enterDatabaseLock.countDown();
                // This two-second clock measures only the coordinated PostgreSQL lock phase.
                waiting=waitForScopedAdvisoryWaiters(gatePid,pids.get(0),pids.get(1));
            } finally {
                enterTransactions.countDown();enterDatabaseLock.countDown();
                try(var unlock=gate.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?,0))")) {
                    unlock.setQueryTimeout(3);
                    unlock.setString(1,clinician+":CLINICIAN:"+key);
                    try(var result=unlock.executeQuery()) {assertThat(result.next()).isTrue();assertThat(result.getBoolean(1)).isTrue();}
                }
            }
            var first=a.get(20,TimeUnit.SECONDS);var second=b.get(20,TimeUnit.SECONDS);
            assertThat(waiting).as("Both requests blocked in PostgreSQL after acquiring their old snapshots").isTrue();
            assertThat(first.statusCode()).isEqualTo(200);assertThat(second.statusCode()).isEqualTo(200);assertThat(first.body()).isEqualTo(second.body());
            assertThat(count("patient")).isEqualTo(before+1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_receipt WHERE actor_id=? AND actor_role='CLINICIAN' AND idempotency_key=?",Integer.class,clinician,key)).isEqualTo(1);
            assertThat(audits(created(JSON.readTree(first.body()),0),"ALLOWED")).isEqualTo(2);
            org.mockito.Mockito.verify(planner,org.mockito.Mockito.times(2)).prevalidate(org.mockito.ArgumentMatchers.argThat(input -> expected.equals(input)));
        } finally {
            enterTransactions.countDown();enterDatabaseLock.countDown();
            if(a!=null && !a.isDone()) {a.cancel(true);}if(b!=null && !b.isDone()) {b.cancel(true);}
            pool.shutdownNow();
            try {assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).as("Owned HTTP workers terminated").isTrue();}
            finally {
                org.mockito.Mockito.doCallRealMethod().when(planner).prevalidate(org.mockito.ArgumentMatchers.argThat(input -> expected.equals(input)));
                org.mockito.Mockito.doCallRealMethod().when(repository).lockKey(org.mockito.ArgumentMatchers.argThat(actor -> actor!=null && clinician.equals(actor.id()) && "CLINICIAN".equals(actor.role())),org.mockito.ArgumentMatchers.eq(key));
            }
        }
    }

    @Test void correctionAppendsRetainsOriginalAndRejectsNonHeadOrChangedContext() throws Exception {
        ObjectNode original=observation("Patient/"+patient,"2160-0","mg/dL",1);original.set("encounter",ref("Encounter/"+encounter));
        var initial=post(bundle(entry(original,urn())),key());String originalId=created(JSON.readTree(initial.body()),0);
        ObjectNode corrected=original.deepCopy();corrected.put("status","amended");((ObjectNode)corrected.get("valueQuantity")).put("value",2);
        correction(corrected,"Observation/"+originalId);
        var amendment=post(bundle(entry(corrected,urn())),key());assertThat(amendment.statusCode()).isEqualTo(200);
        String amendedId=created(JSON.readTree(amendment.body()),0);
        assertThat(jdbc.queryForObject("SELECT value_num FROM observation WHERE id=?",java.math.BigDecimal.class,originalId)).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("SELECT correction_of FROM observation WHERE id=?",String.class,amendedId)).isEqualTo(originalId);
        assertThat(jdbc.queryForObject("SELECT released_at IS NULL FROM observation_review WHERE observation_id=?",Boolean.class,amendedId)).isTrue();
        var originalDetail=get(originalId,clinician,"CLINICIAN");var amendedDetail=get(amendedId,clinician,"CLINICIAN");
        assertThat(originalDetail.statusCode()).isEqualTo(200);assertThat(amendedDetail.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(originalDetail.body()).path("value").decimalValue()).isEqualByComparingTo("1");
        assertThat(JSON.readTree(originalDetail.body()).path("unit").asText()).isEqualTo("mg/dL");
        assertThat(JSON.readTree(originalDetail.body()).at("/range/low").decimalValue()).isEqualByComparingTo("0.7");
        assertThat(JSON.readTree(originalDetail.body()).at("/range/high").decimalValue()).isEqualByComparingTo("1.3");
        assertThat(JSON.readTree(originalDetail.body()).path("flag").asText()).isEqualTo("NORMAL");
        assertThat(JSON.readTree(amendedDetail.body()).path("correctionOf").asText()).isEqualTo(originalId);
        assertThat(JSON.readTree(amendedDetail.body()).path("value").decimalValue()).isEqualByComparingTo("2");
        assertThat(JSON.readTree(amendedDetail.body()).path("flag").asText()).isEqualTo("HIGH");
        assertThat(post(bundle(entry(corrected,urn())),key()).body()).contains("CORRECTION_NOT_HEAD");
        for(String change:List.of("time","analyte","encounter")) {
            ObjectNode wrong=corrected.deepCopy();correction(wrong,"Observation/"+amendedId);
            if(change.equals("time")) {wrong.put("effectiveDateTime","2026-01-01T00:00:00.000000001Z");}
            if(change.equals("analyte")) {wrong.set("code",concept("http://loinc.org","29463-7"));((ObjectNode)wrong.get("valueQuantity")).put("code","kg");}
            if(change.equals("encounter")) {wrong.remove("encounter");}
            assertThat(post(bundle(entry(wrong,urn())),key()).body()).as(change).contains("CORRECTION_CONTEXT_MISMATCH");
        }
    }

    @Test void bundleLocalCorrectionChainsSupportForwardReferencesAndRejectCyclesAndBranches() throws Exception {
        String original=urn(),child=urn();ObjectNode a=observation("Patient/"+patient,"29463-7","kg",60),b=a.deepCopy(),c=a.deepCopy();
        b.put("status","amended");correction(b,original);c.put("status","corrected");correction(c,child);
        var chain=post(bundle(entry(c,urn()),entry(b,child),entry(a,original)),key());assertThat(chain.statusCode()).isEqualTo(200);
        JsonNode ids=JSON.readTree(chain.body());assertThat(jdbc.queryForObject("SELECT correction_of FROM observation WHERE id=?",String.class,created(ids,0))).isEqualTo(created(ids,1));
        ObjectNode cycleA=b.deepCopy(),cycleB=b.deepCopy();String u=urn(),v=urn();correction(cycleA,v);correction(cycleB,u);
        int before=count("observation");assertThat(post(bundle(entry(cycleA,u),entry(cycleB,v)),key()).body()).contains("CORRECTION_CYCLE");
        String target=urn();ObjectNode branch1=b.deepCopy(),branch2=b.deepCopy();correction(branch1,target);correction(branch2,target);
        assertThat(post(bundle(entry(a,target),entry(branch1,urn()),entry(branch2,urn())),key()).body()).contains("CORRECTION_BRANCH");
        assertThat(count("observation")).isEqualTo(before);
    }

    @Test void simultaneousDifferentKeyCorrectionsProduceOneSuccessor() throws Exception {
        ObjectNode original=observation("Patient/"+patient,"29463-7","kg",60);
        String target=created(JSON.readTree(post(bundle(entry(original,urn())),key()).body()),0);
        ObjectNode corrected=original.deepCopy();corrected.put("status","amended");correction(corrected,"Observation/"+target);
        String body=bundle(entry(corrected,urn()));
        try(var gate=owner.getDataSource().getConnection();ExecutorService pool=Executors.newFixedThreadPool(2)) {
            gate.setAutoCommit(false);
            int gatePid;
            try(var query=gate.createStatement();var result=query.executeQuery("SELECT pg_backend_pid()")) {result.next();gatePid=result.getInt(1);}
            try(var lock=gate.prepareStatement("SELECT observation_id FROM observation_review WHERE observation_id=? FOR UPDATE")) {
                lock.setString(1,target);lock.executeQuery().close();
            }
            CountDownLatch start=new CountDownLatch(1);
            Callable<Integer> request=() -> {start.await();return post(body,key()).statusCode();};
            var a=pool.submit(request);var b=pool.submit(request);start.countDown();
            boolean waiting;
            try {waiting=waitForTwoReviewWaiters(gatePid);}
            finally {gate.commit();}
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,400);
            assertThat(waiting).as("Both correction plans reached the predecessor review-row lock before either committed").isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM observation WHERE correction_of=?",Integer.class,target)).isEqualTo(1);
        }
    }

    @Test void auditFailureRollsBackClinicalRowsAndReceiptAndSameKeyCanRetry() throws Exception {
        String body=bundle(entry(newPatient(),urn())),key=key();int before=count("patient");
        owner.execute("CREATE FUNCTION atlas.test_g4_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id=TG_ARGV[0] THEN RAISE EXCEPTION 'synthetic audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        owner.execute("CREATE TRIGGER test_g4_audit_failure BEFORE INSERT ON access_log FOR EACH ROW EXECUTE FUNCTION atlas.test_g4_audit_failure('"+clinician+"')");
        try {
            var failed=post(body,key);assertThat(failed.statusCode()).isEqualTo(503);assertThat(failed.body()).doesNotContain("synthetic","Exception","INSERT",clinician);
            assertThat(count("patient")).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_receipt WHERE actor_id=? AND idempotency_key=?",Integer.class,clinician,key)).isZero();
        } finally {owner.execute("DROP TRIGGER test_g4_audit_failure ON access_log");owner.execute("DROP FUNCTION atlas.test_g4_audit_failure()");}
        assertThat(post(body,key).statusCode()).isEqualTo(200);assertThat(count("patient")).isEqualTo(before+1);
    }

    @Test void unexpectedValidatorFailureDoesNotBecomeCachedInvalidInput() throws Exception {
        String body=bundle(entry(newPatient(),urn())),key=key();int before=count("patient");
        org.mockito.Mockito.doThrow(new IllegalStateException("private synthetic validation failure")).when(validator).valid(org.mockito.ArgumentMatchers.anyString());
        try {
            var failed=post(body,key);assertThat(failed.statusCode()).isEqualTo(503);assertThat(failed.body()).doesNotContain("private","validation failure","Exception");
            assertThat(count("patient")).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_receipt WHERE actor_id=? AND idempotency_key=?",Integer.class,clinician,key)).isZero();
        } finally {org.mockito.Mockito.doCallRealMethod().when(validator).valid(org.mockito.ArgumentMatchers.anyString());}
        assertThat(post(body,key).statusCode()).isEqualTo(200);
    }

    @Test void criticalParentAndComponentSignalsArePreservedAndNeverAutoReleased() throws Exception {
        ObjectNode bp=observation("Patient/"+patient,"85354-9","mm[Hg]",0);bp.remove("valueQuantity");
        ObjectNode systolic=JSON.createObjectNode();systolic.set("code",concept("http://loinc.org","8480-6"));systolic.set("valueQuantity",quantity("mm[Hg]",160));
        systolic.set("interpretation",JSON.valueToTree(List.of(concept("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation","HH"))));
        ObjectNode diastolic=JSON.createObjectNode();diastolic.set("code",concept("http://loinc.org","8462-4"));diastolic.set("valueQuantity",quantity("mm[Hg]",95));
        bp.set("component",JSON.valueToTree(List.of(systolic,diastolic)));
        var response=post(bundle(entry(bp,urn())),key());assertThat(response.statusCode()).isEqualTo(200);String observation=created(JSON.readTree(response.body()),0);
        assertThat(jdbc.queryForObject("SELECT source_critical FROM observation WHERE id=?",Boolean.class,observation)).isTrue();
        assertThat(jdbc.queryForObject("SELECT criticality='CRITICAL' AND released_at IS NULL AND reviewed_at IS NULL FROM observation_review WHERE observation_id=?",Boolean.class,observation)).isTrue();
        assertThat(jdbc.queryForObject("SELECT source_interpretation::text FROM observation WHERE id=?",String.class,observation)).contains("HH");
        JsonNode detail=JSON.readTree(get(observation,clinician,"CLINICIAN").body());
        assertThat(detail.path("flag").asText()).isEqualTo("NO_APPLICABLE_FLAG");
        assertThat(detail.at("/components/0/code").asText()).isEqualTo("8480-6");
        assertThat(detail.at("/components/0/flag").asText()).isEqualTo("HIGH");
        assertThat(detail.at("/components/0/range/low").decimalValue()).isEqualByComparingTo("90");
        assertThat(detail.at("/components/1/range/high").decimalValue()).isEqualByComparingTo("80");
        ObjectNode unavailable=observation("Patient/"+patient,"8302-2","cm",0);unavailable.remove("valueQuantity");
        unavailable.set("dataAbsentReason",concept("http://terminology.hl7.org/CodeSystem/data-absent-reason","unknown"));
        String absentId=created(JSON.readTree(post(bundle(entry(unavailable,urn())),key()).body()),0);
        JsonNode absent=JSON.readTree(get(absentId,clinician,"CLINICIAN").body());
        assertThat(absent.path("value").isNull()).isTrue();assertThat(absent.path("range").isNull()).isTrue();
        assertThat(absent.path("flag").asText()).isEqualTo("NO_APPLICABLE_FLAG");
    }

    @Test void strictTransportMalformedJsonHeadersAndEmptyTransactionsHaveSafeOutcomes() throws Exception {
        assertThat(post("{\"resourceType\":\"Bundle\",\"type\":\"transaction\"}",key()).statusCode()).isEqualTo(200);
        for(String body:List.of("{}", "{", "{} {}", "{\"resourceType\":\"Bundle\",\"type\":\"transaction\",\"entry\":[]}","{\"type\":1,\"type\":2}")) {
            var response=post(body,key());assertThat(response.statusCode()).isEqualTo(400);assertThat(response.body()).contains("OperationOutcome").doesNotContain("Exception","trace");
        }
        assertThat(send(new byte[]{(byte)0xC3,(byte)0x28},key(),clinician,"CLINICIAN","application/fhir+json").statusCode()).isEqualTo(400);
        assertThat(send("{}".getBytes(StandardCharsets.UTF_8),key(),clinician,"CLINICIAN","application/xml").statusCode()).isEqualTo(415);
        assertThat(post("{}","bad/key").statusCode()).isEqualTo(400);
        assertThat(send("{}".getBytes(StandardCharsets.UTF_8),key(),"unknown-actor","CLINICIAN","application/fhir+json").statusCode()).isEqualTo(404);
        assertThat(send("{}".getBytes(StandardCharsets.UTF_8),key(),clinician,"INVALID","application/fhir+json").statusCode()).isEqualTo(400);
        assertThat(send(new byte[FhirController.MAX_BODY_BYTES+1],key(),clinician,"CLINICIAN","application/fhir+json").statusCode()).isEqualTo(413);
    }

    @Test void allInvalidEntriesAreNamedAndRelativeAliasesResolveWithoutClientIdentityOverwrite() throws Exception {
        ObjectNode first=observation("Patient/"+patient,"39156-5","kg/m2",22);
        ObjectNode valid=observation("Patient/"+patient,"2951-2","mmol/L",140);
        ObjectNode last=observation("Patient/"+patient,"2160-0","unsupported",1);
        int before=count("observation");var failed=post(bundle(entry(first,urn()),entry(valid,urn()),entry(last,urn())),key());
        assertThat(failed.statusCode()).isEqualTo(400);JsonNode response=JSON.readTree(failed.body());
        assertThat(response.at("/entry/0/response/status").asText()).startsWith("400");
        assertThat(response.at("/entry/1/response/status").asText()).startsWith("424");
        assertThat(response.at("/entry/2/response/status").asText()).startsWith("400");assertThat(count("observation")).isEqualTo(before);
        ObjectNode p=newPatient();p.put("birthDate","2000");p.remove("extension");
        ObjectNode observation=observation("Patient/client-patient","718-7","g/dL",12);
        var success=post(bundle(entry(observation,urn()),entry(p,urn())),key());assertThat(success.statusCode()).isEqualTo(200);
        String id=created(JSON.readTree(success.body()),0);
        assertThat(jdbc.queryForObject("SELECT birth_date_context IS NULL AND sex_context='unknown' FROM observation WHERE id=?",Boolean.class,id)).isTrue();
    }

    @Test void protectedAssessmentClassesUseTheirLocalNamespaceAndRemainHeld() throws Exception {
        for(String code:List.of("ABSL","SUBU","MHEV")) {
            String e=urn();ObjectNode observation=observation("Patient/"+patient,"29463-7","kg",60);observation.set("encounter",ref(e));
            var response=post(bundle(entry(observation,urn()),entry(encounter("Patient/"+patient,code,clinician),e)),key());
            assertThat(response.statusCode()).as(code).isEqualTo(200);
            String observationId=created(JSON.readTree(response.body()),0);
            assertThat(jdbc.queryForObject("SELECT e.class FROM observation o JOIN encounter e ON e.id=o.encounter_id WHERE o.id=?",String.class,observationId)).isEqualTo(code);
            assertThat(jdbc.queryForObject("SELECT released_at IS NULL FROM observation_review WHERE observation_id=?",Boolean.class,observationId)).isTrue();
        }
    }

    @Test void chunkedBodiesDuplicateHeadersAndEntryCountAreBounded() throws Exception {
        try(HttpClient client=HttpClient.newHttpClient()) {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/fhir")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type","application/fhir+json").header("X-Test-Actor-Id",clinician).header("X-Test-Actor-Role","CLINICIAN").header("X-Idempotency-Key",key())
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(new byte[FhirController.MAX_BODY_BYTES+1]))).build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
            var duplicate=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/fhir")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type","application/fhir+json").header("X-Test-Actor-Id",clinician).header("X-Test-Actor-Id",otherClinician)
                    .header("X-Test-Actor-Role","CLINICIAN").header("X-Idempotency-Key",key()).POST(HttpRequest.BodyPublishers.ofString("{}"));
            assertThat(client.send(duplicate.build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
        }
        ObjectNode repeated=entry(newPatient(),urn());ObjectNode[] tooMany=new ObjectNode[1001];Arrays.fill(tooMany,repeated);
        var response=post(bundle(tooMany),key());assertThat(response.statusCode()).isEqualTo(400);assertThat(response.body()).contains("ENTRY_COUNT_BOUND");
    }

    @Test void secureDetailChecksAllRolesReleaseProxyExpiryAndIdenticalMissingOutcome() throws Exception {
        owner.update("UPDATE patient SET birth_date='2010-01-01' WHERE id=?",patient);
        owner.update("INSERT INTO proxy_link(patient_id,proxy_id) VALUES (?,?)",patient,proxy);
        String ordinary=created(JSON.readTree(post(bundle(entry(observation("Patient/"+patient,"29463-7","kg",60),urn())),key()).body()),0);
        var patientHeld=get(ordinary,patient,"PATIENT");assertThat(patientHeld.statusCode()).isEqualTo(404);
        assertThat(patientHeld.body()).isEqualTo(get(id(),patient,"PATIENT").body());
        assertThat(get(ordinary,otherClinician,"CLINICIAN").statusCode()).isEqualTo(404);
        assertThat(get(ordinary,staff,"STAFF").statusCode()).isEqualTo(404);
        releaseFixture(ordinary);
        assertThat(get(ordinary,patient,"PATIENT").statusCode()).isEqualTo(200);assertThat(get(ordinary,proxy,"PROXY").statusCode()).isEqualTo(200);
        String e=urn();ObjectNode protectedObservation=observation("Patient/"+patient,"29463-7","kg",61);protectedObservation.set("encounter",ref(e));
        String protectedId=created(JSON.readTree(post(bundle(entry(protectedObservation,urn()),entry(encounter("Patient/"+patient,"ABSL",clinician),e)),key()).body()),0);
        releaseFixture(protectedId);
        var hidden=get(protectedId,proxy,"PROXY");var missing=get(id(),proxy,"PROXY");
        assertThat(hidden.statusCode()).isEqualTo(404);assertThat(hidden.body()).isEqualTo(missing.body());
        assertThat(get(protectedId,patient,"PATIENT").statusCode()).isEqualTo(200);
        owner.update("UPDATE patient SET birth_date='2016-01-01' WHERE id=?",patient);
        assertThat(get(protectedId,proxy,"PROXY").statusCode()).isEqualTo(200);
        owner.update("UPDATE patient SET birth_date='2014-10-28' WHERE id=?",patient);
        assertThat(get(protectedId,proxy,"PROXY").statusCode()).isEqualTo(200); // Day before 12.
        owner.update("UPDATE patient SET birth_date='2014-10-27' WHERE id=?",patient);
        assertThat(get(protectedId,proxy,"PROXY").statusCode()).isEqualTo(404); // Exact 12th birthday.
        owner.update("UPDATE patient SET birth_date='2008-10-28' WHERE id=?",patient);
        assertThat(get(ordinary,proxy,"PROXY").statusCode()).isEqualTo(200); // Day before 18.
        owner.update("UPDATE patient SET birth_date='2008-10-27' WHERE id=?",patient);
        assertThat(get(ordinary,proxy,"PROXY").statusCode()).isEqualTo(404); // Exact 18th birthday.
        owner.update("UPDATE patient SET birth_date='2010-01-01' WHERE id=?",patient);
        owner.update("UPDATE proxy_link SET active=false,revoked_at=clock_timestamp() WHERE patient_id=? AND proxy_id=?",patient,proxy);
        assertThat(get(ordinary,proxy,"PROXY").statusCode()).isEqualTo(404);
        owner.update("UPDATE proxy_link SET active=true,revoked_at=NULL WHERE patient_id=? AND proxy_id=?",patient,proxy);
        owner.update("UPDATE patient SET birth_date='2000-01-01' WHERE id=?",patient);
        assertThat(get(ordinary,proxy,"PROXY").statusCode()).isEqualTo(404);
        owner.update("UPDATE patient SET birth_date=NULL WHERE id=?",patient);
        assertThat(get(ordinary,proxy,"PROXY").statusCode()).isEqualTo(404);
        owner.update("UPDATE patient SET birth_date='2027-01-01' WHERE id=?",patient);
        assertThat(get(ordinary,proxy,"PROXY").statusCode()).isEqualTo(404);
    }

    @Test void hiddenSuccessorMetadataAndConcealedAncestorNeverLeakThroughDetail() throws Exception {
        ObjectNode original=observation("Patient/"+patient,"2160-0","mg/dL",1);
        String originalId=created(JSON.readTree(post(bundle(entry(original,urn())),key()).body()),0);releaseFixture(originalId);
        String before=get(originalId,patient,"PATIENT").body();
        ObjectNode amended=original.deepCopy();amended.put("status","amended");correction(amended,"Observation/"+originalId);
        String amendedId=created(JSON.readTree(post(bundle(entry(amended,urn())),key()).body()),0);
        assertThat(get(originalId,patient,"PATIENT").body()).isEqualTo(before);
        assertThat(get(amendedId,patient,"PATIENT").body()).isEqualTo(get(id(),patient,"PATIENT").body());
        releaseFixture(amendedId);owner.update("UPDATE observation_review SET released_at=NULL,released_by=NULL WHERE observation_id=?",originalId);
        assertThat(get(amendedId,patient,"PATIENT").statusCode()).isEqualTo(404);
        assertThat(get(originalId,clinician,"CLINICIAN").statusCode()).isEqualTo(200);assertThat(get(amendedId,clinician,"CLINICIAN").statusCode()).isEqualTo(200);
    }

    @Test void clinicalDetailAuditFailureWithholdsContentAndKeepsExpectedDenials() throws Exception {
        String observation=created(JSON.readTree(post(bundle(entry(observation("Patient/"+patient,"2160-0","mg/dL",1),urn())),key()).body()),0);
        owner.execute("CREATE FUNCTION atlas.test_g4_detail_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id=TG_ARGV[0] AND NEW.action='RESULT_DETAIL' THEN RAISE EXCEPTION 'private synthetic read audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        owner.execute("CREATE TRIGGER test_g4_detail_audit_failure BEFORE INSERT ON access_log FOR EACH ROW EXECUTE FUNCTION atlas.test_g4_detail_audit_failure('"+clinician+"')");
        try {
            var failed=get(observation,clinician,"CLINICIAN");assertThat(failed.statusCode()).isEqualTo(503);
            assertThat(failed.body()).doesNotContain(observation,"correctionOf","value","synthetic","Exception");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE actor_id=? AND action='RESULT_DETAIL'",Integer.class,clinician)).isZero();
        } finally {owner.execute("DROP TRIGGER test_g4_detail_audit_failure ON access_log");owner.execute("DROP FUNCTION atlas.test_g4_detail_audit_failure()");}
        assertThat(get(observation,clinician,"CLINICIAN").statusCode()).isEqualTo(200);
        assertThat(get(observation,otherClinician,"CLINICIAN").statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE actor_id=? AND patient_id=? AND action='RESULT_DETAIL' AND outcome='DENIED' AND actor_roster_verified",Integer.class,otherClinician,patient)).isEqualTo(1);
    }

    @Test void sourceCriticalCannotEscapeViaInconsistentNoncriticalReleaseState() throws Exception {
        ObjectNode source=observation("Patient/"+patient,"2160-0","mg/dL",8);
        source.set("interpretation",JSON.valueToTree(List.of(concept("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation","HH"))));
        String observation=created(JSON.readTree(post(bundle(entry(source,urn())),key()).body()),0);
        releaseFixture(observation); // Deliberately inconsistent fixture: runtime workflow must not permit this.
        assertThat(get(observation,patient,"PATIENT").statusCode()).isEqualTo(404);
        var clinicianView=JSON.readTree(get(observation,clinician,"CLINICIAN").body());
        assertThat(clinicianView.path("releaseState").asText()).isEqualTo("HELD");assertThat(clinicianView.path("sourceCritical").asBoolean()).isTrue();
    }

    @Test void committedCorrectionWinsAgainstAnOverlappingFirstReleaseUsingFreshSnapshotRetry() throws Exception {
        ObjectNode source=observation("Patient/"+patient,"29463-7","kg",60);
        String original=created(JSON.readTree(post(bundle(entry(source,urn())),key()).body()),0);
        var actor=new com.aldercrest.atlas.security.TestActor(clinician,"CLINICIAN",true,false);
        assertThat(workflow.review(actor,patient,original,"NONCRITICAL").status()).isEqualTo(200);
        CountDownLatch correctionWritten=new CountDownLatch(1),allowCorrectionCommit=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger correctionPid=new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            IngestionRecords.ObservationRow row=invocation.getArgument(0);
            Object result=invocation.callRealMethod();
            if(original.equals(row.correctionOf())) {
                correctionPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                correctionWritten.countDown();
                if(!allowCorrectionCommit.await(10,TimeUnit.SECONDS)) {throw new IllegalStateException("Synthetic correction gate timed out.");}
            }
            return result;
        }).when(repository).insert(org.mockito.ArgumentMatchers.any(IngestionRecords.ObservationRow.class));
        ObjectNode corrected=source.deepCopy();corrected.put("status","corrected");correction(corrected,"Observation/"+original);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var ingest=pool.submit(() -> post(bundle(entry(corrected,urn())),key()));
            assertThat(correctionWritten.await(10,TimeUnit.SECONDS)).isTrue();
            var release=pool.submit(() -> workflow.release(actor,patient,original));
            boolean waiting=false;
            try {
                long deadline=System.nanoTime()+Duration.ofMillis(1800).toNanos();
                while(System.nanoTime()<deadline) {
                    int blocked=owner.queryForObject("""
                            SELECT count(DISTINCT a.pid) FROM pg_stat_activity a JOIN pg_locks l ON l.pid=a.pid AND NOT l.granted
                            WHERE a.datname=current_database() AND a.usename='atlas_runtime' AND ?=ANY(pg_blocking_pids(a.pid))
                            """,Integer.class,correctionPid.get());
                    if(blocked>0) {waiting=true;break;}Thread.sleep(10);
                }
            } finally {allowCorrectionCommit.countDown();}
            assertThat(waiting).as("First release actually waited behind the uncommitted correction review-row touch").isTrue();
            var response=ingest.get(20,TimeUnit.SECONDS);assertThat(response.statusCode()).isEqualTo(200);
            assertThat(release.get(20,TimeUnit.SECONDS).status()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT released_at IS NULL FROM observation_review WHERE observation_id=?",Boolean.class,original)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND action='RESULT_RELEASE' AND outcome='DENIED'",Integer.class,patient)).isEqualTo(1);
            String child=created(JSON.readTree(response.body()),0);
            assertThat(get(child,patient,"PATIENT").statusCode()).isEqualTo(404);
        } finally {allowCorrectionCommit.countDown();}
    }

    private HttpResponse<String> post(String body,String key) throws Exception {return send(body.getBytes(StandardCharsets.UTF_8),key,clinician,"CLINICIAN","application/fhir+json");}
    private HttpResponse<String> concurrentPost(String body,String key) throws Exception {
        // Specific test orchestration budget: prevalidation rendezvous plus a real 15s DB action.
        // Production request/transaction deadlines are unchanged.
        try(HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/fhir")).timeout(Duration.ofSeconds(45))
                    .header("Content-Type","application/fhir+json").header("X-Test-Actor-Id",clinician).header("X-Test-Actor-Role","CLINICIAN").header("X-Idempotency-Key",key)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    private HttpResponse<String> get(String observation,String actor,String role) throws Exception {
        try(HttpClient client=HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/patients/"+patient+"/results/"+observation)).timeout(Duration.ofSeconds(15))
                    .header("X-Test-Actor-Id",actor).header("X-Test-Actor-Role",role).GET().build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    private void releaseFixture(String observation) {
        // Test state only, not evidence that the pending clinician workflow/generator exists.
        owner.update("UPDATE observation_review SET criticality='NONCRITICAL',reviewed_by=?,reviewed_at=clock_timestamp(),released_by=?,released_at=clock_timestamp() WHERE observation_id=?",clinician,clinician,observation);
    }
    private HttpResponse<String> send(byte[] body,String key,String actor,String role,String type) throws Exception {
        try(HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/fhir")).timeout(Duration.ofSeconds(25))
                    .header("Content-Type",type).header("X-Test-Actor-Id",actor).header("X-Test-Actor-Role",role).header("X-Idempotency-Key",key)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    private static ObjectNode newPatient() {
        ObjectNode p=JSON.createObjectNode().put("resourceType","Patient").put("id","client-patient").put("birthDate","2000-01-01").put("gender","male");
        p.set("extension",JSON.valueToTree(List.of(Map.of("url",FhirDefinitions.SEX_CONTEXT,"extension",List.of(
                Map.of("url","code","valueCode","female"),Map.of("url","validFrom","valueDate","2000-01-01"))))));return p;
    }
    private static ObjectNode observation(String patient,String code,String unit,double value) {
        ObjectNode o=JSON.createObjectNode().put("resourceType","Observation").put("status","final").put("effectiveDateTime","2026-01-01T00:00:00Z");
        o.set("code",concept("http://loinc.org",code));o.set("subject",ref(patient));o.set("valueQuantity",quantity(unit,value));return o;
    }
    private static ObjectNode encounter(String patient,String encounterClass,String clinician) {
        ObjectNode e=JSON.createObjectNode().put("resourceType","Encounter").put("status","finished");
        e.set("class",JSON.valueToTree(Map.of("system",Set.of("ABSL","SUBU","MHEV").contains(encounterClass)?BundlePlanner.LOCAL_CLASS:BundlePlanner.ACT_CODE,"code",encounterClass)));
        e.set("subject",ref(patient));e.set("participant",JSON.valueToTree(List.of(Map.of("individual",ref("Practitioner/"+clinician)))));return e;
    }
    private static ObjectNode concept(String system,String code) {ObjectNode c=JSON.createObjectNode();c.set("coding",JSON.valueToTree(List.of(Map.of("system",system,"code",code))));return c;}
    private static ObjectNode ref(String reference) {return JSON.createObjectNode().put("reference",reference);}
    private static ObjectNode quantity(String unit,double value) {return JSON.createObjectNode().put("value",value).put("system","http://unitsofmeasure.org").put("code",unit);}
    private static ObjectNode entry(JsonNode resource,String fullUrl) {ObjectNode e=JSON.createObjectNode();e.set("resource",resource);e.put("fullUrl",fullUrl);e.set("request",JSON.valueToTree(Map.of("method","POST","url",resource.path("resourceType").asText())));return e;}
    private static String bundle(ObjectNode... entries) {return FhirReplies.encode(Map.of("resourceType","Bundle","type","transaction","entry",List.of(entries)));}
    private static void correction(ObjectNode observation,String reference) {observation.set("extension",JSON.valueToTree(List.of(Map.of("url",FhirDefinitions.CORRECTION,"valueReference",Map.of("reference",reference)))));}
    private static String created(JsonNode response,int index) {String location=response.at("/entry/"+index+"/response/location").asText();assertThat(location).contains("/");return location.substring(location.indexOf('/')+1);}
    private int count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private int audits(String patient,String outcome) {return jdbc.queryForObject("SELECT count(*) FROM access_log WHERE actor_id=? AND patient_id=? AND action='INGEST' AND outcome=?",Integer.class,clinician,patient,outcome);}
    private boolean waitForTwoReviewWaiters(int gatePid) throws InterruptedException {
        long deadline=System.nanoTime()+Duration.ofMillis(1800).toNanos();
        do {
            int waiting=owner.queryForObject("""
                    SELECT count(DISTINCT a.pid) FROM pg_stat_activity a JOIN pg_locks l ON l.pid=a.pid AND NOT l.granted
                    WHERE a.datname=current_database() AND a.usename='atlas_runtime'
                    AND (?=ANY(pg_blocking_pids(a.pid)) OR EXISTS
                      (SELECT 1 FROM unnest(pg_blocking_pids(a.pid)) AS blocker(pid) WHERE ?=ANY(pg_blocking_pids(blocker.pid))))
                    """,Integer.class,gatePid,gatePid);
            if(waiting>=2) {return true;}Thread.sleep(10);
        } while(System.nanoTime()<deadline);
        return false;
    }
    private boolean waitForScopedAdvisoryWaiters(int gatePid,int firstPid,int secondPid) throws InterruptedException {
        long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
        do {
            int waiting=owner.queryForObject("""
                    SELECT count(DISTINCT waiting.pid)
                    FROM pg_locks waiting JOIN pg_locks held
                      ON held.pid=? AND held.granted AND held.locktype='advisory'
                      AND held.database=waiting.database AND held.classid=waiting.classid
                      AND held.objid=waiting.objid AND held.objsubid=waiting.objsubid
                    JOIN pg_stat_activity actor ON actor.pid=waiting.pid
                    WHERE waiting.pid IN (?,?) AND NOT waiting.granted AND waiting.locktype='advisory'
                      AND waiting.mode='ExclusiveLock' AND held.mode='ExclusiveLock'
                      AND actor.datname=current_database() AND actor.usename='atlas_runtime'
                      AND waiting.database=(SELECT oid FROM pg_database WHERE datname=current_database())
                    """,Integer.class,gatePid,firstPid,secondPid);
            if(waiting==2) {return true;}Thread.sleep(10);
        } while(System.nanoTime()<deadline);
        return false;
    }
    private static String id() {return UUID.randomUUID().toString();}
    private static String key() {return "g4-"+UUID.randomUUID();}
    private static String urn() {return "urn:uuid:"+UUID.randomUUID();}
}
