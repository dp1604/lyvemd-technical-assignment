package com.aldercrest.atlas.workflow;

import com.aldercrest.atlas.ingest.IngestionRepository;
import com.aldercrest.atlas.persistence.AuditWriter;
import com.aldercrest.atlas.persistence.RequestTransaction;
import com.aldercrest.atlas.results.ClinicalViewRepository;
import com.aldercrest.atlas.security.TestActor;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/** Bounded C1/C2 assessment actions. No clinical payload or free text is returned. */
@Service
public class WorkflowService {
    public record Outcome(int status,String code) { }
    private record State(String status,boolean sourceCritical,String criticality,
            boolean reviewed,boolean attested,boolean released,boolean hasSuccessor) { }
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(WorkflowService.class);
    private final RequestTransaction transaction;
    private final IngestionRepository roster;
    private final ClinicalViewRepository rights;
    private final AuditWriter audit;
    private final JdbcTemplate jdbc;

    public WorkflowService(RequestTransaction transaction,IngestionRepository roster,ClinicalViewRepository rights,
            AuditWriter audit,JdbcTemplate jdbc) {
        this.transaction=transaction;this.roster=roster;this.rights=rights;this.audit=audit;this.jdbc=jdbc;
    }

    public Outcome review(TestActor actor,String patientId,String observationId,String classification) {
        return execute(actor,patientId,null,"CLINICIAN_REVIEW",known -> {
            if(!Set.of("NONCRITICAL","CRITICAL").contains(classification==null?"":classification)) {return invalid();}
            State state=state(known,patientId,observationId);
            if(state==null) {return unavailable();}
            if(state.sourceCritical() && !"CRITICAL".equals(classification)) {return conflict();}
            if(state.reviewed()) {return classification.equals(state.criticality())?completed():conflict();}
            if(state.released() || state.attested()) {return conflict();}
            int changed=jdbc.update("""
                    UPDATE observation_review r SET criticality=?,reviewed_by=?,reviewed_at=clock_timestamp()
                    FROM observation o WHERE o.id=r.observation_id AND o.id=? AND o.patient_id=?
                    AND r.reviewed_at IS NULL AND r.released_at IS NULL AND r.critical_path_at IS NULL
                    AND (NOT o.source_critical OR ?='CRITICAL')
                    """,classification,known.id(),observationId,patientId,classification);
            return changed==1?completed():conflict();
        });
    }

    public Outcome attestCritical(TestActor actor,String patientId,String observationId,boolean confirmed) {
        return execute(actor,patientId,null,"CRITICAL_PATH_ATTEST",known -> {
            if(!confirmed) {return invalid();}
            State state=state(known,patientId,observationId);
            if(state==null) {return unavailable();}
            if(!state.reviewed() || !"CRITICAL".equals(state.criticality())) {return conflict();}
            if(state.attested()) {return completed();}
            if(state.released()) {return conflict();}
            int changed=jdbc.update("""
                    UPDATE observation_review r SET critical_path_by=?,critical_path_at=clock_timestamp()
                    FROM observation o WHERE o.id=r.observation_id AND o.id=? AND o.patient_id=?
                    AND r.reviewed_at IS NOT NULL AND r.criticality='CRITICAL'
                    AND r.critical_path_at IS NULL AND r.released_at IS NULL
                    """,known.id(),observationId,patientId);
            return changed==1?completed():conflict();
        });
    }

    public Outcome release(TestActor actor,String patientId,String observationId) {
        return execute(actor,patientId,null,"RESULT_RELEASE",known -> {
            State state=state(known,patientId,observationId);
            if(state==null) {return unavailable();}
            if(!List.of("final","amended","corrected").contains(state.status()) || !state.reviewed()
                    || !List.of("NONCRITICAL","CRITICAL").contains(state.criticality())
                    || (state.sourceCritical() && !"CRITICAL".equals(state.criticality()))
                    || ("CRITICAL".equals(state.criticality()) && !state.attested())) {return conflict();}
            // Existing released history remains released when a later correction arrives.
            if(state.released()) {return completed();}
            if(state.hasSuccessor()) {return conflict();}
            int changed=jdbc.update("""
                    UPDATE observation_review r SET released_by=?,released_at=clock_timestamp()
                    FROM observation o WHERE o.id=r.observation_id AND o.id=? AND o.patient_id=?
                    AND o.status IN ('final','amended','corrected')
                    AND r.reviewed_at IS NOT NULL AND r.released_at IS NULL
                    AND r.criticality IN ('NONCRITICAL','CRITICAL')
                    AND (r.criticality<>'CRITICAL' OR r.critical_path_at IS NOT NULL)
                    AND (NOT o.source_critical OR r.criticality='CRITICAL')
                    AND NOT EXISTS(SELECT 1 FROM observation child WHERE child.correction_of=o.id)
                    """,known.id(),observationId,patientId);
            return changed==1?completed():conflict();
        });
    }

