package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.ingest.IngestionJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Bounded descriptive timing evidence, never a universal indistinguishability proof. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(Group5TimingIT.FixedClock.class)
class Group5TimingIT {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        java.time.Clock assessmentClock() {return java.time.Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"),ZoneOffset.UTC);}
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("ATLAS_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {
            throw new IllegalStateException("Designated synthetic test DB required.");
        }
        SchemaMigration.run(System.getenv());
    }
    private record Sample(int status,String body,long elapsedNanos) { }
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    @Test @Timeout(value=120,unit=TimeUnit.SECONDS)
    void alternatingHiddenAndMissingDetailProbeWritesDescriptiveDistributions() throws Exception {
        String url=System.getenv("ATLAS_DB_URL");
        JdbcTemplate owner=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"),System.getenv("ATLAS_MIGRATION_PASSWORD")));
        String patient=id(),proxy=id(),clinician=id(),ordinary=id(),protectedEncounter=id(),visible=id(),hidden=id(),missing=id();
        assertThat(hidden.length()).isEqualTo(missing.length()); // Both assigned/missing UUID-shaped IDs.
        owner.update("INSERT INTO actor(id,role) VALUES (?,'PATIENT'),(?,'PROXY'),(?,'CLINICIAN')",patient,proxy,clinician);
        owner.update("INSERT INTO patient(id,sex,birth_date) VALUES (?,'female','2010-01-01')",patient);
        owner.update("INSERT INTO proxy_link(patient_id,proxy_id) VALUES (?,?)",patient,proxy);
        owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id) VALUES (?,?,'AMB',?),(?,?,'MHEV',?)",
                ordinary,patient,clinician,protectedEncounter,patient,clinician);
        // Explicit test-only clinical/release fixtures. This is not dataset/workflow execution proof.
        for(String[] row:List.of(new String[]{visible,ordinary},new String[]{hidden,protectedEncounter})) {
            owner.update("""
                    INSERT INTO observation(id,patient_id,encounter_id,code,unit_code,value_num,effective,status,birth_date_context,sex_context)
                    VALUES (?,?,?,'2951-2','mmol/L',140,'2026-01-01T00:00:00Z','final','2010-01-01','female')
                    """,row[0],patient,row[1]);
            owner.update("""
                    INSERT INTO observation_review(observation_id,criticality,reviewed_by,reviewed_at,released_by,released_at)
                    VALUES (?,'NONCRITICAL',?,clock_timestamp(),?,clock_timestamp())
                    """,row[0],clinician,clinician);
        }
        List<Long> hiddenTimes=new ArrayList<>(),missingTimes=new ArrayList<>(),pairedDifferences=new ArrayList<>();
        int warmupPairs=10,measuredPairs=100;
        String referenceBody=null;
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            for(int pair=-warmupPairs;pair<measuredPairs;pair++) {
                // Bounded paired probe; reverse first-in-pair order
                // every time to reduce a simple warmup/time-drift ordering bias.
                Sample first=request(client,patient,proxy,(pair&1)==0?hidden:missing);
                Sample second=request(client,patient,proxy,(pair&1)==0?missing:hidden);
                Sample hiddenSample=(pair&1)==0?first:second,missingSample=(pair&1)==0?second:first;
                assertThat(hiddenSample.status()).isEqualTo(404);assertThat(missingSample.status()).isEqualTo(404);
                assertThat(hiddenSample.body()).isEqualTo(missingSample.body());
                if(referenceBody==null) {referenceBody=hiddenSample.body();}
                assertThat(hiddenSample.body()).isEqualTo(referenceBody);
                if(pair>=0) {
                    hiddenTimes.add(hiddenSample.elapsedNanos());missingTimes.add(missingSample.elapsedNanos());
                    pairedDifferences.add(hiddenSample.elapsedNanos()-missingSample.elapsedNanos());
                }
            }
        }
        assertThat(hiddenTimes).hasSize(measuredPairs);assertThat(missingTimes).hasSize(measuredPairs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND actor_id=? AND action='RESULT_DETAIL' AND outcome='DENIED'",
                Long.class,patient,proxy)).isEqualTo(2L*(warmupPairs+measuredPairs));
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("recordedAtUtc",Instant.now().toString());report.put("endpoint","GET /patients/{id}/results/{obsId}");
        report.put("scenario","Known active16-year-old proxy; released protected MHEV detail versus absent UUID; same patient with one ordinary visible row");
        report.put("conditions","Sequential loopback HTTP; one reused client;10 warmup pairs;100 measured pairs; alternating first-in-pair order; application unchanged during probe; system load not controlled");
        report.put("durationScope","Client monotonic time around complete HTTP send/body receive, including request/read/audit/commit and response transfer");
        report.put("java",Runtime.version().toString());report.put("postgresql",jdbc.queryForObject("SHOW server_version",String.class));
        report.put("warmupPairs",warmupPairs);report.put("measuredPairs",measuredPairs);report.put("statusEqual",true);report.put("bodyByteEqual",true);
        report.put("hidden",summary(hiddenTimes));report.put("missing",summary(missingTimes));
        report.put("pairedSignedHiddenMinusMissing",summary(pairedDifferences));
        report.put("pairedSamplesMs",java.util.stream.IntStream.range(0,measuredPairs).mapToObj(index -> Map.of(
                "pair",index,"first",index%2==0?"hidden":"missing","hiddenMs",hiddenTimes.get(index)/1_000_000.0,
                "missingMs",missingTimes.get(index)/1_000_000.0)).toList());
        report.put("percentileConvention","Median averages middle pair; p95 uses nearest-rank ceil(0.95*n)");
        report.put("interpretation","Descriptive bounded measurements, no invented pass/fail latency margin or calibrated detector. Equal status/body and shared query paths are established separately. These100 pairs do not prove universal timing indistinguishability or production performance; inspect distributions and investigate any practical separation.");
        Path output=Path.of("work","group5-timing.json");Files.createDirectories(output.getParent());
        IngestionJson.mapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
    }
    private Sample request(HttpClient client,String patient,String proxy,String observation) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/patients/"+patient+"/results/"+observation))
                .timeout(Duration.ofSeconds(15)).header("X-Test-Actor-Id",proxy).header("X-Test-Actor-Role","PROXY").GET().build();
        long start=System.nanoTime();HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
        long elapsed=System.nanoTime()-start;assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return new Sample(response.statusCode(),response.body(),elapsed);
    }
    private static Map<String,Object> summary(List<Long> samples) {
        List<Long> ordered=samples.stream().sorted().toList();int size=ordered.size();
        double median=size%2==0?(ordered.get(size/2-1)/2.0+ordered.get(size/2)/2.0):ordered.get(size/2);
        return Map.of("samples",size,"medianMs",median/1_000_000.0,"p95Ms",ordered.get((int)Math.ceil(0.95*size)-1)/1_000_000.0,
                "minMs",ordered.getFirst()/1_000_000.0,"maxMs",ordered.getLast()/1_000_000.0,
                "meanMs",samples.stream().mapToDouble(Long::doubleValue).average().orElseThrow()/1_000_000.0);
    }
    private static String id() {return UUID.randomUUID().toString();}
}
