package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.clinical.ClinicalModels.Context;
import com.aldercrest.atlas.clinical.ClinicalModels.Measurement;
import com.aldercrest.atlas.clinical.HistoricalContext.SexAssertion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class IngestionRecords {
    private IngestionRecords() { }
    public record PatientRow(String id,LocalDate birthDate,List<SexAssertion> assertions,String provenance) { }
    public record EncounterRow(String id,String patientId,String encounterClass,String practitionerId,String status) { }
    public record ObservationRow(String id,String patientId,String encounterId,Measurement measurement,Instant effective,
                                 String status,String correctionOf,Context context,String provenance,
                                 boolean standardized,boolean critical,String sourceInterpretation) { }
    public record StoredObservation(String id,String patientId,String encounterId,String code,Instant effective) { }
    public record Receipt(String hash,IngestReply reply,List<String> patients) { }
    public record Planned(IngestReply reply,List<PatientRow> patients,List<EncounterRow> encounters,
                          List<ObservationRow> observations,List<String> affectedPatients,boolean accepted) { }
}
