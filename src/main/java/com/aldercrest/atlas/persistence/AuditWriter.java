package com.aldercrest.atlas.persistence;

import com.aldercrest.atlas.security.TestActor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AuditWriter {
    private final JdbcTemplate jdbc;
    public AuditWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void write(TestActor actor,String patientId,String action,String outcome,String source) {
        write(actor,patientId,action,outcome,source,null);
    }

    public void write(TestActor actor,String patientId,String action,String outcome,String source,String targetProxyId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Audit must share the action transaction.");
        }
        String safePatient = patientId != null && patientId.matches("[A-Za-z0-9.-]{1,64}") ? patientId : null;
        String safeProxy = targetProxyId != null && targetProxyId.matches("[A-Za-z0-9._-]{1,64}") ? targetProxyId : null;
        jdbc.update("""
                INSERT INTO access_log(actor_id,actor_role,patient_id,action,outcome,source,actor_roster_verified,target_proxy_id)
                VALUES (?,?,?,?,?,?,?,?)
                """,actor.valid() ? actor.id() : null,actor.valid() ? actor.role() : null,safePatient,action,outcome,source,
                actor.rosterVerified(),safeProxy);
    }
}
