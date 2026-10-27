package com.aldercrest.atlas.results;

import com.aldercrest.atlas.http.SafeErrors;
import com.aldercrest.atlas.security.TestActor;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class AccessLogService {
    private final AuditedReadAction action;
    private final AccessLogRepository repository;
    public AccessLogService(AuditedReadAction action,AccessLogRepository repository) {this.action=action;this.repository=repository;}
    public ResponseEntity<?> page(TestActor declared,String patientId,Map<String,String[]> parameters) {
        return action.execute(declared,patientId,"ACCESS_LOG",actor -> {
            var page=ReadQueries.page(parameters);
            return repository.allowed(patientId,actor)?ResponseEntity.ok(repository.page(patientId,page)):SafeErrors.response(404);
        });
    }
}
