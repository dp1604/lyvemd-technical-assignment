package com.aldercrest.atlas.dataset;

import com.aldercrest.atlas.ingest.FhirDefinitions;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fixed synthetic semantic recipe. No database, network, real identity or wall-clock access.
 * Server-generated IDs/audit times are operational outputs, not deterministic semantic inputs.
 */
public final class DatasetRecipe {
    public static final String VERSION="atlas-g6-v1";
    public static final LocalDate AS_OF=LocalDate.of(2026,10,27);
    public static final long SEED=20261027L;
    public static final String CLINICIAN_A=VERSION+"-clinician-a";
    public static final String CLINICIAN_B=VERSION+"-clinician-b";
    public static final String STAFF=VERSION+"-staff";
    public static final String PROXY_TEEN_1=VERSION+"-proxy-teen13";
    public static final String PROXY_TEEN_2=VERSION+"-proxy-teen16";
    public static final String PROXY_EXPIRED=VERSION+"-proxy-expired";
    public static final String PROXY_REVOKED=VERSION+"-proxy-revoked";
    public static final String PROXY_RETAINED=VERSION+"-proxy-retained";
    public static final String INITIAL="INITIAL",CORRECTION="CORRECTION",REPLAY="REPLAY";
    public static final String NONCRITICAL="NONCRITICAL",HOLD="HOLD",HOLD_CRITICAL="HOLD_CRITICAL",CRITICAL_FULL="CRITICAL_FULL";
    private static final String LOINC="http://loinc.org",UCUM="http://unitsofmeasure.org";
    private static final String ACT_CODE="http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String LOCAL_CLASS="https://example.org/fhir/CodeSystem/atlas-encounter-class";
    private static final String INTERPRETATION="http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
    private static final Set<String> PROTECTED=Set.of("ABSL","SUBU","MHEV");
    // Sorting is explicit even if a future controlled caller supplies a Map.of component.
    private static final ObjectMapper JSON=IngestionJson.mapper().copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public record PatientSpec(String key,LocalDate birthDate,String sex,String clinicianId) { }
    public record EncounterSpec(String key,String patientKey,String classCode) { }
    public record ObservationSpec(String key,String patientKey,String encounterKey,String code,String unitCode,
            BigDecimal value,Instant effective,boolean standardized,boolean sourceCritical,String correctionOf,
            String releaseMode,String phase,List<Map<String,Object>> components) {
        public ObservationSpec {
            components=components==null?List.of():components.stream().map(DatasetRecipe::freezeMap).toList();
        }
    }

    private final List<PatientSpec> patients;
    private final List<EncounterSpec> encounters;
    private final List<ObservationSpec> observations;
    private final Map<String,List<String>> scenarios;
    private DatasetRecipe(List<PatientSpec> patients,List<EncounterSpec> encounters,List<ObservationSpec> observations,
            Map<String,List<String>> scenarios) {
        this.patients=List.copyOf(patients);this.encounters=List.copyOf(encounters);this.observations=List.copyOf(observations);
        Map<String,List<String>> copy=new LinkedHashMap<>();scenarios.forEach((key,value)->copy.put(key,List.copyOf(value)));
        this.scenarios=Collections.unmodifiableMap(copy);
    }
    public List<PatientSpec> patients() {return patients;}
    public List<EncounterSpec> encounters() {return encounters;}
    public List<ObservationSpec> observations() {return observations;}
    public Map<String,List<String>> scenarios() {return scenarios;}

