package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.clinical.ClinicalInputException;
import com.aldercrest.atlas.clinical.ClinicalInputNormalizer;
import com.aldercrest.atlas.clinical.HistoricalContext;
import com.aldercrest.atlas.security.TestActor;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.aldercrest.atlas.ingest.ExtensionContent.text;
import static com.aldercrest.atlas.ingest.IngestionRecords.*;

/** Per-call planning validates every entry and relationship before the first clinical INSERT. */
@Component
public class BundlePlanner {
    public static final String ACT_CODE="http://terminology.hl7.org/CodeSystem/v3-ActCode";
    public static final String LOCAL_CLASS="https://example.org/fhir/CodeSystem/atlas-encounter-class";
    private final FhirValidation validation;
    private final IngestionRepository repository;
    public BundlePlanner(FhirValidation validation,IngestionRepository repository) {this.validation=validation;this.repository=repository;}
    public Planned plan(JsonNode bundle,TestActor actor) {return new Planning(actor).plan(bundle);}

    private record Entry(int index,String type,String id,JsonNode resource) { }
    private static final class Invalid extends RuntimeException {
        final String code; Invalid(String code) {super(null,null,false,false);this.code=code;}
    }
    private static void require(boolean condition,String code) {if(!condition) {throw new Invalid(code);}}

    private final class Planning {
        private final TestActor actor;
        private final RawProfile profile=new RawProfile();
        private final ExtensionContent extensions=new ExtensionContent();
        private final List<Entry> entries=new ArrayList<>();
        private final List<EntryIssue> issues=new ArrayList<>();
        private final Map<String,Entry> aliases=new HashMap<>();
        private final Map<String,PatientRow> patients=new LinkedHashMap<>();
        private final Map<String,PatientRow> storedPatients=new HashMap<>();
        private final Map<String,EncounterRow> encounters=new LinkedHashMap<>();
        private final Map<String,ObservationRow> observations=new LinkedHashMap<>();
        private final Set<String> affected=new LinkedHashSet<>();
        Planning(TestActor actor) {this.actor=actor;}

