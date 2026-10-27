package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.persistence.AuditWriter;
import com.aldercrest.atlas.persistence.RequestTransaction;
import com.aldercrest.atlas.security.TestActor;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(IngestionService.class);
    private final RequestTransaction transaction;
    private final IngestionRepository repository;
    private final BundlePlanner planner;
    private final AuditWriter audit;
    private record Prepared(com.fasterxml.jackson.databind.JsonNode json,BundlePlanner.StructuralChecks structural,boolean invalidJson) { }
    public IngestionService(RequestTransaction transaction,IngestionRepository repository,BundlePlanner planner,AuditWriter audit) {
        this.transaction=transaction;this.repository=repository;this.planner=planner;this.audit=audit;
    }
    public IngestReply ingest(TestActor declared,String key,byte[] body,int transportStatus) {
        // This stage reads only the bounded caller-supplied bytes and bundled local definitions.
        // It holds no database connection/snapshot/lock. Its outcome is disclosed only after the
        // ordinary stored-roster and durable-receipt checks inside the action transaction.
        Prepared prepared=declared.valid() && key!=null && transportStatus==0?prepare(body):null;
        for(int attempt=0;attempt<3;attempt++) {
            try {return transaction.execute(() -> action(declared,key,body,transportStatus,prepared));}
            catch(DataAccessException error) {
                // REPEATABLE READ snapshots may predate a competing receipt/correction commit even after
                // waiting on the advisory lock. Roll back EVERYTHING and acquire a new transaction/snapshot.
                if(attempt<2 && retryable(error)) {continue;}
                LOG.warn("ATLAS_DATABASE_ACTION_FAILED"); // Never attach SQL, actor, body, patient or exception.
                return FhirReplies.error(503,"SERVICE_UNAVAILABLE");
            }
        }
        throw new IllegalStateException("Unreachable retry state.");
    }
    private Prepared prepare(byte[] body) {
        try {
            String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            var input=IngestionJson.mapper().readTree(json);
            return new Prepared(input,planner.prevalidate(input),false);
        } catch(java.io.IOException error) {return new Prepared(null,null,true);}
        catch(RuntimeException error) {
            // Preserve roster/replay precedence even if a future local validator fails before
            // producing its bounded outcome. Never publish an exception or cache this failure.
            return new Prepared(null,new BundlePlanner.StructuralChecks(java.util.Set.of(),false),false);
        }
    }
    private IngestReply action(TestActor declared,String key,byte[] body,int transportStatus,Prepared prepared) {
        if(!declared.valid() || key==null) {return denied(declared,400,"INVALID_TEST_HEADERS_OR_KEY");}
        if(!repository.known(declared)) {return denied(declared,404,"NOT_AVAILABLE");}
        TestActor actor=declared.withVerifiedRoster();
        if(!"CLINICIAN".equals(actor.role())) {return denied(actor,404,"NOT_AVAILABLE");}
        if(transportStatus!=0) {return denied(actor,transportStatus,"INVALID_REQUEST_TRANSPORT");}
        String hash=hash(body);
        repository.lockKey(actor,key);
        var receipt=repository.receipt(actor,key);
        if(receipt!=null) {
            boolean same=receipt.hash().equals(hash);
            record(actor,receipt.patients(),same && receipt.reply().status()==200?"ALLOWED":"DENIED");
            return same?receipt.reply():FhirReplies.error(409,"IDEMPOTENCY_KEY_CONFLICT");
        }
        IngestionRecords.Planned plan;
        if(prepared.invalidJson()) {
            plan=new IngestionRecords.Planned(FhirReplies.error(400,"INVALID_JSON_OR_UTF8"),List.of(),List.of(),List.of(),List.of(),false);
        } else if(!prepared.structural().available()) {
            LOG.warn("ATLAS_FHIR_VALIDATION_UNAVAILABLE");
            return denied(actor,503,"SERVICE_UNAVAILABLE"); // Transient outcome is never saved as a receipt.
        } else {plan=planner.plan(prepared.json(),actor,prepared.structural());}
        if(plan.accepted()) {
            plan.patients().forEach(repository::insert);
            plan.encounters().forEach(repository::insert);
            plan.observations().forEach(repository::insert);
        }
        record(actor,plan.affectedPatients(),plan.accepted()?"ALLOWED":"DENIED");
        repository.saveReceipt(actor,key,hash,plan.reply(),plan.affectedPatients());
        return plan.reply(); // RequestTransaction commits before this value reaches the controller.
    }
    private IngestReply denied(TestActor actor,int status,String code) {
        record(actor,List.of(),"DENIED");return FhirReplies.error(status,code);
    }
    private void record(TestActor actor,List<String> patients,String outcome) {
        if(patients.isEmpty()) {audit.write(actor,null,"INGEST",outcome,"HTTP");}
        else {patients.forEach(patient -> audit.write(actor,patient,"INGEST",outcome,"HTTP"));}
    }
    private static boolean retryable(Throwable error) {
        for(Throwable cursor=error;cursor!=null;cursor=cursor.getCause()) {
            if(cursor instanceof SQLException sql && List.of("40001","40P01","23505").contains(sql.getSQLState())) {return true;}
        }
        return false;
    }
    private static String hash(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException error) {throw new IllegalStateException("Required SHA-256 unavailable.");}
    }
}