    /** At least10 fixed scenario personas. Additional rows go only to background personas10+. */
    public static DatasetRecipe create(int patientCount,int observationCount) {
        if(patientCount<10 || patientCount>1000 || observationCount<0) {throw new IllegalArgumentException("INVALID_DATASET_SIZE");}
        List<PatientSpec> patients=new ArrayList<>();List<EncounterSpec> encounters=new ArrayList<>();
        List<ObservationSpec> observations=new ArrayList<>();Map<String,List<String>> scenarios=new LinkedHashMap<>();
        String[] births={"1986-03-12","2013-04-15","2010-02-28","2012-07-01","1980-08-20",
                "1988-11-09","1975-01-01","2008-05-10","2016-09-01","1985-02-03"};
        for(int index=0;index<patientCount;index++) {
            String key=patientKey(index);LocalDate birth=index<10?LocalDate.parse(births[index]):LocalDate.of(1970+index%30,1+index%12,1+index%27);
            String sex=index==3 || index==5?"female":index<10?"male":index%17==0?"unknown":index%2==0?"male":"female";
            patients.add(new PatientSpec(key,birth,sex,index%2==0?CLINICIAN_A:CLINICIAN_B));
            encounters.add(new EncounterSpec(key+"-ordinary",key,"AMB"));
        }
        add(observations,"p000-creatinine-mgdl","p000","2160-0","mg/dL","1","2026-08-01T08:00:00Z",true);
        add(observations,"p000-creatinine-umol","p000","2160-0","umol/L","88.4","2026-08-02T08:00:00Z",true);
        scenarios.put("creatinineBothUnits",List.of("p000-creatinine-mgdl","p000-creatinine-umol"));
        List<String> teenProtected=new ArrayList<>();
        for(String patient:List.of("p001","p002")) {
            add(observations,patient+"-ordinary-na",patient,"2951-2","mmol/L","140","2026-08-01T08:00:00Z",false);
            int month=5;
            for(String cls:List.of("ABSL","SUBU","MHEV")) {
                String encounter=patient+"-"+cls,key=patient+"-protected-"+cls.toLowerCase(java.util.Locale.ROOT);
                encounters.add(new EncounterSpec(encounter,patient,cls));
                observations.add(spec(key,patient,encounter,"2951-2","mmol/L","141","2026-0"+(month++)+"-01T08:00:00Z",false,false,null,NONCRITICAL,INITIAL,List.of()));
                teenProtected.add(key);
            }
        }
        scenarios.put("teenProtected",teenProtected);
        add(observations,"p003-hb-before14","p003","718-7","g/dL","11.5","2026-06-30T08:00:00Z",false);
        add(observations,"p003-hb-after14","p003","718-7","g/dL","11.5","2026-07-01T08:00:00Z",false);
        scenarios.put("haemoglobinAge14",List.of("p003-hb-before14","p003-hb-after14"));
        add(observations,"p004-k-original","p004","2947-0","mmol/L","4","2026-08-15T08:00:00Z",false);
        observations.add(spec("p004-k-amended","p004","p004-ordinary","2947-0","mmol/L","5.8","2026-08-15T08:00:00Z",
                false,false,"p004-k-original",NONCRITICAL,CORRECTION,List.of()));
        scenarios.put("amendment",List.of("p004-k-original","p004-k-amended"));
        add(observations,"p005-weight-only","p005","29463-7","kg","74","2026-09-01T08:00:00Z",false);
        scenarios.put("weightWithoutHeight",List.of("p005-weight-only"));
        List<String> multiYear=new ArrayList<>();
        for(int month=0;month<94;month++) {
            String key="p006-na-"+String.format(java.util.Locale.ROOT,"%03d",month);
            Instant effective=YearMonth.of(2019,1).plusMonths(month).atDay(1).atTime(8,0).toInstant(ZoneOffset.UTC);
            observations.add(new ObservationSpec(key,"p006","p006-ordinary","2951-2","mmol/L",BigDecimal.valueOf(136+month%8),
                    effective,false,false,null,NONCRITICAL,INITIAL,List.of()));multiYear.add(key);
        }
        scenarios.put("multiYear",multiYear);
        add(observations,"p007-expired-proxy-na","p007","2951-2","mmol/L","140","2026-09-01T08:00:00Z",false);
        scenarios.put("expiredProxy",List.of("p007-expired-proxy-na"));
        add(observations,"p008-proxy-na","p008","2951-2","mmol/L","140","2026-09-01T08:00:00Z",false);
        scenarios.put("revokedProxy",List.of("p008-proxy-na"));
        observations.add(spec("p009-held-na","p009","p009-ordinary","2951-2","mmol/L","140","2026-09-01T08:00:00Z",false,false,null,HOLD,INITIAL,List.of()));
        observations.add(spec("p009-held-critical-k","p009","p009-ordinary","2947-0","mmol/L","6.2","2026-09-02T08:00:00Z",false,true,null,HOLD_CRITICAL,INITIAL,List.of()));
        observations.add(spec("p009-released-critical-k","p009","p009-ordinary","2947-0","mmol/L","6.3","2026-09-03T08:00:00Z",false,true,null,CRITICAL_FULL,INITIAL,List.of()));
        scenarios.put("criticalWorkflow",List.of("p009-held-na","p009-held-critical-k","p009-released-critical-k"));
        observations.add(spec("p000-replayed-na","p000","p000-ordinary","2951-2","mmol/L","140","2026-09-01T08:00:00Z",false,false,null,NONCRITICAL,REPLAY,List.of()));
        scenarios.put("replay",List.of("p000-replayed-na"));

        int remaining=observationCount-observations.size(),background=patientCount-10;
        if(remaining<0 || (background==0 && remaining!=0) || (long)remaining>(long)background*998) {
            throw new IllegalArgumentException("INVALID_DATASET_SIZE");
        }
        FixedRandom random=new FixedRandom(SEED);
        for(int index=10;index<patientCount;index++) {
            int count=remaining/background+((index-10)<remaining%background?1:0);
            for(int row=0;row<count;row++) {observations.add(background(index,row,count,random));}
        }
        if(observations.size()!=observationCount) {throw new IllegalStateException("DATASET_COUNT_MISMATCH");}
        return new DatasetRecipe(patients,encounters,observations,scenarios);
    }