        Planned plan(JsonNode bundle) {
            try {
                profile.fields(bundle,Set.of("resourceType","type","entry","id","meta","identifier"));
                require("Bundle".equals(text(bundle.get("resourceType"))) && "transaction".equals(text(bundle.get("type"))),"TRANSACTION_BUNDLE_REQUIRED");
                if(bundle.has("id")) {require(text(bundle.get("id")).matches("[A-Za-z0-9.-]{1,64}"),"INVALID_BUNDLE");}
                // Unsupported Bundle metadata is rejected rather than silently losing modifiers/security.
                require(!bundle.has("meta") && !bundle.has("identifier"),"UNSUPPORTED_BUNDLE_METADATA");
                JsonNode input=bundle.has("entry") ? profile.array(bundle.get("entry")) : IngestionJson.mapper().createArrayNode();
                require(!bundle.has("entry") || !input.isEmpty(),"EMPTY_ARRAY_NOT_FHIR_JSON");
                require(input.size()<=1000,"ENTRY_COUNT_BOUND");
                for(JsonNode node:input) {add(node);}
            } catch(Invalid error) {return failed(FhirReplies.error(400,error.code));}
            catch(IllegalArgumentException error) {return failed(FhirReplies.error(400,"INVALID_BUNDLE"));}
            for(Entry entry:entries) {if(!bad(entry) && entry.type().equals("Patient")) {attempt(entry,() -> patient(entry));}}
            for(Entry entry:entries) {if(!bad(entry) && entry.type().equals("Encounter")) {attempt(entry,() -> encounter(entry));}}
            for(Entry entry:entries) {if(!bad(entry) && entry.type().equals("Observation")) {attempt(entry,() -> observation(entry));}}
            checkCorrections();
            if(!issues.isEmpty()) {return failed(FhirReplies.rejected(entries.size(),issues));}
            List<ObservationRow> ordered=new ArrayList<>();
            Set<String> inserted=new HashSet<>();
            while(ordered.size()<observations.size()) {
                int before=ordered.size();
                for(ObservationRow observation:observations.values()) {
                    if(!inserted.contains(observation.id()) && (observation.correctionOf()==null || !observations.containsKey(observation.correctionOf()) || inserted.contains(observation.correctionOf()))) {
                        ordered.add(observation);inserted.add(observation.id());
                    }
                }
                if(before==ordered.size()) {throw new IllegalStateException("Invalid correction plan.");}
            }
            return new Planned(FhirReplies.success(entries.stream().map(e -> e.type()+"/"+e.id()).toList()),
                    List.copyOf(patients.values()),List.copyOf(encounters.values()),List.copyOf(ordered),List.copyOf(affected),true);
        }
        private Planned failed(IngestReply reply) {return new Planned(reply,List.of(),List.of(),List.of(),List.copyOf(affected),false);}
        private boolean bad(Entry entry) {return issues.stream().anyMatch(issue -> issue.index()==entry.index());}
        private void attempt(Entry entry,Runnable action) {
            try {action.run();}
            catch(Invalid error) {issues.add(new EntryIssue(entry.index(),error.code));}
            catch(ClinicalInputException error) {issues.add(new EntryIssue(entry.index(),error.getMessage()));}
            catch(ca.uhn.fhir.parser.DataFormatException error) {issues.add(new EntryIssue(entry.index(),"FHIR_INVALID"));}
            catch(IllegalArgumentException error) {issues.add(new EntryIssue(entry.index(),"INVALID_PROFILE"));}
        }
        private void add(JsonNode node) {
            int index=entries.size();
            JsonNode resource=node.path("resource");
            Entry entry=new Entry(index,resource.path("resourceType").asText(),UUID.randomUUID().toString(),resource);
            entries.add(entry);
            attempt(entry,() -> {
                profile.fields(node,Set.of("fullUrl","resource","request"));
                profile.fields(node.get("request"),Set.of("method","url"));
                require("POST".equals(text(node.path("request").get("method"))) && entry.type().equals(text(node.path("request").get("url"))),"POST_CREATE_REQUIRED");
            });
            attempt(entry,() -> {
                profile.resource(resource);
                require(validation.valid(resource.toString()),"FHIR_INVALID");
                // Strict parser is deliberately separate from the validator; neither may drop unknown fields.
                validation.parse(resource.toString());
            });
            attempt(entry,() -> {
                if(node.has("fullUrl")) {
                    String full=text(node.get("fullUrl"));
                    require(full.matches("urn:uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),"LOCAL_UUID_FULLURL_REQUIRED");
                    alias(full,entry);
                }
                if(resource.has("id")) {alias(entry.type()+"/"+text(resource.get("id")),entry);}
            });
        }
        private void alias(String alias,Entry entry) {
            Entry previous=aliases.putIfAbsent(alias,entry);
            if(previous!=null) {issues.add(new EntryIssue(previous.index(),"DUPLICATE_REFERENCE_IDENTITY"));throw new Invalid("DUPLICATE_REFERENCE_IDENTITY");}
        }
        private void patient(Entry entry) {
            JsonNode raw=entry.resource();
            String birth=raw.has("birthDate")?text(raw.get("birthDate")):null;
            LocalDate full=birth!=null && birth.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")?ExtensionContent.fullDate(birth):null;
            Map<String,Object> provenance=new LinkedHashMap<>();
            provenance.put("birthDateSource",birth);provenance.put("birthDatePrecision",birth==null?"MISSING":full==null?"PARTIAL":"DAY");
            provenance.put("clinicalSexSource",FhirDefinitions.SEX_CONTEXT);provenance.put("administrativeGenderUsed",false);
            PatientRow row=new PatientRow(entry.id(),full,extensions.sexAssertions(raw),FhirReplies.encode(provenance));
            patients.put(entry.id(),row);affected.add(entry.id());
        }
        private PatientRow patientReference(JsonNode reference) {
            String raw=referenceText(reference,"Patient");
            Entry local=aliases.get(raw);
            if(local!=null) {
                require(local.type().equals("Patient") && !bad(local) && patients.containsKey(local.id()),"REFERENCE_UNAVAILABLE");
                return patients.get(local.id());
            }
            String id=relative(raw,"Patient");affected.add(id);
            PatientRow stored=storedPatients.get(id);
            if(stored==null) {stored=repository.authorizedPatient(id,actor);}
            require(stored!=null,"REFERENCE_UNAVAILABLE");storedPatients.put(id,stored);return stored;
        }
        private String referenceText(JsonNode reference,String type) {
            profile.fields(reference,Set.of("reference","type","display"));
            if(reference.has("type")) {require(type.equals(text(reference.get("type"))),"REFERENCE_UNAVAILABLE");}
            return text(reference.get("reference"));
        }
        private String relative(String reference,String type) {
            require(reference.matches(java.util.regex.Pattern.quote(type)+"/[A-Za-z0-9.-]{1,64}"),"REFERENCE_UNAVAILABLE");
            return reference.substring(type.length()+1);
        }
        private String localOrRelative(String reference,String type) {
            Entry local=aliases.get(reference);
            if(local!=null) {require(type.equals(local.type()) && !bad(local),"REFERENCE_UNAVAILABLE");return local.id();}
            return relative(reference,type);
        }
        private void encounter(Entry entry) {
            JsonNode raw=entry.resource();
            PatientRow patient=patientReference(raw.get("subject"));
            JsonNode participants=profile.array(raw.get("participant"));require(participants.size()==1,"ONE_PRINCIPAL_CLINICIAN_REQUIRED");
            String practitioner=relative(referenceText(participants.get(0).get("individual"),"Practitioner"),"Practitioner");
            require(repository.clinician(practitioner),"REFERENCE_UNAVAILABLE");
            String encounterClass=text(raw.path("class").get("code"));String system=text(raw.path("class").get("system"));
            boolean protectedClass=Set.of("ABSL","SUBU","MHEV").contains(encounterClass);
            require(protectedClass?LOCAL_CLASS.equals(system):ACT_CODE.equals(system) && Set.of("AMB","IMP","EMER","HH","VR","ACUTE","NONAC","OBSENC","SS","PRENC").contains(encounterClass),"UNSUPPORTED_ENCOUNTER_CLASS");
            encounters.put(entry.id(),new EncounterRow(entry.id(),patient.id(),encounterClass,practitioner,text(raw.get("status"))));
        }
        private void observation(Entry entry) {
            JsonNode raw=entry.resource();PatientRow patient=patientReference(raw.get("subject"));
            String encounter=null;
            if(raw.has("encounter")) {
                encounter=localOrRelative(referenceText(raw.get("encounter"),"Encounter"),"Encounter");
                EncounterRow local=encounters.get(encounter);
                require(local!=null?local.patientId().equals(patient.id()):repository.encounterMatches(encounter,patient.id()),"REFERENCE_UNAVAILABLE");
            }
            var parsed=(org.hl7.fhir.r4.model.Observation)validation.parse(raw.toString());
            var measurement=new ClinicalInputNormalizer().normalize(parsed);
            var extension=extensions.observation(raw,measurement.analyte());
            String status=text(raw.get("status"));String correction=extension.correctionReference();
            require(Set.of("amended","corrected").contains(status)==(correction!=null),"CORRECTION_LINK_STATUS_REQUIRED");
            if(correction!=null) {correction=localOrRelative(correction,"Observation");}
            var effective=ExactTime.parse(text(raw.get("effectiveDateTime"))).instant();
            var context=new HistoricalContext().resolve(patient.birthDate(),patient.assertions(),effective);
            Map<String,Object> provenance=new LinkedHashMap<>();
            provenance.put("birthDateSource","Patient/"+patient.id());provenance.put("clinicalSexSource",FhirDefinitions.SEX_CONTEXT);
            provenance.put("contextDateUtc",effective.atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString());
            provenance.put("administrativeGenderUsed",false);
            for(String field:new String[]{"code","valueQuantity","component","dataAbsentReason"}) {if(raw.has(field)) {provenance.put("source_"+field,raw.get(field));}}
            Map<String,Object> sourceInterpretation=new LinkedHashMap<>();
            if(raw.has("interpretation")) {sourceInterpretation.put("parent",raw.get("interpretation"));}
            if(raw.has("component")) {sourceInterpretation.put("components",raw.get("component").findValues("interpretation"));}
            observations.put(entry.id(),new ObservationRow(entry.id(),patient.id(),encounter,measurement,effective,status,correction,
                    context,FhirReplies.encode(provenance),extension.standardized(),critical(raw),FhirReplies.encode(sourceInterpretation)));
        }
        private boolean critical(JsonNode raw) {
            List<JsonNode> concepts=new ArrayList<>();
            if(raw.has("interpretation")) {raw.get("interpretation").forEach(concepts::add);}
            if(raw.has("component")) {for(JsonNode part:raw.get("component")) {if(part.has("interpretation")) {part.get("interpretation").forEach(concepts::add);}}}
            for(JsonNode concept:concepts) {for(JsonNode coding:concept.path("coding")) {
                if("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation".equals(coding.path("system").asText())
                        && Set.of("HH","LL","AA").contains(coding.path("code").asText())) {return true;}
            }}
            return false;
        }
        private void checkCorrections() {
            Map<String,Entry> byId=new HashMap<>();entries.forEach(entry -> byId.put(entry.id(),entry));
            Map<String,String> successor=new HashMap<>();
            for(ObservationRow observation:observations.values()) {
                Entry entry=byId.get(observation.id());if(observation.correctionOf()==null) {continue;}
                attempt(entry,() -> {
                    require(!observation.id().equals(observation.correctionOf()),"CORRECTION_CYCLE");
                    String prior=successor.putIfAbsent(observation.correctionOf(),observation.id());
                    if(prior!=null) {issues.add(new EntryIssue(byId.get(prior).index(),"CORRECTION_BRANCH"));throw new Invalid("CORRECTION_BRANCH");}
                    ObservationRow local=observations.get(observation.correctionOf());
                    StoredObservation target=local==null?repository.observation(observation.correctionOf(),observation.patientId()):
                            new StoredObservation(local.id(),local.patientId(),local.encounterId(),local.measurement().analyte().code(),local.effective());
                    require(target!=null,"REFERENCE_UNAVAILABLE");
                    require(target.patientId().equals(observation.patientId()) && target.code().equals(observation.measurement().analyte().code())
                            && Objects.equals(target.encounterId(),observation.encounterId()) && target.effective().equals(observation.effective()),"CORRECTION_CONTEXT_MISMATCH");
                    require(local!=null || !repository.hasSuccessor(target.id()),"CORRECTION_NOT_HEAD");
                });
            }
            for(ObservationRow observation:observations.values()) {
                Set<String> seen=new HashSet<>();ObservationRow cursor=observation;
                while(cursor!=null && cursor.correctionOf()!=null) {
                    if(!seen.add(cursor.id())) {for(String id:seen) {issues.add(new EntryIssue(byId.get(id).index(),"CORRECTION_CYCLE"));} break;}
                    cursor=observations.get(cursor.correctionOf());
                }
            }
        }
    }
}
