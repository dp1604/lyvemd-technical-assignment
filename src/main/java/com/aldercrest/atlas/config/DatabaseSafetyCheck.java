package com.aldercrest.atlas.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSafetyCheck implements InitializingBean {
    private final JdbcTemplate jdbc;

    public DatabaseSafetyCheck(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void afterPropertiesSet() {
        Boolean safe = jdbc.queryForObject("""
                SELECT current_setting('server_version_num')::int / 10000 = 16
                   AND current_schema() = 'atlas'
                   AND NOT r.rolsuper AND NOT r.rolcreatedb AND NOT r.rolcreaterole
                   AND NOT r.rolbypassrls
                   AND NOT has_schema_privilege(current_user, 'atlas', 'CREATE')
                   AND NOT has_schema_privilege(current_user, 'public', 'CREATE')
                   AND NOT has_table_privilege(current_user, 'atlas.observation', 'UPDATE,DELETE')
                   AND NOT has_table_privilege(current_user, 'atlas.access_log', 'UPDATE,DELETE')
                FROM pg_roles r WHERE r.rolname = current_user
                """, Boolean.class);
        if (!Boolean.TRUE.equals(safe)) {
            throw new IllegalStateException("Database version, migration or runtime privileges are unsafe.");
        }
    }
}
