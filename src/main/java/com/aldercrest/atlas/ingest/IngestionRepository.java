package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.clinical.ClinicalModels.Sex;
import com.aldercrest.atlas.clinical.HistoricalContext;
import com.aldercrest.atlas.clinical.HistoricalContext.SexAssertion;
import com.aldercrest.atlas.security.TestActor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.aldercrest.atlas.ingest.IngestionRecords.*;

/** Only bound data parameters; all clinical reads are called within the audited request transaction. */
@Repository
public class IngestionRepository {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public IngestionRepository(JdbcTemplate jdbc,Clock clock) {this.jdbc=jdbc;this.clock=clock;}
    public boolean known(TestActor actor) {
        return actor.valid() && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM actor WHERE id=? AND role=?)",Boolean.class,actor.id(),actor.role()));
    }
    public boolean clinician(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM actor WHERE id=? AND role='CLINICIAN')",Boolean.class,id));
    }
    public void lockKey(TestActor actor,String key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",actor.id()+":"+actor.role()+":"+key);
    }
    public Receipt receipt(TestActor actor,String key) {
        return jdbc.query("""
                SELECT request_hash,http_status,response_body,patient_ids::text FROM ingestion_receipt
                WHERE actor_id=? AND actor_role=? AND idempotency_key=?
                """,(rs,n) -> {
                    try {return new Receipt(rs.getString(1),new IngestReply(rs.getInt(2),rs.getString(3)),
                            IngestionJson.mapper().readValue(rs.getString(4),new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { }));}
                    catch(java.io.IOException error) {throw new IllegalStateException("Invalid operation receipt.");}
                },actor.id(),actor.role(),key).stream().findFirst().orElse(null);
    }
    public void saveReceipt(TestActor actor,String key,String hash,IngestReply reply,List<String> patients) {
        jdbc.update("""
                INSERT INTO ingestion_receipt(actor_id,actor_role,idempotency_key,request_hash,http_status,response_body,patient_ids)
                VALUES (?,?,?,?,?,?,CAST(? AS jsonb))
                """,actor.id(),actor.role(),key,hash,reply.status(),reply.body(),FhirReplies.encode(patients));
    }
    public PatientRow authorizedPatient(String id,TestActor actor) {
        // Entitlement is tested in the same query before demographic content is returned.
        List<LocalDate> births=jdbc.query("""
                SELECT p.birth_date FROM patient p WHERE p.id=? AND EXISTS
                (SELECT 1 FROM encounter e WHERE e.patient_id=p.id AND e.practitioner_id=?)
                """,(rs,n) -> rs.getObject(1,LocalDate.class),id,actor.id());
        if(births.isEmpty()) {return null;}
        List<SexAssertion> assertions=jdbc.query("""
                SELECT sex,valid_from,valid_to_exclusive FROM patient_sex_context WHERE patient_id=? ORDER BY valid_from
                """,(rs,n) -> new SexAssertion(Sex.valueOf(rs.getString(1).toUpperCase(Locale.ROOT)),
                rs.getObject(2,LocalDate.class),rs.getObject(3,LocalDate.class)),id);
        return new PatientRow(id,births.getFirst(),assertions,"{}");
    }
    public boolean encounterMatches(String id,String patientId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM encounter WHERE id=? AND patient_id=?)",Boolean.class,id,patientId));
    }
    public StoredObservation observation(String id,String patientId) {
        return jdbc.query("""
                SELECT id,patient_id,encounter_id,code,effective,effective_submicro_nanos FROM observation WHERE id=? AND patient_id=?
                """,(rs,n) -> new StoredObservation(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                ExactTime.restore(rs.getObject(5,OffsetDateTime.class),rs.getInt(6))),id,patientId).stream().findFirst().orElse(null);
    }
    public boolean hasSuccessor(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM observation WHERE correction_of=?)",Boolean.class,id));
    }
    public void insert(PatientRow patient) {
        String sex=new HistoricalContext().resolve(patient.birthDate(),patient.assertions(),clock.instant()).sex().name().toLowerCase(Locale.ROOT);
        jdbc.update("INSERT INTO patient(id,sex,birth_date,demographic_provenance) VALUES (?,?,?,CAST(? AS jsonb))",
                patient.id(),sex,patient.birthDate(),patient.provenance());
        for(SexAssertion assertion:patient.assertions()) {
            jdbc.update("INSERT INTO patient_sex_context(patient_id,valid_from,valid_to_exclusive,sex) VALUES (?,?,?,?)",
                    patient.id(),assertion.validFrom(),assertion.validToExclusive(),assertion.sex().name().toLowerCase(Locale.ROOT));
        }
    }
    public void insert(EncounterRow encounter) {
        jdbc.update("INSERT INTO encounter(id,patient_id,class,practitioner_id,fhir_status) VALUES (?,?,?,?,?)",
                encounter.id(),encounter.patientId(),encounter.encounterClass(),encounter.practitionerId(),encounter.status());
    }
    public void insert(ObservationRow observation) {
        List<Map<String,Object>> components=observation.measurement().components().stream().map(component -> {
            Map<String,Object> result=new java.util.LinkedHashMap<>();
            result.put("code",component.analyte().code());result.put("unit_code",component.quantity().unitCode());
            result.put("value_num",component.quantity().value());result.put("source_unit_code",component.quantity().sourceUnitCode());return result;
        }).toList();
        jdbc.update("""
                INSERT INTO observation(id,patient_id,encounter_id,code,unit_code,value_num,effective,status,correction_of,
                component,birth_date_context,sex_context,context_provenance,standardized_creatinine,source_critical,
                effective_submicro_nanos,source_interpretation)
                VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,CAST(? AS jsonb),?,?,?,CAST(? AS jsonb))
                """,observation.id(),observation.patientId(),observation.encounterId(),observation.measurement().analyte().code(),
                observation.measurement().analyte().unitCode(),observation.measurement().quantity().value(),
                new ExactTime(observation.effective()).databaseTime(),observation.status(),observation.correctionOf(),
                components.isEmpty()?null:FhirReplies.encode(components),observation.context().birthDate(),
                observation.context().sex().name().toLowerCase(Locale.ROOT),observation.provenance(),
                observation.standardized(),observation.critical(),new ExactTime(observation.effective()).submicroNanos(),observation.sourceInterpretation());
        jdbc.update("INSERT INTO observation_review(observation_id,criticality) VALUES (?,?)",observation.id(),observation.critical()?"CRITICAL":"UNKNOWN");
    }
}
