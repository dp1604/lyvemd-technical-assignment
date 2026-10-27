package com.aldercrest.atlas.results;

import com.aldercrest.atlas.http.SafeErrors;
import com.aldercrest.atlas.ingest.IngestionRepository;
import com.aldercrest.atlas.persistence.AuditWriter;
import com.aldercrest.atlas.persistence.RequestTransaction;
import com.aldercrest.atlas.security.TestActor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ResultDetailService {
    private final RequestTransaction transaction;
    private final IngestionRepository roster;
    private final ClinicalViewRepository repository;
    private final AuditWriter audit;
    public ResultDetailService(RequestTransaction transaction,IngestionRepository roster,ClinicalViewRepository repository,AuditWriter audit) {
        this.transaction=transaction;this.roster=roster;this.repository=repository;this.audit=audit;
    }
    public ResponseEntity<?> detail(TestActor declared,String patientId,String observationId) {
        return transaction.execute(() -> {
            if(!declared.valid()) {return denied(declared,patientId,400);}
            if(!roster.known(declared)) {return denied(declared,patientId,404);}
            TestActor actor=declared.withVerifiedRoster();
            if(!validId(patientId) || !validId(observationId)) {return denied(actor,patientId,404);}
            var access=repository.access(patientId,actor);
            if(access==null) {return denied(actor,patientId,404);}
            var view=repository.view(patientId,access);
            var observation=view.series().observations().stream().filter(row -> observationId.equals(row.id())).findFirst().orElse(null);
            if(observation==null) {return denied(actor,patientId,404);}
            var body=ResultRendering.observation(observation,view);
            audit.write(actor,patientId,"RESULT_DETAIL","ALLOWED","HTTP");
            return ResponseEntity.ok(body);
        });
    }
    private ResponseEntity<?> denied(TestActor actor,String patientId,int status) {
        audit.write(actor,patientId,"RESULT_DETAIL","DENIED","HTTP");return SafeErrors.response(status);
    }
    private static boolean validId(String id) {return id!=null && id.matches("[A-Za-z0-9.-]{1,64}");}
}
