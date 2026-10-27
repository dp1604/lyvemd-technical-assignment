package com.aldercrest.atlas.dataset;

import com.aldercrest.atlas.ingest.IngestionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Short-lived privileged synthetic fixture phase. Never create clinical source/review/audit rows here. */
public final class DatasetFixtureBootstrap {
    public static final String VERSION="atlas-g6-v1";
    public static final String CLINICIAN_A=VERSION+"-clinician-a";
    public static final String CLINICIAN_B=VERSION+"-clinician-b";
    private static final int MAX_INPUT_BYTES=2*1024*1024;
    private static final String FAILED="DATASET_FIXTURE_SETUP_FAILED";
    private static final Pattern URL=Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:55433/(atlas|atlas_test)(?:\\?([^#]*))?");
    private static final Map<String,String> FIXED_ACTORS=Map.of(
            CLINICIAN_A,"CLINICIAN",CLINICIAN_B,"CLINICIAN",VERSION+"-staff","STAFF",
            VERSION+"-proxy-teen13","PROXY",VERSION+"-proxy-teen16","PROXY",VERSION+"-proxy-expired","PROXY",
            VERSION+"-proxy-revoked","PROXY",VERSION+"-proxy-retained","PROXY");
    private record Patient(String logicalKey,String id,String actorId,String key,String hash) { }
    private record Configuration(String hash,String markerKey) { }
    @FunctionalInterface private interface Work {void run(Connection connection) throws SQLException,IOException;}

    private DatasetFixtureBootstrap() { }

    /** Optional standalone entry point; AtlasApplication may instead dispatch directly to run. */
    public static void main(String[] args) {
        try {
            if(args==null || args.length<1 || args.length>2) {throw failed();}
            Path input=args.length==2?Path.of(args[1]):null;
            if("configuration".equals(args[0])) {checkConfiguration(input);}
            else {run(args[0],input);}
            System.out.println("ATLAS_DATASET_FIXTURES 200 ACTION_COMPLETED");
        } catch(RuntimeException error) {
            System.out.println("ATLAS_DATASET_FIXTURES 503 "+FAILED);
            System.exit(2);
        }
    }

    /** One owner transaction; caller must remove migration credentials before starting any app context. */
    public static void run(String mode,Path manifest) {
        if("roster".equals(mode)) {
            ownerTransaction(connection -> {
                for(var actor:FIXED_ACTORS.entrySet()) {ensureActor(connection,actor.getKey(),actor.getValue());}
            });
            return;
        }
        if(!"relationships".equals(mode)) {throw failed();}
        List<Patient> patients=patients(manifest);
        ownerTransaction(connection -> {
            // Validate every stored receipt and patient before creating any relationship fixture.
            for(Patient patient:patients) {verifyReceiptAndPatient(connection,patient);}
            for(var actor:FIXED_ACTORS.entrySet()) {requireActor(connection,actor.getKey(),actor.getValue());}
            for(Patient patient:patients) {ensureActor(connection,patient.id(),"PATIENT");}
            ensureProxy(connection,patients.get(1).id(),VERSION+"-proxy-teen13");
            ensureProxy(connection,patients.get(2).id(),VERSION+"-proxy-teen16");
            ensureProxy(connection,patients.get(7).id(),VERSION+"-proxy-expired");
            ensureProxy(connection,patients.get(8).id(),VERSION+"-proxy-revoked");
            ensureProxy(connection,patients.get(8).id(),VERSION+"-proxy-retained");
        });
    }

    /** Read only reserved receipt metadata; unrelated cohorts/records are never reset or adopted. */
    public static void checkConfiguration(Path input) {
        Configuration configuration=configuration(input);
        ownerTransaction(connection -> {
            int markers=0;
            try(PreparedStatement statement=connection.prepareStatement("""
                    SELECT actor_id,idempotency_key,http_status FROM atlas.ingestion_receipt
                    WHERE actor_id IN (?,?) AND actor_role='CLINICIAN' AND idempotency_key LIKE ?
                    """)) {
                statement.setString(1,CLINICIAN_A);statement.setString(2,CLINICIAN_B);statement.setString(3,VERSION+"-config-%");
                try(var rows=statement.executeQuery()) {
                    while(rows.next()) {
                        markers++;
                        if(!CLINICIAN_A.equals(rows.getString(1)) || !configuration.markerKey().equals(rows.getString(2))
                                || rows.getInt(3)!=200) {throw failed();}
                    }
                }
            }
            if(markers>1) {throw failed();}
            if(markers==0) {
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT EXISTS(SELECT 1 FROM atlas.ingestion_receipt WHERE actor_id IN (?,?)
                            AND actor_role='CLINICIAN' AND idempotency_key LIKE ?)
                        """)) {
                    statement.setString(1,CLINICIAN_A);statement.setString(2,CLINICIAN_B);statement.setString(3,VERSION+"-initial-%");
                    try(var rows=statement.executeQuery()) {if(!rows.next() || rows.getBoolean(1)) {throw failed();}}
                }
            }
        });
    }

    private static void verifyReceiptAndPatient(Connection connection,Patient patient) throws SQLException,IOException {
        try(PreparedStatement statement=connection.prepareStatement("""
                SELECT request_hash,http_status,response_body FROM atlas.ingestion_receipt
                WHERE actor_id=? AND actor_role='CLINICIAN' AND idempotency_key=?
                """)) {
            statement.setString(1,patient.actorId());statement.setString(2,patient.key());
            try(var rows=statement.executeQuery()) {
                if(!rows.next() || !patient.hash().equals(rows.getString(1)) || rows.getInt(2)!=200) {throw failed();}
                JsonNode reply=IngestionJson.mapper().readTree(rows.getString(3));
                if(reply==null || !"Bundle".equals(reply.path("resourceType").asText())
                        || !"transaction-response".equals(reply.path("type").asText()) || !reply.path("entry").isArray()) {throw failed();}
                int patients=0;
                for(JsonNode entry:reply.path("entry")) {
                    JsonNode response=entry.path("response");
                    if(!"201 Created".equals(response.path("status").asText())) {throw failed();}
                    String location=response.path("location").asText();
                    if(location.startsWith("Patient/")) {
                        patients++;
                        if(!("Patient/"+patient.id()).equals(location)) {throw failed();}
                    }
                }
                if(patients!=1 || rows.next()) {throw failed();}
            }
        }
        try(PreparedStatement statement=connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM atlas.patient WHERE id=?)")) {
            statement.setString(1,patient.id());
            try(var rows=statement.executeQuery()) {if(!rows.next() || !rows.getBoolean(1)) {throw failed();}}
        }
    }

    private static void ensureActor(Connection connection,String id,String role) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("INSERT INTO atlas.actor(id,role) VALUES (?,?) ON CONFLICT(id) DO NOTHING")) {
            statement.setString(1,id);statement.setString(2,role);statement.executeUpdate();
        }
        requireActor(connection,id,role); // Never replace an existing person's/fixture's role.
    }
    private static void requireActor(Connection connection,String id,String role) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("SELECT role FROM atlas.actor WHERE id=?")) {
            statement.setString(1,id);
            try(var rows=statement.executeQuery()) {if(!rows.next() || !role.equals(rows.getString(1)) || rows.next()) {throw failed();}}
        }
    }
    private static void ensureProxy(Connection connection,String patientId,String proxyId) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM atlas.proxy_link WHERE proxy_id=? AND patient_id<>?)")) {
            statement.setString(1,proxyId);statement.setString(2,patientId);
            try(var rows=statement.executeQuery()) {if(!rows.next() || rows.getBoolean(1)) {throw failed();}}
        }
        try(PreparedStatement statement=connection.prepareStatement("INSERT INTO atlas.proxy_link(patient_id,proxy_id) VALUES (?,?) ON CONFLICT(patient_id,proxy_id) DO NOTHING")) {
            statement.setString(1,patientId);statement.setString(2,proxyId);statement.executeUpdate();
        }
        try(PreparedStatement statement=connection.prepareStatement("SELECT active,revoked_at,revoked_by FROM atlas.proxy_link WHERE patient_id=? AND proxy_id=?")) {
            statement.setString(1,patientId);statement.setString(2,proxyId);
            try(var rows=statement.executeQuery()) {
                if(!rows.next()) {throw failed();}
                boolean active=rows.getBoolean(1),hasRevokedTime=rows.getObject(2)!=null;
                if(active==hasRevokedTime || (active && rows.getString(3)!=null) || rows.next()) {throw failed();}
                // Preserve inactive links and their original known/unknown revoker; never reactivate.
            }
        }
    }

    private static List<Patient> patients(Path path) {
        try {
            JsonNode root=input(path);
            if(!VERSION.equals(text(root,"version")) || !root.path("patients").isObject() || root.path("patients").size()!=100) {throw failed();}
            List<Patient> patients=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(int index=0;index<100;index++) {
                String logical="p%03d".formatted(index);JsonNode row=root.path("patients").path(logical);
                String id=text(row,"id"),actor=text(row,"actorId"),key=text(row,"idempotencyKey"),hash=text(row,"requestHash");
                if(!uuid(id) || !ids.add(id) || !Set.of(CLINICIAN_A,CLINICIAN_B).contains(actor)
                        || !(VERSION+"-initial-"+logical).equals(key) || !hash.matches("[0-9a-f]{64}")) {throw failed();}
                patients.add(new Patient(logical,id,actor,key,hash));
            }
            return List.copyOf(patients);
        } catch(IOException | RuntimeException error) {throw failed();}
    }
    private static Configuration configuration(Path path) {
        try {
            JsonNode root=input(path);
            String hash=text(root,"recipeHash"),key=text(root,"markerKey");
            if(!VERSION.equals(text(root,"version")) || !root.path("patients").isIntegralNumber() || !root.path("patients").canConvertToInt()
                    || root.path("patients").intValue()!=100 || !root.path("sourceObservations").isIntegralNumber()
                    || !root.path("sourceObservations").canConvertToInt() || root.path("sourceObservations").intValue()!=10_000
                    || !hash.matches("[0-9a-f]{64}") || !(VERSION+"-config-"+hash).equals(key)) {throw failed();}
            return new Configuration(hash,key);
        } catch(IOException | RuntimeException error) {throw failed();}
    }
    private static JsonNode input(Path path) throws IOException {
        if(path==null) {throw failed();}
        byte[] bytes;
        try(var stream=Files.newInputStream(path)) {bytes=stream.readNBytes(MAX_INPUT_BYTES+1);}
        if(bytes.length>MAX_INPUT_BYTES) {throw failed();}
        JsonNode root=IngestionJson.mapper().readTree(bytes);
        if(root==null || !root.isObject()) {throw failed();}
        return root;
    }
    private static String text(JsonNode root,String name) {
        JsonNode value=root.path(name);
        if(!value.isTextual() || value.textValue().isEmpty() || value.textValue().length()>128) {throw failed();}
        return value.textValue();
    }
    private static boolean uuid(String value) {
        try {return UUID.fromString(value).toString().equals(value);} catch(IllegalArgumentException error) {return false;}
    }

    private static void ownerTransaction(Work work) {
        try(Connection connection=ownerConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                try(var statement=connection.createStatement()) {
                    statement.execute("SET LOCAL statement_timeout='10s'");statement.execute("SET LOCAL lock_timeout='3s'");
                }
                try(var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                    lock.setString(1,VERSION+"-fixture-bootstrap");lock.executeQuery().close();
                }
                verifyOwner(connection);work.run(connection);connection.commit();
            } catch(SQLException | IOException | RuntimeException error) {
                try {connection.rollback();} catch(SQLException ignored) { }
                throw failed();
            }
        } catch(SQLException | RuntimeException error) {throw failed();}
    }
    private static Connection ownerConnection() throws SQLException {
        String url=System.getenv("ATLAS_DB_URL"),user=System.getenv("ATLAS_MIGRATION_USER"),password=System.getenv("ATLAS_MIGRATION_PASSWORD");
        var match=URL.matcher(url==null?"":url);
        if(!match.matches() || !"atlas_migrate".equals(user) || password==null || password.isBlank()) {throw failed();}
        String query=match.group(2);Set<String> seen=new HashSet<>();
        if(query!=null) {
            if(query.isEmpty()) {throw failed();}
            for(String pair:query.split("&",-1)) {
                String[] parts=pair.split("=",-1);
                if(parts.length!=2 || !seen.add(parts[0])) {throw failed();}
                boolean allowed=switch(parts[0]) {
                    case "connectTimeout" -> parts[1].matches("[1-9]|10");
                    case "socketTimeout" -> parts[1].matches("[1-9]|[1-5][0-9]|60");
                    case "ApplicationName" -> parts[1].matches("[A-Za-z0-9._-]{1,64}");
                    case "currentSchema" -> "atlas".equals(parts[1]);
                    default -> false;
                };
                if(!allowed) {throw failed();}
            }
        }
        Properties properties=new Properties();properties.setProperty("user",user);properties.setProperty("password",password);
        properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","20");
        properties.setProperty("ApplicationName","atlas-dataset-fixtures");
        return DriverManager.getConnection(url,properties);
    }
    private static void verifyOwner(Connection connection) throws SQLException {
        try(var statement=connection.prepareStatement("""
                SELECT current_user='atlas_migrate' AND session_user='atlas_migrate'
                    AND current_database() IN ('atlas','atlas_test')
                    AND current_setting('server_version_num')::integer/10000=16
                    AND NOT r.rolsuper AND NOT r.rolcreatedb AND NOT r.rolcreaterole AND NOT r.rolreplication AND NOT r.rolbypassrls
                    AND EXISTS(SELECT 1 FROM pg_namespace n WHERE n.nspname='atlas' AND n.nspowner=r.oid)
                FROM pg_roles r WHERE r.rolname=current_user
                """);var rows=statement.executeQuery()) {
            if(!rows.next() || !rows.getBoolean(1) || rows.next()) {throw failed();}
        }
    }
    private static IllegalStateException failed() {return new IllegalStateException(FAILED);}
}
