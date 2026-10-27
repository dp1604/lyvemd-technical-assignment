package com.aldercrest.atlas.results;

import com.aldercrest.atlas.http.SafeErrors;
import com.aldercrest.atlas.ingest.IngestionRepository;
import com.aldercrest.atlas.persistence.AuditWriter;
import com.aldercrest.atlas.persistence.RequestTransaction;
import com.aldercrest.atlas.security.TestActor;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** All four GET paths share identity, committed audit, generic denials and failure semantics. */
@Component
public class AuditedReadAction {
    private final RequestTransaction transaction;
    private final IngestionRepository roster;
    private final AuditWriter audit;
    public AuditedReadAction(RequestTransaction transaction,IngestionRepository roster,AuditWriter audit) {this.transaction=transaction;this.roster=roster;this.audit=audit;}
    public ResponseEntity<?> execute(TestActor declared,String patientId,String action,Function<TestActor,ResponseEntity<?>> read) {
        return transaction.execute(() -> {
            TestActor actor=declared;ResponseEntity<?> response;
            if(!declared.valid()) {response=SafeErrors.response(400);}
            else if(!roster.known(declared)) {response=SafeErrors.response(404);}
            else {
                actor=declared.withVerifiedRoster();
                if(!validId(patientId)) {response=SafeErrors.response(404);}
                else {
                    try {response=read.apply(actor);}
                    catch(ReadQueries.InvalidQuery error) {response=SafeErrors.response(400);}
                }
            }
            audit.write(actor,patientId,action,response.getStatusCode().is2xxSuccessful()?"ALLOWED":"DENIED","HTTP");
            return response; // Both body and audit finish inside the action; commit precedes HTTP.
        });
    }
    public static boolean validId(String id) {return id!=null && id.matches("[A-Za-z0-9.-]{1,64}");}
}
