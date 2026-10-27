package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.results.ResultDetailService;
import com.aldercrest.atlas.security.TestActor;
import com.aldercrest.atlas.workflow.WorkflowService;
import com.aldercrest.atlas.workflow.WorkflowService.Outcome;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Real service transactions + designated synthetic PostgreSQL16. Never fixture-release a result. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@org.springframework.context.annotation.Import(WorkflowIT.FixedClock.class)
class WorkflowIT {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        java.time.Clock assessmentClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-10-27T00:00:00Z"),java.time.ZoneOffset.UTC);
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("ATLAS_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {
            throw new IllegalStateException("Designated synthetic test DB required.");
        }
        SchemaMigration.run(System.getenv());
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkflowService workflow;
    @Autowired ResultDetailService detail;
    private JdbcTemplate owner;
    private String clinician,secondClinician,unentitledClinician,patient,otherPatient,encounter,proxy,otherProxy,staff;

    @BeforeEach void fixture() {
        owner=new JdbcTemplate(new DriverManagerDataSource(System.getenv("ATLAS_DB_URL")+"&currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"),System.getenv("ATLAS_MIGRATION_PASSWORD")));
        clinician=id();secondClinician=id();unentitledClinician=id();patient=id();otherPatient=id();
        encounter=id();proxy=id();otherProxy=id();staff=id();
        owner.update("INSERT INTO actor(id,role) VALUES (?,'CLINICIAN'),(?,'CLINICIAN'),(?,'CLINICIAN')",
                clinician,secondClinician,unentitledClinician);
        owner.update("INSERT INTO actor(id,role) VALUES (?,'PATIENT'),(?,'PATIENT'),(?,'PROXY'),(?,'PROXY'),(?,'STAFF')",
                patient,otherPatient,proxy,otherProxy,staff);
        // Under12 throughout this task's test date: both ordinary proxy links are initially usable.
        owner.update("INSERT INTO patient(id,sex,birth_date) VALUES (?,'male','2016-01-01')",patient);
        owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id,fhir_status) VALUES (?,?,'AMB',?,'finished'),(?,?,'AMB',?,'finished')",
                encounter,patient,clinician,id(),patient,secondClinician);
        owner.update("INSERT INTO proxy_link(patient_id,proxy_id) VALUES (?,?),(?,?)",patient,proxy,patient,otherProxy);
    }

    @Test void finalSourceStaysHeldUntilActualReviewThenSeparateRelease() {
        String observation=source("final",false,null);
        assertThat(readStatus(observation,self())).isEqualTo(404);
        assertThat(readStatus(observation,actor(proxy,"PROXY"))).isEqualTo(404);
        assertThat(readStatus(observation,clinician())).isEqualTo(200);
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(409);
        assertThat(workflow.review(clinician(),patient,observation,"NONCRITICAL").status()).isEqualTo(200);
        assertThat(state(observation).get("released_at")).isNull();
        assertThat(readStatus(observation,self())).isEqualTo(404);
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(200);
        assertThat(readStatus(observation,self())).isEqualTo(200);
        assertThat(readStatus(observation,actor(proxy,"PROXY"))).isEqualTo(200);
        assertThat(state(observation)).containsEntry("reviewed_by",clinician).containsEntry("released_by",clinician);
        assertThat(audits("CLINICIAN_REVIEW","ALLOWED")).isEqualTo(1);
        assertThat(audits("RESULT_RELEASE","ALLOWED")).isEqualTo(1);
        assertThat(audits("RESULT_RELEASE","DENIED")).isEqualTo(1);
    }

    @Test void immutableSourceCriticalFloorNeedsExplicitReviewAttestationAndSeparateRelease() {
        // IngestionIT independently checks parent/component HH/LL/AA set this immutable source flag.
        String observation=source("final",true,null);
        assertThat(workflow.review(clinician(),patient,observation,"NONCRITICAL").status()).isEqualTo(409);
        assertThat(workflow.attestCritical(clinician(),patient,observation,true).status()).isEqualTo(409);
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(409);
        assertThat(workflow.review(clinician(),patient,observation,"CRITICAL").status()).isEqualTo(200);
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(409);
        assertThat(workflow.attestCritical(clinician(),patient,observation,false).status()).isEqualTo(400);
        assertThat(workflow.attestCritical(clinician(),patient,observation,true).status()).isEqualTo(200);
        assertThat(readStatus(observation,self())).isEqualTo(404);
        assertThat(state(observation).get("released_at")).isNull();
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(200);
        assertThat(readStatus(observation,self())).isEqualTo(200);
        assertThat(state(observation)).containsEntry("criticality","CRITICAL").containsEntry("critical_path_by",clinician);
    }

    @Test void onlyFinalAmendedCorrectedSourceStatusesCanRelease() {
        for(String status:List.of("registered","preliminary","final","amended","corrected","cancelled","entered-in-error","unknown")) {
            String previous=List.of("amended","corrected").contains(status)?source("final",false,null):null;
            String observation=source(status,false,previous);
            assertThat(workflow.review(clinician(),patient,observation,"NONCRITICAL").status()).as(status).isEqualTo(200);
            boolean eligible=List.of("final","amended","corrected").contains(status);
            assertThat(workflow.release(clinician(),patient,observation).status()).as(status).isEqualTo(eligible?200:409);
            assertThat(state(observation).get("released_at")!=null).as(status).isEqualTo(eligible);
        }
    }

    @Test void onlyKnownEntitledClinicianCanActAndAllOrdinaryDenialsCommit() {
        String observation=source("final",false,null);
        List<TestActor> denied=List.of(self(),actor(proxy,"PROXY"),actor(staff,"STAFF"),actor(unentitledClinician,"CLINICIAN"),
                actor(id(),"CLINICIAN"),actor(clinician,"PATIENT"));
        for(TestActor caller:denied) {
            Outcome response=workflow.review(caller,patient,observation,"NONCRITICAL");
            assertThat(response.status()).isEqualTo(404);assertThat(response.code()).isEqualTo("NOT_AVAILABLE");
        }
        assertThat(workflow.review(TestActor.unverified(),patient,observation,"NONCRITICAL").status()).isEqualTo(400);
        assertThat(workflow.review(clinician(),patient,id(),"NONCRITICAL").status()).isEqualTo(404);
        assertThat(workflow.review(clinician(),id(),observation,"NONCRITICAL").status()).isEqualTo(404);
        assertThat(workflow.review(clinician(),patient,observation,"private-invalid-marker").status()).isEqualTo(400);
        assertThat(state(observation).get("reviewed_at")).isNull();
        assertThat(audits("CLINICIAN_REVIEW","DENIED")).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND action='CLINICIAN_REVIEW' AND actor_id IS NULL AND NOT actor_roster_verified",
                Integer.class,patient)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND action='CLINICIAN_REVIEW' AND actor_id=? AND actor_role='PATIENT' AND NOT actor_roster_verified",
                Integer.class,patient,clinician)).isEqualTo(1);
        assertThat(workflow.review(actor(secondClinician,"CLINICIAN"),patient,observation,"NONCRITICAL").status()).isEqualTo(200);
    }

    @Test void repeatedActionsPreserveOriginalActorsTimesAndConflictingReviewDoesNotOverwrite() {
        String observation=source("final",false,null);
        assertThat(workflow.review(clinician(),patient,observation,"CRITICAL").status()).isEqualTo(200);
        Map<String,Object> reviewed=state(observation);
        assertThat(workflow.review(actor(secondClinician,"CLINICIAN"),patient,observation,"CRITICAL").status()).isEqualTo(200);
        assertThat(workflow.review(actor(secondClinician,"CLINICIAN"),patient,observation,"NONCRITICAL").status()).isEqualTo(409);
        assertThat(state(observation)).isEqualTo(reviewed);
        assertThat(workflow.attestCritical(actor(secondClinician,"CLINICIAN"),patient,observation,true).status()).isEqualTo(200);
        Map<String,Object> attested=state(observation);
        assertThat(workflow.attestCritical(clinician(),patient,observation,true).status()).isEqualTo(200);
        assertThat(state(observation)).isEqualTo(attested);
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(200);
        Map<String,Object> released=state(observation);
        assertThat(workflow.release(actor(secondClinician,"CLINICIAN"),patient,observation).status()).isEqualTo(200);
        assertThat(workflow.review(clinician(),patient,observation,"NONCRITICAL").status()).isEqualTo(409);
        assertThat(state(observation)).isEqualTo(released);
        assertThat(released).containsEntry("reviewed_by",clinician).containsEntry("critical_path_by",secondClinician).containsEntry("released_by",clinician);
        assertThat(audits("CLINICIAN_REVIEW","ALLOWED")).isEqualTo(2);
        assertThat(audits("CLINICIAN_REVIEW","DENIED")).isEqualTo(2);
        assertThat(audits("CRITICAL_PATH_ATTEST","ALLOWED")).isEqualTo(2);
        assertThat(audits("RESULT_RELEASE","ALLOWED")).isEqualTo(2);
    }

    @Test void heldSupersededOriginalCannotBeReleasedToUnblockCorrectionButReleasedHistoryRemains() {
        String held=source("final",false,null);
        assertThat(workflow.review(clinician(),patient,held,"NONCRITICAL").status()).isEqualTo(200);
        String heldChild=source("amended",false,held);
        assertThat(workflow.release(clinician(),patient,held).status()).isEqualTo(409);
        assertThat(state(held).get("released_at")).isNull();
        assertThat(workflow.review(clinician(),patient,heldChild,"NONCRITICAL").status()).isEqualTo(200);
        assertThat(workflow.release(clinician(),patient,heldChild).status()).isEqualTo(200);
        assertThat(readStatus(heldChild,self())).as("Complete visible ancestry still required").isEqualTo(404);

        String released=source("final",false,null);
        assertThat(workflow.review(clinician(),patient,released,"NONCRITICAL").status()).isEqualTo(200);
        assertThat(workflow.release(clinician(),patient,released).status()).isEqualTo(200);
        Map<String,Object> original=state(released);
        String child=source("corrected",false,released);
        assertThat(workflow.release(actor(secondClinician,"CLINICIAN"),patient,released).status()).isEqualTo(200);
        assertThat(state(released)).isEqualTo(original);
        assertThat(readStatus(released,self())).isEqualTo(200);
        assertThat(readStatus(child,self())).isEqualTo(404);
        assertThat(workflow.review(clinician(),patient,child,"NONCRITICAL").status()).isEqualTo(200);
        assertThat(workflow.release(clinician(),patient,child).status()).isEqualTo(200);
        assertThat(readStatus(released,self())).isEqualTo(200);assertThat(readStatus(child,self())).isEqualTo(200);
    }

    @Test void patientRevokesOneIndividualProxyWithTargetAttributionAndSafeRepetition() {
        String observation=source("final",false,null);
        assertThat(workflow.review(clinician(),patient,observation,"NONCRITICAL").status()).isEqualTo(200);
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(200);
        assertThat(readStatus(observation,actor(proxy,"PROXY"))).isEqualTo(200);
        assertThat(readStatus(observation,actor(otherProxy,"PROXY"))).isEqualTo(200);
        assertThat(workflow.revokeProxy(self(),patient,proxy).status()).isEqualTo(200);
        Map<String,Object> revoked=link(proxy);
        assertThat(revoked).containsEntry("active",false).containsEntry("revoked_by",patient);
        assertThat(revoked.get("revoked_at")).isNotNull();
        assertThat(readStatus(observation,actor(proxy,"PROXY"))).isEqualTo(404);
        assertThat(readStatus(observation,actor(otherProxy,"PROXY"))).isEqualTo(200);
        assertThat(workflow.revokeProxy(self(),patient,proxy).status()).isEqualTo(200);
        assertThat(link(proxy)).isEqualTo(revoked);assertThat(link(otherProxy)).containsEntry("active",true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND actor_id=? AND action='PROXY_REVOKE' AND outcome='ALLOWED' AND source='LOCAL_COMMAND' AND target_proxy_id=? AND actor_roster_verified",
                Integer.class,patient,patient,proxy)).isEqualTo(2);
        for(TestActor caller:List.of(clinician(),actor(staff,"STAFF"),actor(proxy,"PROXY"),actor(otherPatient,"PATIENT"))) {
            assertThat(workflow.revokeProxy(caller,patient,otherProxy).status()).isEqualTo(404);
        }
        assertThat(workflow.revokeProxy(self(),patient,id()).status()).isEqualTo(404);
        assertThat(link(otherProxy)).containsEntry("active",true).containsEntry("revoked_by",null);
        assertThat(audits("PROXY_REVOKE","DENIED")).isEqualTo(5);
    }

    @Test void auditInsertAndDeferredCommitFailuresRollbackEveryWorkflowMutation() {
        for(boolean deferred:List.of(false,true)) {
            String review=source("final",false,null);
            assertRollback("CLINICIAN_REVIEW",deferred,()->workflow.review(clinician(),patient,review,"NONCRITICAL"),()->state(review));

            String critical=source("final",true,null);
            assertThat(workflow.review(clinician(),patient,critical,"CRITICAL").status()).isEqualTo(200);
            assertRollback("CRITICAL_PATH_ATTEST",deferred,()->workflow.attestCritical(clinician(),patient,critical,true),()->state(critical));

            String release=source("final",false,null);
            assertThat(workflow.review(clinician(),patient,release,"NONCRITICAL").status()).isEqualTo(200);
            assertRollback("RESULT_RELEASE",deferred,()->workflow.release(clinician(),patient,release),()->state(release));
            assertThat(readStatus(release,self())).isEqualTo(404);

            assertRollback("PROXY_REVOKE",deferred,()->workflow.revokeProxy(self(),patient,proxy),()->link(proxy));
            assertThat(link(proxy)).containsEntry("active",true).containsEntry("revoked_by",null);
        }
    }

    @Test void mutableWorkflowGrantDoesNotPermitClinicalAuditOrLinkIdentityMutation() {
        String observation=source("final",false,null);
        assertThatThrownBy(()->jdbc.update("UPDATE observation SET value_num=1 WHERE id=?",observation)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM observation WHERE id=?",observation)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE access_log SET actor_id=? WHERE patient_id=?",secondClinician,patient)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM access_log WHERE patient_id=?",patient)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE observation_review SET observation_id=observation_id WHERE observation_id=?",observation)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE proxy_link SET proxy_id=proxy_id WHERE patient_id=? AND proxy_id=?",patient,proxy)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("CREATE TABLE atlas.test_g5_forbidden(id integer)")).isInstanceOf(DataAccessException.class);
    }

    @Test void overlappingIdenticalReviewsWaitInPostgresThenPreserveSingleClassification() throws Exception {
        String observation=source("final",false,null);
        List<Outcome> results=contend(observation,()->workflow.review(clinician(),patient,observation,"NONCRITICAL"),
                ()->workflow.review(actor(secondClinician,"CLINICIAN"),patient,observation,"NONCRITICAL"));
        assertThat(results).extracting(Outcome::status).containsExactly(200,200);
        assertThat(state(observation).get("reviewed_by")).isIn(clinician,secondClinician);
        assertThat(state(observation)).containsEntry("criticality","NONCRITICAL");
        assertThat(audits("CLINICIAN_REVIEW","ALLOWED")).isEqualTo(2);
    }

    @Test void overlappingConflictingReviewsHaveOneWinnerAndOneAuditedConflict() throws Exception {
        String observation=source("final",false,null);
        List<Outcome> results=contend(observation,()->workflow.review(clinician(),patient,observation,"NONCRITICAL"),
                ()->workflow.review(actor(secondClinician,"CLINICIAN"),patient,observation,"CRITICAL"));
        assertThat(results).extracting(Outcome::status).containsExactlyInAnyOrder(200,409);
        Map<String,Object> recorded=state(observation);
        assertThat(recorded).containsEntry("criticality",results.getFirst().status()==200?"NONCRITICAL":"CRITICAL")
                .containsEntry("reviewed_by",results.getFirst().status()==200?clinician:secondClinician);
        assertThat(audits("CLINICIAN_REVIEW","ALLOWED")).isEqualTo(1);
        assertThat(audits("CLINICIAN_REVIEW","DENIED")).isEqualTo(1);
    }

    @Test void overlappingReleasesCommitOnceAndBothCompletedActionsAreAudited() throws Exception {
        String observation=source("final",false,null);
        assertThat(workflow.review(clinician(),patient,observation,"NONCRITICAL").status()).isEqualTo(200);
        List<Outcome> results=contend(observation,()->workflow.release(clinician(),patient,observation),
                ()->workflow.release(actor(secondClinician,"CLINICIAN"),patient,observation));
        assertThat(results).extracting(Outcome::status).containsExactly(200,200);
        Map<String,Object> recorded=state(observation);
        assertThat(recorded.get("released_at")).isNotNull();assertThat(recorded.get("released_by")).isIn(clinician,secondClinician);
        assertThat(workflow.release(clinician(),patient,observation).status()).isEqualTo(200);
        assertThat(state(observation)).isEqualTo(recorded);
        assertThat(audits("RESULT_RELEASE","ALLOWED")).isEqualTo(3);
    }

    private List<Outcome> contend(String observation,Callable<Outcome> first,Callable<Outcome> second) throws Exception {
        try(Connection gate=owner.getDataSource().getConnection();ExecutorService pool=Executors.newFixedThreadPool(2)) {
            gate.setAutoCommit(false);
            int gatePid;
            try(var pid=gate.createStatement();var result=pid.executeQuery("SELECT pg_backend_pid()")) {
                result.next();gatePid=result.getInt(1);
            }
            try(var lock=gate.prepareStatement("SELECT observation_id FROM observation_review WHERE observation_id=? FOR UPDATE")) {
                lock.setString(1,observation);lock.executeQuery().close();
            }
            CountDownLatch start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return first.call();});
            var b=pool.submit(()->{start.await();return second.call();});start.countDown();
            boolean bothWaiting;
            try {bothWaiting=twoActualDatabaseWaiters(gatePid);} finally {gate.commit();}
            Outcome one=a.get(20,TimeUnit.SECONDS),two=b.get(20,TimeUnit.SECONDS);
            assertThat(bothWaiting).as("Both actions reached UPDATE and had ungranted PostgreSQL locks before the owner gate opened").isTrue();
            return List.of(one,two);
        }
    }
    private boolean twoActualDatabaseWaiters(int gatePid) throws InterruptedException {
        long deadline=System.nanoTime()+Duration.ofMillis(1800).toNanos();
        do {
            int count=owner.queryForObject("""
                    SELECT count(DISTINCT a.pid) FROM pg_stat_activity a JOIN pg_locks l ON l.pid=a.pid AND NOT l.granted
                    WHERE a.datname=current_database() AND a.usename='atlas_runtime'
                    AND (? = ANY(pg_blocking_pids(a.pid)) OR EXISTS
                        (SELECT 1 FROM unnest(pg_blocking_pids(a.pid)) AS first_blocker(pid)
                         WHERE ? = ANY(pg_blocking_pids(first_blocker.pid))))
                    """,Integer.class,gatePid,gatePid);
            if(count>=2) {return true;}Thread.sleep(10);
        } while(System.nanoTime()<deadline);
        return false;
    }

    private void assertRollback(String action,boolean deferred,Callable<Outcome> call,Callable<Map<String,Object>> read) {
        try {
            Map<String,Object> before=read.call();int events=audits(action,"ALLOWED");
            installAuditFault(action,deferred);
            try {
                Outcome result=call.call();
                assertThat(result.status()).as(action+" deferred="+deferred).isEqualTo(503);
                assertThat(result.code()).isEqualTo("SERVICE_UNAVAILABLE");
                assertThat(read.call()).isEqualTo(before);
                assertThat(audits(action,"ALLOWED")).isEqualTo(events);
            } finally {removeAuditFault();}
        } catch(RuntimeException error) {throw error;}
        catch(Exception error) {throw new IllegalStateException("Synthetic test callback failed.",error);}
    }
    private void installAuditFault(String action,boolean deferred) {
        owner.execute("""
                CREATE FUNCTION atlas.test_g5_workflow_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.patient_id=TG_ARGV[0] AND NEW.action=TG_ARGV[1] THEN
                    RAISE EXCEPTION 'synthetic audit failure' USING ERRCODE='23514';
                END IF; RETURN NEW; END $$
                """);
        // Values are generated UUID and a fixed test-owned action enum, never caller input.
        String kind=deferred?"CREATE CONSTRAINT TRIGGER test_g5_workflow_audit_failure AFTER INSERT ON access_log DEFERRABLE INITIALLY DEFERRED"
                :"CREATE TRIGGER test_g5_workflow_audit_failure BEFORE INSERT ON access_log";
        try {owner.execute(kind+" FOR EACH ROW EXECUTE FUNCTION atlas.test_g5_workflow_audit_failure('"+patient+"','"+action+"')");}
        catch(RuntimeException error) {owner.execute("DROP FUNCTION atlas.test_g5_workflow_audit_failure()");throw error;}
    }
    private void removeAuditFault() {
        try {owner.execute("DROP TRIGGER test_g5_workflow_audit_failure ON access_log");}
        finally {owner.execute("DROP FUNCTION atlas.test_g5_workflow_audit_failure()");}
    }
    private String source(String status,boolean critical,String correctionOf) {
        String observation=id();
        owner.update("""
                INSERT INTO observation(id,patient_id,encounter_id,code,unit_code,value_num,effective,status,correction_of,
                    birth_date_context,sex_context,source_critical)
                VALUES (?,?,?,'29463-7','kg',60,'2026-01-01T00:00:00Z',?,?,'2016-01-01','male',?)
                """,observation,patient,encounter,status,correctionOf,critical);
        owner.update("INSERT INTO observation_review(observation_id,criticality) VALUES (?,?)",observation,critical?"CRITICAL":"UNKNOWN");
        return observation;
    }
    private Map<String,Object> state(String observation) {return jdbc.queryForMap("SELECT * FROM observation_review WHERE observation_id=?",observation);}
    private Map<String,Object> link(String target) {return jdbc.queryForMap("SELECT * FROM proxy_link WHERE patient_id=? AND proxy_id=?",patient,target);}
    private int readStatus(String observation,TestActor actor) {return detail.detail(actor,patient,observation).getStatusCode().value();}
    private int audits(String action,String outcome) {return jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND action=? AND outcome=? AND source='LOCAL_COMMAND'",Integer.class,patient,action,outcome);}
    private TestActor clinician() {return actor(clinician,"CLINICIAN");}
    private TestActor self() {return actor(patient,"PATIENT");}
    private static TestActor actor(String id,String role) {return new TestActor(id,role,true,false);}
    private static String id() {return UUID.randomUUID().toString();}
}