    /** Source observation entries deliberately precede the Patient/Encounter forward-reference targets. */
    public String initialBundle(String patientKey) {
        PatientSpec patient=patients.stream().filter(row->row.key().equals(patientKey)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("UNKNOWN_DATASET_PATIENT"));
        List<Map<String,Object>> entries=new ArrayList<>();
        observations.stream().filter(row->row.patientKey().equals(patientKey) && INITIAL.equals(row.phase())).forEach(row ->
                entries.add(entry(row.key(),observationResource(row,urn(patientKey),urn(row.encounterKey()),null),"Observation")));
        encounters.stream().filter(row->row.patientKey().equals(patientKey)).forEach(row -> entries.add(entry(row.key(),encounterResource(row,patient),"Encounter")));
        entries.add(entry(patientKey,patientResource(patient),"Patient"));
        if(entries.size()>1000) {throw new IllegalStateException("DATASET_BUNDLE_ENTRY_BOUND");}
        Map<String,Object> bundle=map("resourceType","Bundle","type","transaction","entry",entries);
        try {
            String json=JSON.writeValueAsString(bundle);
            if(json.getBytes(StandardCharsets.UTF_8).length>2*1024*1024) {throw new IllegalStateException("DATASET_BUNDLE_BYTE_BOUND");}
            return json;
        } catch(JsonProcessingException error) {throw new IllegalStateException("DATASET_ENCODING_FAILED");}
    }

    public static String urn(String key) {
        if(key==null || !key.matches("[A-Za-z0-9.-]{1,64}")) {throw new IllegalArgumentException("INVALID_LOGICAL_KEY");}
        return "urn:uuid:"+UUID.nameUUIDFromBytes((VERSION+"/"+SEED+"/"+key).getBytes(StandardCharsets.UTF_8));
    }

    public static Map<String,Object> observationResource(ObservationSpec spec,String patientRef,String encounterRef,String correctionRef) {
        if(spec==null || patientRef==null || patientRef.isBlank() || encounterRef==null || encounterRef.isBlank()
                || (spec.correctionOf()!=null && (correctionRef==null || correctionRef.isBlank()))
                || (spec.correctionOf()==null && correctionRef!=null)) {throw new IllegalArgumentException("INVALID_DATASET_REFERENCE");}
        Map<String,Object> resource=map("resourceType","Observation","id",spec.key(),"status",spec.correctionOf()==null?"final":"amended",
                "code",concept(LOINC,spec.code()),"subject",map("reference",patientRef),"encounter",map("reference",encounterRef),
                "effectiveDateTime",spec.effective().toString());
        if(!spec.components().isEmpty()) {resource.put("component",spec.components());}
        else {
            if(spec.value()==null) {throw new IllegalArgumentException("DATASET_VALUE_REQUIRED");}
            resource.put("valueQuantity",quantity(spec.value(),spec.unitCode()));
        }
        List<Map<String,Object>> extensions=new ArrayList<>();
        if("2160-0".equals(spec.code())) {extensions.add(map("url",FhirDefinitions.STANDARDIZED,"valueBoolean",spec.standardized()));}
        if(correctionRef!=null) {extensions.add(map("url",FhirDefinitions.CORRECTION,"valueReference",map("reference",correctionRef)));}
        if(!extensions.isEmpty()) {resource.put("extension",extensions);}
        if(spec.sourceCritical()) {resource.put("interpretation",List.of(concept(INTERPRETATION,"HH")));}
        return resource;
    }

