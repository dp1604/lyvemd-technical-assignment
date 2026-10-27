package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/**
 * Real HTTP / designated synthetic PostgreSQL. AI-drafted tests reviewed and executed by root.
 * Owner fixtures isolate read behavior; direct review-row fixtures are not workflow evidence.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(ReadApiIT.FixedClock.class)
class ReadApiIT {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        java.time.Clock assessmentClock() {return java.time.Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"),ZoneOffset.UTC);}
    }
    private static final ObjectMapper JSON=IngestionJson.mapper();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final String FAULT_FUNCTION="atlas.test_g5_read_audit_failure";
    private static final String FAULT_TRIGGER="test_g5_read_audit_failure";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("ATLAS_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55433/atlas_test(?:\\?.*)?")) {
            throw new IllegalStateException("Designated synthetic test DB required.");
        }
        SchemaMigration.run(System.getenv());
    }
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    private JdbcTemplate owner;
    private String patient,otherPatient,clinician,otherClinician,proxy,staff,ordinary;
    @BeforeEach void fixture() {
        String url=System.getenv("ATLAS_DB_URL");
        owner=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema=atlas",
                System.getenv("ATLAS_MIGRATION_USER"),System.getenv("ATLAS_MIGRATION_PASSWORD")));
        patient=id();otherPatient=id();clinician=id();otherClinician=id();proxy=id();staff=id();
        owner.update("INSERT INTO actor(id,role) VALUES (?,'PATIENT'),(?,'PATIENT'),(?,'CLINICIAN'),(?,'CLINICIAN'),(?,'PROXY'),(?,'STAFF')",
                patient,otherPatient,clinician,otherClinician,proxy,staff);
        owner.update("INSERT INTO patient(id,sex,birth_date) VALUES (?,'female','2010-01-01'),(?,'male','2000-01-01')",patient,otherPatient);
        owner.update("INSERT INTO proxy_link(patient_id,proxy_id) VALUES (?,?)",patient,proxy);
        ordinary=encounter("AMB");
    }

    @Test void listTotalsAndPagesAreOnlyVisibleAndConcealedAdditionsChangeNothing() throws Exception {
        String older=row(ordinary,"2951-2","mmol/L","135","2026-01-01T00:00:00Z",true);
        String newer=row(ordinary,"2951-2","mmol/L","140","2026-01-02T00:00:00Z",true);
        var baseline=get("/results",proxy,"PROXY");assertThat(baseline.statusCode()).isEqualTo(200);
        JsonNode base=JSON.readTree(baseline.body());assertThat(base.path("total").asLong()).isEqualTo(2);
        assertThat(ids(base,"items")).containsExactly(newer,older);
        assertThat(base.at("/items/0/flag").asText()).isEqualTo("NORMAL");
        assertThat(base.at("/items/0/range/low").decimalValue()).isEqualByComparingTo("135");
        assertThat(base.at("/items/0/range/high").decimalValue()).isEqualByComparingTo("145");

        row(ordinary,"2951-2","mmol/L","155","2026-01-03T00:00:00Z",false);
        row(encounter("MHEV"),"2951-2","mmol/L","160","2026-01-04T00:00:00Z",true);
        var withConcealed=get("/results",proxy,"PROXY");
        assertThat(withConcealed.statusCode()).isEqualTo(200);
        assertThat(withConcealed.body()).isEqualTo(baseline.body());
        assertThat(JSON.readTree(get("/results",patient,"PATIENT").body()).path("total").asLong()).isEqualTo(3);
        assertThat(JSON.readTree(get("/results",clinician,"CLINICIAN").body()).path("total").asLong()).isEqualTo(4);
        JsonNode page=JSON.readTree(get("/results?offset=1&limit=1",proxy,"PROXY").body());
        assertThat(ids(page,"items")).containsExactly(older);assertThat(page.path("total").asLong()).isEqualTo(2);
        JsonNode beyond=JSON.readTree(get("/results?offset=1000000&limit=200",proxy,"PROXY").body());
        assertThat(beyond.path("items")).isEmpty();assertThat(beyond.path("total").asLong()).isEqualTo(2);
    }

    @Test void hiddenAndMissingDetailUseEqualStatusAndBodyWithoutCorrectionMetadataLeaks() throws Exception {
        String hidden=row(encounter("ABSL"),"2951-2","mmol/L","140","2026-01-01T00:00:00Z",true);
        var protectedResult=get("/results/"+hidden,proxy,"PROXY");
        var missing=get("/results/"+id(),proxy,"PROXY");
        assertThat(protectedResult.statusCode()).isEqualTo(404);assertThat(protectedResult.body()).isEqualTo(missing.body());
        String held=row(ordinary,"2951-2","mmol/L","140","2026-01-02T00:00:00Z",false);
        var heldResult=get("/results/"+held,patient,"PATIENT");
        var absent=get("/results/"+id(),patient,"PATIENT");
        assertThat(heldResult.statusCode()).isEqualTo(404);assertThat(heldResult.body()).isEqualTo(absent.body());
    }

    @Test void latestVisibleCorrectionDrivesTrendWhileAllRetainedRawVersionsRemainReadable() throws Exception {
        String original=row(ordinary,"2951-2","mmol/L","135","2026-01-01T00:00:00Z",true);
        String corrected=source(ordinary,"2951-2","mmol/L","145","2026-01-01T00:00:00Z","corrected",original,false,null,false,"2010-01-01");
        var before=get("/trend?code=2951-2",patient,"PATIENT");
        assertThat(ids(JSON.readTree(before.body()),"points")).containsExactly(original);
        JsonNode originalDetail=JSON.readTree(get("/results/"+original,patient,"PATIENT").body());
        assertThat(originalDetail.path("correctedBy")).isEmpty();
        assertThat(ids(JSON.readTree(get("/trend?code=2951-2",clinician,"CLINICIAN").body()),"points")).containsExactly(corrected);
        release(corrected);
        JsonNode trend=JSON.readTree(get("/trend?code=2951-2",patient,"PATIENT").body());
        assertThat(ids(trend,"points")).containsExactly(corrected);assertThat(trend.path("total").asLong()).isEqualTo(1);
        assertThat(trend.at("/points/0/correctionOf").asText()).isEqualTo(original);
        JsonNode list=JSON.readTree(get("/results",patient,"PATIENT").body());
        assertThat(list.path("total").asLong()).isEqualTo(2);assertThat(ids(list,"items")).containsExactlyInAnyOrder(original,corrected);
        assertThat(get("/results/"+original,patient,"PATIENT").statusCode()).isEqualTo(200);
        assertThat(get("/results/"+corrected,patient,"PATIENT").statusCode()).isEqualTo(200);
    }

    @Test void latestIneligibleVisibleCorrectionIsALabelledGapAndNeverRevivesAnOriginal() throws Exception {
        String original=row(ordinary,"2951-2","mmol/L","135","2026-01-01T00:00:00Z",true);
        // C3 model robustness fixture: the DB permits this state, while current ingestion's
        // bounded correction profile need not create preliminary corrections.
        String pending=source(ordinary,"2951-2","mmol/L","145","2026-01-01T00:00:00Z","preliminary",original,false,null,false,"2010-01-01");
        JsonNode clinicianTrend=JSON.readTree(get("/trend?code=2951-2",clinician,"CLINICIAN").body());
        assertThat(ids(clinicianTrend,"points")).containsExactly(pending);
        assertThat(clinicianTrend.at("/points/0/value").isNull()).isTrue();
        assertThat(clinicianTrend.at("/points/0/range").isNull()).isTrue();
        assertThat(clinicianTrend.at("/points/0/flag").asText()).isEqualTo("NO_APPLICABLE_FLAG");
        assertThat(clinicianTrend.at("/points/0/flagReason").asText()).isEqualTo("INPUT_STATUS_INELIGIBLE");
        assertThat(clinicianTrend.at("/points/0/cannotComputeReason").asText()).isEqualTo("INPUT_STATUS_INELIGIBLE");
        assertThat(ids(JSON.readTree(get("/trend?code=2951-2",patient,"PATIENT").body()),"points")).containsExactly(original);
    }

    @Test void bmiUsesVisibleHeightBeforeTheRequestedWindowAndDerivedDetailShowsItsSources() throws Exception {
        String height=row(ordinary,"8302-2","cm","180","2020-01-01T00:00:00Z",true);
        String weight=row(ordinary,"29463-7","kg","81","2026-01-02T00:00:00Z",true);
        var response=get("/trend?code=39156-5&from=2026-01-01&to=2026-01-31",patient,"PATIENT");
        assertThat(response.statusCode()).isEqualTo(200);JsonNode body=JSON.readTree(response.body());
        assertThat(body.path("total").asLong()).isEqualTo(1);JsonNode bmi=body.path("points").get(0);
        assertThat(bmi.path("value").decimalValue()).isEqualByComparingTo("25");
        assertThat(bmi.path("computed").asBoolean()).isTrue();assertThat(bmi.path("derived").asBoolean()).isTrue();
        assertThat(bmi.path("id").asText()).matches("[0-9a-f]{64}");
        assertThat(bmi.path("method").asText()).isEqualTo("atlas-visible-as-of-bmi-v1");
        assertThat(bmi.path("notice").asText()).contains("this caller","No clinical freshness");
        assertThat(sourceIds(bmi)).containsExactlyInAnyOrder(height,weight);
        assertThat(bmi.path("sources").findValuesAsText("effective")).contains("2020-01-01T00:00:00Z","2026-01-02T00:00:00Z");
        var detail=get("/results/"+bmi.path("id").asText(),patient,"PATIENT");
        assertThat(detail.statusCode()).isEqualTo(200);assertThat(JSON.readTree(detail.body())).isEqualTo(bmi);
        assertThat(bmi.path("correctionOf").isNull()).isTrue();
        assertThat(bmi.path("releaseState").asText()).isEqualTo("INHERITED_FROM_VISIBLE_INPUTS");
    }

    @Test void absentAndProtectedHeightHaveIdenticalBmiReasonIdSourcesAndTotals() throws Exception {
        String weight=row(ordinary,"29463-7","kg","81","2026-01-02T00:00:00Z",true);
        var absent=get("/trend?code=39156-5",proxy,"PROXY");
        JsonNode bmi=JSON.readTree(absent.body()).path("points").get(0);
        assertThat(bmi.path("computed").asBoolean()).isFalse();assertThat(bmi.path("value").isNull()).isTrue();
        assertThat(bmi.path("cannotComputeReason").asText()).isEqualTo("HEIGHT_UNAVAILABLE");
        assertThat(sourceIds(bmi)).containsExactly(weight);
        var listAbsent=get("/results",proxy,"PROXY");
        row(encounter("SUBU"),"8302-2","cm","180","2020-01-01T00:00:00Z",true);
        var concealed=get("/trend?code=39156-5",proxy,"PROXY");
        assertThat(concealed.statusCode()).isEqualTo(absent.statusCode());assertThat(concealed.body()).isEqualTo(absent.body());
        assertThat(get("/results",proxy,"PROXY").body()).isEqualTo(listAbsent.body());
        assertThat(JSON.readTree(get("/results/"+bmi.path("id").asText(),proxy,"PROXY").body())).isEqualTo(bmi);
    }

    @Test void concealedNewHeightAndCorrectionCannotChangeCallerVisibleDerivedIdentitiesOrValues() throws Exception {
        String height=row(ordinary,"8302-2","cm","180","2020-01-01T00:00:00Z",true);
        row(ordinary,"29463-7","kg","81","2026-01-02T00:00:00Z",true);
        var baseline=get("/trend?code=39156-5",proxy,"PROXY");
        source(ordinary,"8302-2","cm","200","2020-01-01T00:00:00Z","corrected",height,false,null,false,"2010-01-01");
        row(encounter("MHEV"),"8302-2","cm","220","2026-01-03T00:00:00Z",true);
        assertThat(get("/trend?code=39156-5",proxy,"PROXY").body()).isEqualTo(baseline.body());
    }

    @Test void aReleasedCorrectionWithAConcealedAncestorCannotLeakThroughListTrendOrDerivedSources() throws Exception {
        String original=source(ordinary,"8302-2","cm","180","2020-01-01T00:00:00Z","final",null,false,null,false,"2010-01-01");
        String corrected=source(ordinary,"8302-2","cm","200","2020-01-01T00:00:00Z","corrected",original,true,null,false,"2010-01-01");
        String weight=row(ordinary,"29463-7","kg","81","2026-01-02T00:00:00Z",true);
        var list=get("/results",patient,"PATIENT");
        assertThat(list.body()).doesNotContain(original,corrected);
        assertThat(JSON.readTree(list.body()).path("total").asLong()).isEqualTo(2); // Weight + unavailable BMI.
        JsonNode heightTrend=JSON.readTree(get("/trend?code=8302-2",patient,"PATIENT").body());
        assertThat(heightTrend.path("points")).isEmpty();assertThat(heightTrend.path("total").asLong()).isZero();
        JsonNode bmi=JSON.readTree(get("/trend?code=39156-5",patient,"PATIENT").body()).path("points").get(0);
        assertThat(bmi.path("cannotComputeReason").asText()).isEqualTo("HEIGHT_UNAVAILABLE");
        assertThat(sourceIds(bmi)).containsExactly(weight);
        assertThat(get("/results/"+corrected,patient,"PATIENT").statusCode()).isEqualTo(404);
        JsonNode clinicianBmi=JSON.readTree(get("/trend?code=39156-5&from=2026-01-01",clinician,"CLINICIAN").body()).path("points").get(0);
        assertThat(clinicianBmi.path("value").decimalValue()).isEqualByComparingTo("20.25");
        assertThat(sourceIds(clinicianBmi)).containsExactlyInAnyOrder(corrected,weight);
    }

    @Test void egfrIsAComputedViewWithCanonicalDisplayAndExplicitUnavailableReasons() throws Exception {
        owner.update("UPDATE patient SET birth_date='2000-01-01' WHERE id=?",patient);
        String valid=source(ordinary,"2160-0","mg/dL","1","2026-01-01T00:00:00Z","final",null,true,null,true,"2000-01-01");
        String nonstandardized=source(ordinary,"2160-0","mg/dL","1","2026-01-02T00:00:00Z","final",null,true,null,false,"2000-01-01");
        String minor=source(ordinary,"2160-0","mg/dL","1","2026-01-03T00:00:00Z","final",null,true,null,true,"2010-01-01");
        JsonNode trend=JSON.readTree(get("/trend?code=98979-8",patient,"PATIENT").body());
        assertThat(trend.path("total").asLong()).isEqualTo(3);
        JsonNode computed=pointForSource(trend,valid),notStandard=pointForSource(trend,nonstandardized),under18=pointForSource(trend,minor);
        assertThat(computed.path("computed").asBoolean()).isTrue();assertThat(computed.path("value").decimalValue()).isPositive();
        assertThat(computed.path("unit").asText()).isEqualTo("mL/s/1.73m2");
        assertThat(computed.path("unitCode").asText()).isEqualTo("mL/s/{1.73_m2}");
        assertThat(computed.path("method").asText()).isEqualTo("CKD-EPI-2021-creatinine-v1");
        assertThat(computed.path("notice").asText()).contains("visible to this caller","source dates","niddk.nih.gov");
        assertThat(computed.path("cannotComputeReason").asText()).isEqualTo("NONE");
        assertThat(computed.path("range").isNull()).isTrue();assertThat(computed.path("flag").asText()).isEqualTo("NO_APPLICABLE_FLAG");
        assertThat(notStandard.path("cannotComputeReason").asText()).isEqualTo("CREATININE_NOT_STANDARDIZED");
        assertThat(notStandard.path("value").isNull()).isTrue();assertThat(under18.path("cannotComputeReason").asText()).isEqualTo("AGE_NOT_APPLICABLE");
        assertThat(under18.path("value").isNull()).isTrue();
        assertThat(JSON.readTree(get("/results/"+computed.path("id").asText(),patient,"PATIENT").body())).isEqualTo(computed);
        assertThat(JSON.readTree(get("/results",patient,"PATIENT").body()).path("total").asLong()).isEqualTo(6);
    }

    @Test void bloodPressureComponentTrendsProjectTheirOwnValueRangeFlagAndParentDetailId() throws Exception {
        String components=JSON.writeValueAsString(List.of(
                Map.of("code","8480-6","value_num",130,"unit_code","mm[Hg]","source_unit_code","mm[Hg]"),
                Map.of("code","8462-4","value_num",75,"unit_code","mm[Hg]","source_unit_code","mm[Hg]")));
        String parent=source(ordinary,"85354-9","mm[Hg]",null,"2026-01-01T00:00:00Z","final",null,true,components,false,"2000-01-01");
        JsonNode systolic=JSON.readTree(get("/trend?code=8480-6",patient,"PATIENT").body()).path("points").get(0);
        JsonNode diastolic=JSON.readTree(get("/trend?code=8462-4",patient,"PATIENT").body()).path("points").get(0);
        assertThat(systolic.path("code").asText()).isEqualTo("8480-6");assertThat(systolic.path("value").decimalValue()).isEqualByComparingTo("130");
        assertThat(systolic.path("flag").asText()).isEqualTo("HIGH");assertThat(systolic.at("/range/high").decimalValue()).isEqualByComparingTo("120");
        assertThat(diastolic.path("code").asText()).isEqualTo("8462-4");assertThat(diastolic.path("value").decimalValue()).isEqualByComparingTo("75");
        assertThat(diastolic.path("flag").asText()).isEqualTo("NORMAL");assertThat(diastolic.at("/range/low").decimalValue()).isEqualByComparingTo("60");
        assertThat(systolic.path("id").asText()).isEqualTo(parent);assertThat(systolic.path("parentObservationId").asText()).isEqualTo(parent);
        assertThat(diastolic.path("id").asText()).isEqualTo(parent);assertThat(get("/results/"+parent,patient,"PATIENT").statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(get("/results",patient,"PATIENT").body()).path("total").asLong()).isEqualTo(1);
    }

    @Test void exactNanosecondOrderAndInclusiveTimestampAndDateBoundsSurviveDatabasePrecision() throws Exception {
        String earlier=row(ordinary,"2951-2","mmol/L","135","2026-01-01T00:00:00.123456001Z",true);
        String later=row(ordinary,"2951-2","mmol/L","140","2026-01-01T00:00:00.123456002Z",true);
        String dayEnd=row(ordinary,"2951-2","mmol/L","145","2026-01-01T23:59:59.999999999Z",true);
        String nextDay=row(ordinary,"2951-2","mmol/L","150","2026-01-02T00:00:00Z",true);
        assertThat(ids(JSON.readTree(get("/results",patient,"PATIENT").body()),"items")).containsExactly(nextDay,dayEnd,later,earlier);
        JsonNode exact=JSON.readTree(get("/trend?code=2951-2&from=2026-01-01T00:00:00.123456001Z&to=2026-01-01T00:00:00.123456002Z",patient,"PATIENT").body());
        assertThat(ids(exact,"points")).containsExactly(earlier,later);assertThat(exact.path("total").asLong()).isEqualTo(2);
        JsonNode day=JSON.readTree(get("/trend?code=2951-2&from=2026-01-01&to=2026-01-01",patient,"PATIENT").body());
        assertThat(ids(day,"points")).containsExactly(earlier,later,dayEnd);
        JsonNode equivalentOffset=JSON.readTree(get("/trend?code=2951-2&from=2026-01-01T05:30:00.123456001%2B05:30&to=2026-01-01T05:30:00.123456002%2B05:30",patient,"PATIENT").body());
        assertThat(ids(equivalentOffset,"points")).containsExactly(earlier,later);
        assertThat(ids(JSON.readTree(get("/trend?code=2951-2&from=2026-01-02",patient,"PATIENT").body()),"points")).containsExactly(nextDay);
    }

    @Test void allClinicalReadRoutesUseStoredActorRightsAndAuditEveryAllowedOrDeniedRequest() throws Exception {
        String source=row(ordinary,"2951-2","mmol/L","140","2026-01-01T00:00:00Z",true);
        List<String> routes=List.of("/results","/results/"+source,"/trend?code=2951-2");
        for(String route:routes) {
            for(String[] allowed:List.of(new String[]{patient,"PATIENT"},new String[]{proxy,"PROXY"},new String[]{clinician,"CLINICIAN"})) {
                long before=audits(allowed[0]);assertThat(get(route,allowed[0],allowed[1]).statusCode()).as(route).isEqualTo(200);
                assertThat(audits(allowed[0])).isEqualTo(before+1);
            }
            for(String[] denied:List.of(new String[]{otherPatient,"PATIENT"},new String[]{otherClinician,"CLINICIAN"},new String[]{staff,"STAFF"},new String[]{id(),"PATIENT"})) {
                long before=audits(denied[0]);assertThat(get(route,denied[0],denied[1]).statusCode()).as(route).isEqualTo(404);
                assertThat(audits(denied[0])).isEqualTo(before+1);
            }
            long before=unverifiedAudits();assertThat(get(route,"bad!id","PATIENT").statusCode()).isEqualTo(400);
            assertThat(unverifiedAudits()).isEqualTo(before+1);
        }
    }

    @Test void proxyAgesAndAllThreeProtectedClassesApplyAcrossListDetailTrendAndLog() throws Exception {
        String ordinaryId=row(ordinary,"2951-2","mmol/L","140","2026-01-01T00:00:00Z",true);
        List<String> protectedIds=new ArrayList<>();
        for(String cls:List.of("ABSL","SUBU","MHEV")) {protectedIds.add(row(encounter(cls),"2951-2","mmol/L","140","2026-01-01T00:00:00Z",true));}
        birth("2014-10-28"); // Day before12 at fixed UTC request date.
        assertThat(JSON.readTree(get("/results",proxy,"PROXY").body()).path("total").asLong()).isEqualTo(4);
        assertThat(JSON.readTree(get("/trend?code=2951-2",proxy,"PROXY").body()).path("total").asLong()).isEqualTo(4);
        for(String hidden:protectedIds) {assertThat(get("/results/"+hidden,proxy,"PROXY").statusCode()).isEqualTo(200);}
        assertThat(get("/access-log",proxy,"PROXY").statusCode()).isEqualTo(404);
        birth("2014-10-27"); // Exact12: all historical protected encounters now hidden.
        assertThat(ids(JSON.readTree(get("/results",proxy,"PROXY").body()),"items")).containsExactly(ordinaryId);
        assertThat(ids(JSON.readTree(get("/trend?code=2951-2",proxy,"PROXY").body()),"points")).containsExactly(ordinaryId);
        for(String hidden:protectedIds) {assertThat(get("/results/"+hidden,proxy,"PROXY").statusCode()).isEqualTo(404);}
        assertThat(get("/access-log",proxy,"PROXY").statusCode()).isEqualTo(404);
        birth("2008-10-28"); // Day before18.
        assertThat(get("/results/"+ordinaryId,proxy,"PROXY").statusCode()).isEqualTo(200);
        for(String missingContext:new String[]{"2008-10-27",null,"2027-01-01"}) {
            birth(missingContext);
            for(String route:List.of("/results","/results/"+ordinaryId,"/trend?code=2951-2","/access-log")) {
                assertThat(get(route,proxy,"PROXY").statusCode()).as("Proxy DOB "+missingContext+" route "+route).isEqualTo(404);
            }
        }
    }

    @Test void queryErrorsAcrossClinicalRoutesAreAuditedWithoutEchoingTheQuery() throws Exception {
        String source=row(ordinary,"2951-2","mmol/L","140","2026-01-01T00:00:00Z",true);
        for(String route:List.of("/results?limit=0","/results?offset=-1","/results?limit=1&limit=2","/results?unknown=secretmarker",
                "/results/"+source+"?extra=secretmarker","/trend","/trend?code=2951-2&code=2947-0","/trend?code=unsupported",
                "/trend?code=2951-2&from=2026-01-02&to=2026-01-01","/trend?code=2951-2&from=2026-01-01T00:00:00",
                "/trend?code=2951-2&from=not-a-date","/trend?code=2951-2&unknown=secretmarker")) {
            long before=audits(patient);var response=get(route,patient,"PATIENT");
            assertThat(response.statusCode()).as(route).isEqualTo(400);assertThat(response.body()).doesNotContain("secretmarker");
            assertThat(audits(patient)).isEqualTo(before+1);
            assertThat(jdbc.queryForObject("SELECT outcome FROM access_log WHERE patient_id=? AND actor_id=? ORDER BY id DESC LIMIT 1",String.class,patient,patient)).isEqualTo("DENIED");
        }
    }

    @Test void insertAuditFailureWithholdsContentFromEveryClinicalReadRouteAndThenRecovers() throws Exception {auditFault(false);}
    @Test void deferredCommitAuditFailureWithholdsMaterializedContentFromEveryClinicalReadRouteAndThenRecovers() throws Exception {auditFault(true);}

    @Test void missingDuplicateAndRosterMismatchedDeclarationsAreDeniedOnEveryClinicalRoute() throws Exception {
        String source=row(ordinary,"2951-2","mmol/L","140","2026-01-01T00:00:00Z",true);
        for(String route:List.of("/results","/results/"+source,"/trend?code=2951-2")) {
            for(List<String> headers:List.of(List.<String>of(),
                    List.of("X-Test-Actor-Id",patient),
                    List.of("X-Test-Actor-Id",patient,"X-Test-Actor-Role","patient"),
                    List.of("X-Test-Actor-Id",patient,"X-Test-Actor-Id",proxy,"X-Test-Actor-Role","PATIENT"),
                    List.of("X-Test-Actor-Id",patient,"X-Test-Actor-Role","PATIENT","X-Test-Actor-Role","STAFF"))) {
                long before=unverifiedAudits();
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/patients/"+patient+route)).timeout(Duration.ofSeconds(20)).GET();
                for(int index=0;index<headers.size();index+=2) {request.header(headers.get(index),headers.get(index+1));}
                assertThat(HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
                assertThat(unverifiedAudits()).isEqualTo(before+1);
            }
            long before=audits(staff);
            assertThat(get(route,staff,"PATIENT").statusCode()).isEqualTo(404);
            assertThat(audits(staff)).isEqualTo(before+1);
            assertThat(jdbc.queryForObject("SELECT actor_roster_verified FROM access_log WHERE patient_id=? AND actor_id=? ORDER BY id DESC LIMIT 1",Boolean.class,patient,staff)).isFalse();
        }
    }
    private void auditFault(boolean deferred) throws Exception {
        String source=row(ordinary,"2951-2","mmol/L","141.12345","2026-01-01T00:00:00Z",true);
        List<String> routes=List.of("/results","/results/"+source,"/trend?code=2951-2");long before=audits(patient);
        try {
            installFault(deferred);
            for(String route:routes) {
                var failed=get(route,patient,"PATIENT");assertThat(failed.statusCode()).as(route).isEqualTo(503);
                JsonNode body=JSON.readTree(failed.body());assertThat(body.path("resourceType").asText()).isEqualTo("OperationOutcome");
                assertThat(body.has("items")).isFalse();assertThat(body.has("points")).isFalse();assertThat(body.has("value")).isFalse();
                assertThat(failed.body()).doesNotContain(source,patient,"141.12345","test_g5_read_audit_failure");
                assertThat(audits(patient)).isEqualTo(before);
            }
        } finally {removeFault();}
        for(String route:routes) {assertThat(get(route,patient,"PATIENT").statusCode()).as(route).isEqualTo(200);}
        assertThat(audits(patient)).isEqualTo(before+3);
    }
    private HttpResponse<String> get(String path,String actorId,String role) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/patients/"+patient+path)).timeout(Duration.ofSeconds(20))
                .header("X-Test-Actor-Id",actorId).header("X-Test-Actor-Role",role).GET().build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");return response;
    }
    private String encounter(String cls) {
        String encounter=id();owner.update("INSERT INTO encounter(id,patient_id,class,practitioner_id) VALUES (?,?,?,?)",encounter,patient,cls,clinician);return encounter;
    }
    private String row(String enc,String code,String unit,String value,String effective,boolean released) {
        return source(enc,code,unit,value,effective,"final",null,released,null,false,"2010-01-01");
    }
    private String source(String enc,String code,String unit,String value,String effective,String status,String correctionOf,
                          boolean released,String components,boolean standardized,String contextBirth) {
        String observation=id();Instant instant=Instant.parse(effective);
        owner.update("""
                INSERT INTO observation(id,patient_id,encounter_id,code,unit_code,value_num,effective,effective_submicro_nanos,status,
                    correction_of,component,birth_date_context,sex_context,standardized_creatinine)
                VALUES (?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS date),'female',?)
                """,observation,patient,enc,code,unit,value==null?null:new BigDecimal(value),instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                instant.getNano()%1000,status,correctionOf,components,contextBirth,standardized);
        owner.update("INSERT INTO observation_review(observation_id,criticality) VALUES (?,'UNKNOWN')",observation);
        if(released) {release(observation);}return observation;
    }
    private void release(String observation) {
        // Test-only state fixture; dataset/workflow checks must invoke the actual local actions.
        owner.update("UPDATE observation_review SET criticality='NONCRITICAL',reviewed_by=?,reviewed_at=clock_timestamp(),released_by=?,released_at=clock_timestamp() WHERE observation_id=?",
                clinician,clinician,observation);
    }
    private void birth(String value) {owner.update("UPDATE patient SET birth_date=CAST(? AS date) WHERE id=?",value,patient);}
    private long audits(String actor) {return jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND actor_id=? AND source='HTTP'",Long.class,patient,actor);}
    private long unverifiedAudits() {return jdbc.queryForObject("SELECT count(*) FROM access_log WHERE patient_id=? AND actor_id IS NULL AND NOT actor_roster_verified",Long.class,patient);}
    private static List<String> ids(JsonNode body,String field) {List<String> ids=new ArrayList<>();body.path(field).forEach(row -> ids.add(row.path("id").asText()));return ids;}
    private static List<String> sourceIds(JsonNode derived) {List<String> ids=new ArrayList<>();derived.path("sources").forEach(row -> ids.add(row.path("observationId").asText()));return ids;}
    private static JsonNode pointForSource(JsonNode trend,String source) {
        for(JsonNode point:trend.path("points")) {if(sourceIds(point).contains(source)) {return point;}}
        throw new AssertionError("Expected derived source not present.");
    }
    private void installFault(boolean deferred) {
        if(!patient.matches("[0-9a-f-]{36}")) {throw new IllegalArgumentException("Synthetic UUID required.");}
        owner.execute("CREATE FUNCTION "+FAULT_FUNCTION+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id=TG_ARGV[0] AND NEW.action IN ('RESULT_LIST','RESULT_DETAIL','TREND') THEN RAISE EXCEPTION 'private synthetic read audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        String timing=deferred?"CREATE CONSTRAINT TRIGGER "+FAULT_TRIGGER+" AFTER INSERT ON access_log DEFERRABLE INITIALLY DEFERRED":"CREATE TRIGGER "+FAULT_TRIGGER+" BEFORE INSERT ON access_log";
        owner.execute(timing+" FOR EACH ROW EXECUTE FUNCTION "+FAULT_FUNCTION+"('"+patient+"')");
    }
    private void removeFault() {
        try {owner.execute("DROP TRIGGER IF EXISTS "+FAULT_TRIGGER+" ON access_log");}
        finally {owner.execute("DROP FUNCTION IF EXISTS "+FAULT_FUNCTION+"()");}
    }
    private static String id() {return UUID.randomUUID().toString();}
}
