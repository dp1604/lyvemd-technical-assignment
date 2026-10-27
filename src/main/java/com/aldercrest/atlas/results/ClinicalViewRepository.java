package com.aldercrest.atlas.results;

import com.aldercrest.atlas.clinical.*;
import com.aldercrest.atlas.ingest.ExactTime;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.aldercrest.atlas.security.TestActor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

/** Shared stored-rights/view boundary. Call only inside the content+audit action transaction. */
@Repository
public class ClinicalViewRepository {
    public record Access(boolean clinician,Integer proxyAge) { }
    public record State(String releaseState,String criticality,boolean sourceCritical) { }
    public record View(VisibleClinicalSeries series,Map<String,State> states,Map<String,List<String>> correctedBy) { }
    private record Candidate(Observation observation,State state) { }
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ClinicalViewRepository(JdbcTemplate jdbc,Clock clock) {this.jdbc=jdbc;this.clock=clock;}

    public Access access(String patientId,TestActor actor) {
        List<LocalDate> births=jdbc.query("""
                SELECT p.birth_date FROM patient p WHERE p.id=? AND
                ((?='PATIENT' AND p.id=?) OR
                 (?='CLINICIAN' AND EXISTS(SELECT 1 FROM encounter e WHERE e.patient_id=p.id AND e.practitioner_id=?)) OR
                 (?='PROXY' AND EXISTS(SELECT 1 FROM proxy_link x WHERE x.patient_id=p.id AND x.proxy_id=? AND x.active AND x.revoked_at IS NULL)))
                """,(rs,n) -> rs.getObject(1,LocalDate.class),patientId,actor.role(),actor.id(),actor.role(),actor.id(),actor.role(),actor.id());
        if(births.isEmpty()) {return null;}
        if("PROXY".equals(actor.role())) {
            LocalDate birth=births.getFirst(),today=LocalDate.now(clock.withZone(ZoneOffset.UTC));
            if(birth==null || birth.isAfter(today)) {return null;}
            int age=Period.between(birth,today).getYears(); // Adopted Feb29->Mar1 completed-year rule.
            return age>=18?null:new Access(false,age);
        }
        return new Access("CLINICIAN".equals(actor.role()),null);
    }

    public View view(String patientId,Access access) {
        // This query never depends on a requested observation ID. Protected and missing detail IDs
        // follow the same patient-view query, ancestry filtering and response/audit path.
        List<Candidate> candidates=jdbc.query("""
                SELECT o.id,o.patient_id,o.encounter_id,o.code,o.value_num,o.effective,o.effective_submicro_nanos,
                       o.status,o.correction_of,o.component::text,o.birth_date_context,o.sex_context,o.standardized_creatinine,
                       (o.status IN ('final','amended','corrected') AND r.reviewed_at IS NOT NULL AND r.released_at IS NOT NULL
                        AND r.criticality IN ('NONCRITICAL','CRITICAL')
                        AND (r.criticality<>'CRITICAL' OR r.critical_path_at IS NOT NULL)
                        AND (NOT o.source_critical OR r.criticality='CRITICAL')) AS released,
                       COALESCE(r.criticality,'UNKNOWN') AS criticality,o.source_critical
                FROM observation o LEFT JOIN encounter e ON e.id=o.encounter_id AND e.patient_id=o.patient_id
                LEFT JOIN observation_review r ON r.observation_id=o.id
                WHERE o.patient_id=? AND (? OR
                  (o.status IN ('final','amended','corrected') AND r.reviewed_at IS NOT NULL AND r.released_at IS NOT NULL
                   AND r.criticality IN ('NONCRITICAL','CRITICAL')
                   AND (r.criticality<>'CRITICAL' OR r.critical_path_at IS NOT NULL)
                   AND (NOT o.source_critical OR r.criticality='CRITICAL')))
                  AND (CAST(? AS integer) IS NULL OR ?<12 OR e.class IS NULL OR e.class NOT IN ('ABSL','SUBU','MHEV'))
                ORDER BY o.effective DESC,o.effective_submicro_nanos DESC,o.id
                """,(rs,n) -> {
                    List<Component> components=new ArrayList<>();
                    String raw=rs.getString("component");
                    if(raw!=null) {
                        try {for(var component:IngestionJson.mapper().readTree(raw)) {
                            if(!component.has("value_num") || !(component.get("value_num").isNull() || component.get("value_num").isNumber())) {
                                throw new IllegalStateException("Invalid stored clinical component.");
                            }
                            Analyte analyte=Analyte.fromCode(component.path("code").asText());
                            if(!analyte.unitCode().equals(component.path("unit_code").asText())) {throw new IllegalStateException("Invalid stored component unit.");}
                            components.add(new Component(analyte,new CanonicalQuantity(
                                    component.path("value_num").isNull()?null:component.path("value_num").decimalValue(),
                                    analyte.unitCode(),component.path("source_unit_code").isNull()?null:component.path("source_unit_code").asText())));
                        }} catch(java.io.IOException error) {throw new IllegalStateException("Invalid stored clinical component.");}
                    }
                    String sex=rs.getString("sex_context");
                    Context context=new Context(rs.getObject("birth_date_context",LocalDate.class),sex==null?Sex.UNKNOWN:Sex.valueOf(sex.toUpperCase(Locale.ROOT)));
                    Observation observation=new Observation(rs.getString("id"),rs.getString("patient_id"),rs.getString("encounter_id"),
                            Analyte.fromCode(rs.getString("code")),rs.getBigDecimal("value_num"),
                            ExactTime.restore(rs.getObject("effective",OffsetDateTime.class),rs.getInt("effective_submicro_nanos")),
                            rs.getString("status"),rs.getString("correction_of"),components,context,rs.getBoolean("standardized_creatinine"));
                    return new Candidate(observation,new State(rs.getBoolean("released")?"RELEASED":"HELD",rs.getString("criticality"),rs.getBoolean("source_critical")));
                },patientId,access.clinician(),access.proxyAge(),access.proxyAge());
        VisibleClinicalSeries series=VisibleClinicalSeries.filter(patientId,candidates.stream().map(Candidate::observation).toList(),row -> true);
        Set<String> complete=new HashSet<>();series.observations().forEach(row -> complete.add(row.id()));
        Map<String,State> states=new HashMap<>();candidates.forEach(candidate -> {if(complete.contains(candidate.observation().id())) {states.put(candidate.observation().id(),candidate.state());}});
        Map<String,List<String>> successors=new HashMap<>();
        series.observations().forEach(row -> {if(row.correctionOf()!=null) {successors.computeIfAbsent(row.correctionOf(),id -> new ArrayList<>()).add(row.id());}});
        successors.replaceAll((id,children) -> children.stream().sorted().toList());
        return new View(series,Map.copyOf(states),Map.copyOf(successors));
    }
}