    public Outcome revokeProxy(TestActor actor,String patientId,String proxyId) {
        return execute(actor,patientId,proxyId,"PROXY_REVOKE",known -> {
            if(!"PATIENT".equals(known.role()) || !patientId.equals(known.id()) || !validActorId(proxyId)) {return unavailable();}
            List<Boolean> active=jdbc.query("SELECT active FROM proxy_link WHERE patient_id=? AND proxy_id=?",
                    (rs,n)->rs.getBoolean(1),patientId,proxyId);
            if(active.isEmpty()) {return unavailable();}
            if(!active.getFirst()) {return completed();}
            int changed=jdbc.update("""
                    UPDATE proxy_link SET active=false,revoked_at=clock_timestamp(),revoked_by=?
                    WHERE patient_id=? AND proxy_id=? AND active AND revoked_at IS NULL
                    """,known.id(),patientId,proxyId);
            return changed==1?completed():conflict();
        });
    }

    private State state(TestActor actor,String patientId,String observationId) {
        if(!"CLINICIAN".equals(actor.role()) || !validResourceId(observationId)) {return null;}
        var access=rights.access(patientId,actor);
        if(access==null || !access.clinician()) {return null;}
        return jdbc.query("""
                SELECT o.status,o.source_critical,r.criticality,
                    r.reviewed_at IS NOT NULL AS reviewed,r.critical_path_at IS NOT NULL AS attested,
                    r.released_at IS NOT NULL AS released,
                    EXISTS(SELECT 1 FROM observation child WHERE child.correction_of=o.id) AS has_successor
                FROM observation o JOIN observation_review r ON r.observation_id=o.id
                WHERE o.id=? AND o.patient_id=?
                """,(rs,n)->new State(rs.getString("status"),rs.getBoolean("source_critical"),rs.getString("criticality"),
                rs.getBoolean("reviewed"),rs.getBoolean("attested"),rs.getBoolean("released"),rs.getBoolean("has_successor")),
                observationId,patientId).stream().findFirst().orElse(null);
    }

    private Outcome execute(TestActor declared,String patientId,String proxyId,String action,Function<TestActor,Outcome> body) {
        TestActor claim=safeClaim(declared);
        for(int attempt=0;attempt<3;attempt++) {
            try {
                return transaction.execute(() -> {
                    TestActor actor=claim;Outcome result;
                    if(!claim.valid()) {result=invalid();}
                    else if(!roster.known(claim)) {result=unavailable();}
                    else {
                        actor=claim.withVerifiedRoster();
                        result=validResourceId(patientId)?body.apply(actor):unavailable();
                    }
                    audit.write(actor,patientId,action,result.status()==200?"ALLOWED":"DENIED","LOCAL_COMMAND",proxyId);
                    return result; // Complete state+audit commit precedes the caller seeing success.
                });
            } catch(DataAccessException | TransactionException error) {
                if(attempt<2 && retryable(error)) {continue;}
                LOG.warn("ATLAS_DATABASE_ACTION_FAILED");
                return new Outcome(503,"SERVICE_UNAVAILABLE");
            }
        }
        throw new IllegalStateException("Unreachable retry state.");
    }

    private static TestActor safeClaim(TestActor actor) {
        if(actor==null || !actor.valid() || !validActorId(actor.id())
                || !Set.of("PATIENT","PROXY","CLINICIAN","STAFF").contains(actor.role()==null?"":actor.role())) {
            return TestActor.unverified();
        }
        return new TestActor(actor.id(),actor.role(),true,false);
    }
    private static boolean validResourceId(String value) {return value!=null && value.matches("[A-Za-z0-9.-]{1,64}");}
    private static boolean validActorId(String value) {return value!=null && value.matches("[A-Za-z0-9._-]{1,64}");}
    private static Outcome completed() {return new Outcome(200,"ACTION_COMPLETED");}
    private static Outcome invalid() {return new Outcome(400,"INVALID_ACTION_INPUT");}
    private static Outcome unavailable() {return new Outcome(404,"NOT_AVAILABLE");}
    private static Outcome conflict() {return new Outcome(409,"ACTION_CONFLICT");}
    private static boolean retryable(Throwable error) {
        for(Throwable cursor=error;cursor!=null;cursor=cursor.getCause()) {
            if(cursor instanceof SQLException sql && List.of("40001","40P01").contains(sql.getSQLState())) {return true;}
        }
        return false;
    }
}
