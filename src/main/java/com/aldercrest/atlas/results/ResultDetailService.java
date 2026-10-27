package com.aldercrest.atlas.results;

import com.aldercrest.atlas.http.SafeErrors;
import com.aldercrest.atlas.security.TestActor;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ResultDetailService {
    private final AuditedReadAction action;
    private final ClinicalViewRepository repository;
    public ResultDetailService(AuditedReadAction action,ClinicalViewRepository repository) {this.action=action;this.repository=repository;}
    public ResponseEntity<?> detail(TestActor declared,String patientId,String observationId) {return detail(declared,patientId,observationId,Map.of());}
    public ResponseEntity<?> detail(TestActor declared,String patientId,String observationId,Map<String,String[]> parameters) {
        return action.execute(declared,patientId,"RESULT_DETAIL",actor -> {
            ReadQueries.none(parameters);
            if(!AuditedReadAction.validId(observationId)) {return SafeErrors.response(404);}
            var access=repository.access(patientId,actor);
            if(access==null) {return SafeErrors.response(404);}
            var view=repository.view(patientId,access);
            var result=new ReadResults(view).find(observationId);
            return result==null?SafeErrors.response(404):ResponseEntity.ok(result);
        });
    }
}
