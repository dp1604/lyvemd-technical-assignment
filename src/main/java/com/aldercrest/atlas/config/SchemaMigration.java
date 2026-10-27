package com.aldercrest.atlas.config;

import java.util.Map;
import org.flywaydb.core.Flyway;

/** Separate short-lived process: migration credentials never enter the serving JVM. */
public final class SchemaMigration {
    private SchemaMigration() { }

    public static void run(Map<String, String> environment) {
        Flyway.configure().dataSource(required(environment, "ATLAS_DB_URL"),
                        required(environment, "ATLAS_MIGRATION_USER"),
                        required(environment, "ATLAS_MIGRATION_PASSWORD"))
                .schemas("atlas").defaultSchema("atlas").createSchemas(false)
                .locations("classpath:db/migration").cleanDisabled(true)
                .validateMigrationNaming(true).load().migrate();
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database configuration: " + name);
        }
        return value;
    }
}
