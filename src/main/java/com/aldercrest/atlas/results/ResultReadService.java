package com.aldercrest.atlas.results;

import com.aldercrest.atlas.http.SafeErrors;
import com.aldercrest.atlas.security.TestActor;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ResultReadService {
    private final AuditedReadAction action;
    private final ClinicalViewRepository repository;
    public ResultReadService(AuditedReadAction action,ClinicalViewRepository repository) {this.action=action;this.repository=repository;}
    public ResponseEntity<?> page(TestActor declared,String patientId,Map<String,String[]> parameters) {
        return action.execute(declared,patientId,"RESULT_LIST",actor -> {
            var page=ReadQueries.page(parameters);var access=repository.access(patientId,actor);
            return access==null?SafeErrors.response(404):ResponseEntity.ok(new ReadResults(repository.view(patientId,access)).page(page));
        });
    }
    public ResponseEntity<?> trend(TestActor declared,String patientId,Map<String,String[]> parameters) {
        return action.execute(declared,patientId,"TREND",actor -> {
            var query=ReadQueries.trend(parameters);var access=repository.access(patientId,actor);
            return access==null?SafeErrors.response(404):ResponseEntity.ok(new ReadResults(repository.view(patientId,access)).trend(query));
        });
    }
}