    private static Map<String,Object> patientResource(PatientSpec patient) {
        return map("resourceType","Patient","id",patient.key(),"active",true,"birthDate",patient.birthDate().toString(),
                "extension",List.of(map("url",FhirDefinitions.SEX_CONTEXT,"extension",List.of(
                        map("url","code","valueCode",patient.sex()),map("url","validFrom","valueDate",patient.birthDate().toString())))));
    }
    private static Map<String,Object> encounterResource(EncounterSpec encounter,PatientSpec patient) {
        return map("resourceType","Encounter","id",encounter.key(),"status","finished",
                "class",map("system",PROTECTED.contains(encounter.classCode())?LOCAL_CLASS:ACT_CODE,"code",encounter.classCode()),
                "subject",map("reference",urn(patient.key())),
                "participant",List.of(map("individual",map("reference","Practitioner/"+patient.clinicianId()))));
    }
    private static Map<String,Object> entry(String key,Map<String,Object> resource,String type) {
        return map("fullUrl",urn(key),"resource",resource,"request",map("method","POST","url",type));
    }
    private static ObservationSpec background(int patientIndex,int row,int count,FixedRandom random) {
        String patient=patientKey(patientIndex),code,unit;BigDecimal value;boolean standardized=false;
        List<Map<String,Object>> components=List.of();
        switch(row%8) {
            case 0 -> {
                code="85354-9";unit="mm[Hg]";value=null;
                components=List.of(component("8480-6",BigDecimal.valueOf(110+random.nextInt(41)),unit),
                        component("8462-4",BigDecimal.valueOf(60+random.nextInt(35)),unit));
            }
            case 1 -> {code="8302-2";unit="cm";value=BigDecimal.valueOf(150+random.nextInt(41));}
            case 2 -> {code="29463-7";unit="kg";value=BigDecimal.valueOf(50+random.nextInt(51));}
            case 3 -> {code="718-7";unit="g/dL";value=BigDecimal.valueOf(100+random.nextInt(91),1);}
            case 4 -> {code="2951-2";unit="mmol/L";value=BigDecimal.valueOf(130+random.nextInt(21));}
            case 5 -> {code="2947-0";unit="mmol/L";value=BigDecimal.valueOf(30+random.nextInt(31),1);}
            case 6 -> {
                code="2160-0";boolean si=(row/8+patientIndex)%2==0;unit=si?"umol/L":"mg/dL";
                value=BigDecimal.valueOf(6+random.nextInt(15),1);if(si) {value=value.multiply(new BigDecimal("88.4"));}
                standardized=(row/8+patientIndex)%7!=0;
            }
            default -> {code="4548-4";unit="%";value=BigDecimal.valueOf(40+random.nextInt(61),1);}
        }
        long daysBack=1L+(long)(count-1-row)*10+patientIndex%7;
        Instant effective=AS_OF.minusDays(daysBack).atTime(10,patientIndex%60).toInstant(ZoneOffset.UTC);
        return new ObservationSpec(patient+"-o"+String.format(java.util.Locale.ROOT,"%04d",row),patient,patient+"-ordinary",code,unit,value,
                effective,standardized,false,null,NONCRITICAL,INITIAL,components);
    }
    private static Map<String,Object> component(String code,BigDecimal value,String unit) {return map("code",concept(LOINC,code),"valueQuantity",quantity(value,unit));}
    private static Map<String,Object> quantity(BigDecimal value,String unit) {return map("value",value,"unit",unit,"system",UCUM,"code",unit);}
    private static Map<String,Object> concept(String system,String code) {return map("coding",List.of(map("system",system,"code",code)));}
    private static void add(List<ObservationSpec> rows,String key,String patient,String code,String unit,String value,String effective,boolean standardized) {
        rows.add(spec(key,patient,patient+"-ordinary",code,unit,value,effective,standardized,false,null,NONCRITICAL,INITIAL,List.of()));
    }
    private static ObservationSpec spec(String key,String patient,String encounter,String code,String unit,String value,String effective,
            boolean standardized,boolean critical,String correction,String release,String phase,List<Map<String,Object>> components) {
        return new ObservationSpec(key,patient,encounter,code,unit,value==null?null:new BigDecimal(value),Instant.parse(effective),
                standardized,critical,correction,release,phase,components);
    }
    private static String patientKey(int index) {return String.format(java.util.Locale.ROOT,"p%03d",index);}
    private static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();for(int index=0;index<pairs.length;index+=2) {result.put((String)pairs[index],pairs[index+1]);}return result;
    }
    private static Map<String,Object> freezeMap(Map<String,Object> input) {
        Map<String,Object> result=new LinkedHashMap<>();input.forEach((key,value)->result.put(key,freeze(value)));return Collections.unmodifiableMap(result);
    }
    @SuppressWarnings("unchecked") private static Object freeze(Object value) {
        if(value instanceof Map<?,?> map) {return freezeMap((Map<String,Object>)map);}
        if(value instanceof List<?> list) {return list.stream().map(DatasetRecipe::freeze).toList();}return value;
    }
    /** Explicit64-bit LCG; Java long overflow and unsigned remainder make the algorithm fixed. */
    private static final class FixedRandom {
        private long state;private FixedRandom(long seed) {state=seed;}
        private int nextInt(int bound) {state=state*6364136223846793005L+1442695040888963407L;return (int)Long.remainderUnsigned(state,bound);}
    }
}
