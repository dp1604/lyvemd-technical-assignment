package com.aldercrest.atlas.results;

import com.aldercrest.atlas.security.TestActor;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccessLogRepository {
    private final JdbcTemplate jdbc;
    public AccessLogRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public boolean allowed(String patientId,TestActor actor) {
        return ("STAFF".equals(actor.role()) || ("PATIENT".equals(actor.role()) && patientId.equals(actor.id())))
                && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM patient WHERE id=?)",Boolean.class,patientId));
    }
    public Map<String,Object> page(String patientId,ReadQueries.Page page) {
        long total=jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=?",Long.class,patientId);
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT id,actor_id,actor_role,patient_id,action,outcome,source,at,actor_roster_verified,target_proxy_id
                FROM access_log WHERE patient_id=? ORDER BY at DESC,id DESC LIMIT ? OFFSET ?
                """,(rs,n) -> {
                    Map<String,Object> row=new LinkedHashMap<>();
                    row.put("id",Long.toString(rs.getLong("id")));row.put("actorId",rs.getString("actor_id"));
                    row.put("actorRole",rs.getString("actor_role"));row.put("patientId",rs.getString("patient_id"));
                    row.put("action",rs.getString("action"));row.put("outcome",rs.getString("outcome"));row.put("source",rs.getString("source"));
                    row.put("at",rs.getObject("at",OffsetDateTime.class).toInstant().toString());row.put("actorRosterVerified",rs.getBoolean("actor_roster_verified"));
                    row.put("targetProxyId",rs.getString("target_proxy_id"));return row;
                },patientId,page.limit(),page.offset());
        // The action's own audit is appended by AuditedReadAction after this page/count is materialized.
        return Map.of("items",rows,"total",total,"offset",page.offset(),"limit",page.limit());
    }
}
