package com.aldercrest.atlas.dataset;

import com.aldercrest.atlas.AtlasApplication;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.aldercrest.atlas.security.TestActor;
import com.aldercrest.atlas.workflow.WorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Explicit synthetic-data operator tool; never an HTTP endpoint or production ingestion shortcut. */
public final class DatasetTool implements AutoCloseable {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=IngestionJson.mapper().copy()
            .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private record Reply(int status,String body,JsonNode json) { }
    private final DatasetRecipe recipe;
    private final URI base;
    private final Path directory;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectNode manifest;
    private DatasetTool(DatasetRecipe recipe,URI base,Path directory) {
        this.recipe=recipe;this.base=base;this.directory=directory;
        manifest=JSON.createObjectNode();manifest.put("version",DatasetRecipe.VERSION);manifest.put("seed",DatasetRecipe.SEED);
        manifest.put("asOf",DatasetRecipe.AS_OF.toString());manifest.put("configuredPatients",recipe.patients().size());
        manifest.put("configuredObservations",recipe.observations().size());
        manifest.putObject("patients");manifest.putObject("encounters");manifest.putObject("observations");manifest.putObject("requests");
    }
    public static int run(String[] args) {
        try {
            if(args.length<3 || !"--dataset".equals(args[0])) {throw new IllegalArgumentException();}
            String mode=args[1];Path directory=Path.of(args[2]).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            if("roster".equals(mode) || "relationships".equals(mode)) {
                if("roster".equals(mode)) {DatasetFixtureBootstrap.checkConfiguration(directory.resolve("recipe-config.json"));}
                DatasetFixtureBootstrap.run(mode,directory.resolve("load-manifest.json"));
                System.out.println("ATLAS_DATASET FIXTURES_COMPLETED");return 0;
            }
            if(args.length!=6) {throw new IllegalArgumentException();}
            URI base=localBase(args[3]);
            DatasetRecipe recipe=DatasetRecipe.create(Integer.parseInt(args[4]),Integer.parseInt(args[5]));
            try(DatasetTool tool=new DatasetTool(recipe,base,directory)) {
            if("generate".equals(mode)) {tool.generate();}
            else if("load".equals(mode) || "verify".equals(mode)) {
                SpringApplication application=new SpringApplication(AtlasApplication.class);
                application.setWebApplicationType(WebApplicationType.NONE);application.setLogStartupInfo(false);
                try(var context=application.run()) {
                    WorkflowService workflow=context.getBean(WorkflowService.class);
                    if("load".equals(mode)) {tool.load(workflow);}
                    else {tool.restoreManifest();tool.verify(workflow);}
                }
            } else {throw new IllegalArgumentException();}
            }
            System.out.println("ATLAS_DATASET "+mode.toUpperCase(Locale.ROOT)+"_COMPLETED");return 0;
        } catch(Exception error) {
            // Payloads, identities, SQL and exception messages never reach ordinary operator logs.
            System.err.println("ATLAS_DATASET "+(error instanceof DatasetFailure failure?failure.category:"ACTION_FAILED"));return 2;
        }
    }
    public static URI localBase(String value) {
        URI uri=URI.create(value);
        if(!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort()<1024 || uri.getPort()>65535
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {throw new IllegalArgumentException("LOCAL_TARGET_REQUIRED");}
        return uri;
    }
    private void generate() throws Exception {
        String config=DatasetRecipe.VERSION+":"+DatasetRecipe.SEED+":"+DatasetRecipe.AS_OF+":"+recipe.patients().size()+":"+recipe.observations().size();
        // Bind the recipe implementation as well as its chosen counts; generated wire input hashes
        // make a changed recipe conflict rather than silently append a different dataset.
        MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(config.getBytes(StandardCharsets.UTF_8));
        for(var patient:recipe.patients()) {digest.update(recipe.initialBundle(patient.key()).getBytes(StandardCharsets.UTF_8));}
        for(var observation:recipe.observations()) {
            digest.update(JSON.writeValueAsBytes(DatasetRecipe.observationResource(observation,"Patient/"+observation.patientKey(),
                    "Encounter/"+observation.encounterKey(),observation.correctionOf()==null?null:"Observation/"+observation.correctionOf())));
            digest.update((observation.phase()+":"+observation.releaseMode()).getBytes(StandardCharsets.UTF_8));
        }
        String recipeHash=HexFormat.of().formatHex(digest.digest());
        ObjectNode descriptor=JSON.createObjectNode();descriptor.put("version",DatasetRecipe.VERSION);descriptor.put("seed",DatasetRecipe.SEED);
        descriptor.put("asOf",DatasetRecipe.AS_OF.toString());descriptor.put("patients",recipe.patients().size());
        descriptor.put("sourceObservations",recipe.observations().size());descriptor.put("recipeHash",recipeHash);
        descriptor.put("markerKey",DatasetRecipe.VERSION+"-config-"+recipeHash);
        Path descriptorPath=directory.resolve("recipe-config.json");
        if(Files.exists(descriptorPath)) {
            JsonNode prior=JSON.readTree(Files.readAllBytes(descriptorPath));
            require(prior.path("version").asText().equals(DatasetRecipe.VERSION) && prior.path("seed").asLong()==DatasetRecipe.SEED
                    && prior.path("asOf").asText().equals(DatasetRecipe.AS_OF.toString()) && prior.path("patients").asInt()==recipe.patients().size()
                    && prior.path("sourceObservations").asInt()==recipe.observations().size()
                    && prior.path("recipeHash").asText().equals(recipeHash) && prior.path("markerKey").asText().equals(descriptor.path("markerKey").asText()),"EXISTING_RECIPE_JOURNAL_CONFLICT");
        }
        Files.createDirectories(directory.resolve("requests"));
        for(var patient:recipe.patients()) {writeBytes(directory.resolve("requests/initial-"+patient.key()+".json"),recipe.initialBundle(patient.key()).getBytes(StandardCharsets.UTF_8));}
        writeJson(directory.resolve("recipe-config.json"),descriptor);
    }
    private void load(WorkflowService workflow) throws Exception {
        ObjectNode descriptor=(ObjectNode)JSON.readTree(Files.readAllBytes(directory.resolve("recipe-config.json")));
        require(descriptor.path("version").asText().equals(DatasetRecipe.VERSION)
                && descriptor.path("patients").asInt()==recipe.patients().size()
                && descriptor.path("sourceObservations").asInt()==recipe.observations().size()
                && descriptor.path("markerKey").asText().matches("atlas-g6-v1-config-[0-9a-f]{64}"),"CONFIGURATION_REQUIRED");
        Reply marker=post("{\"resourceType\":\"Bundle\",\"type\":\"transaction\"}",descriptor.path("markerKey").asText(),DatasetRecipe.CLINICIAN_A);
        require(marker.status()==200,"CONFIGURATION_MARKER_FAILED");
        int completed=0;
        for(var patient:recipe.patients()) {
            String key=DatasetRecipe.VERSION+"-initial-"+patient.key();
            String body=recipe.initialBundle(patient.key());
            Reply reply=post(body,key,patient.clinicianId());require(reply.status()==200,"INITIAL_BUNDLE_FAILED");
            mapResponse(body,key,patient.clinicianId(),reply);
            // Persist after each successful response. Missing local files are recoverable by exact
            // request replay, because the server receipt, not this file, is the identity authority.
            saveManifest();
            for(var observation:recipe.observations()) {
                if(observation.patientKey().equals(patient.key()) && observation.phase().equals("INITIAL")) {applyRelease(workflow,observation);}
            }
            if(++completed%10==0) {System.out.println("ATLAS_DATASET PATIENT_BUNDLES_COMPLETED="+completed);}
        }
        // Originals have already passed actual review/release before deferred corrections exist.
        for(var observation:recipe.observations()) {
            if(observation.phase().equals("CORRECTION")) {
                String body=existingBundle(observation);String key=DatasetRecipe.VERSION+"-correction-"+observation.key();
                String actor=patient(observation.patientKey()).clinicianId();
                Reply reply=post(body,key,actor);require(reply.status()==200,"CORRECTION_BUNDLE_FAILED");
                mapResponse(body,key,actor,reply);saveManifest();applyRelease(workflow,observation);
            }
        }
        var duplicate=recipe.observations().stream().filter(row -> row.phase().equals("REPLAY")).findFirst().orElseThrow();
        String duplicateBody=existingBundle(duplicate),duplicateActor=patient(duplicate.patientKey()).clinicianId();
        String duplicateKey=DatasetRecipe.VERSION+"-duplicate";
        Reply first=post(duplicateBody,duplicateKey,duplicateActor);require(first.status()==200,"DUPLICATE_FIRST_FAILED");
        mapResponse(duplicateBody,duplicateKey,duplicateActor,first);saveManifest();applyRelease(workflow,duplicate);
        long before=resultsTotal(duplicate.patientKey(),duplicateActor,"CLINICIAN");
        Reply second=post(duplicateBody,duplicateKey,duplicateActor);
        require(first.status()==second.status() && first.body().equals(second.body()),"REPLAY_RESPONSE_DIFFERED");
        require(resultsTotal(duplicate.patientKey(),duplicateActor,"CLINICIAN")==before,"REPLAY_CHANGED_TOTAL");
        manifest.putObject("duplicateEvidence").put("sameResponseBytes",true).put("totalBefore",before).put("totalAfter",before);
        invalidBundleCase();
        manifest.put("loadCompletedAtUtc",Instant.now().toString());saveManifest();
        require(manifest.path("patients").size()==recipe.patients().size() && manifest.path("observations").size()==recipe.observations().size(),"SOURCE_MAPPING_COUNT_MISMATCH");
    }
    private void applyRelease(WorkflowService workflow,DatasetRecipe.ObservationSpec observation) {
        TestActor actor=new TestActor(patient(observation.patientKey()).clinicianId(),"CLINICIAN",true,false);
        String patientId=patientId(observation.patientKey()),id=observationId(observation.key());
        if(observation.releaseMode().equals("HOLD")) {return;}
        boolean critical=observation.sourceCritical();
        require(workflow.review(actor,patientId,id,critical?"CRITICAL":"NONCRITICAL").status()==200,"REVIEW_FAILED");
        if(observation.releaseMode().equals("HOLD_CRITICAL")) {return;}
        if(critical) {require(workflow.attestCritical(actor,patientId,id,true).status()==200,"ATTESTATION_FAILED");}
        require(workflow.release(actor,patientId,id).status()==200,"RELEASE_FAILED");
    }
    private void invalidBundleCase() throws Exception {
        var example=recipe.observations().stream().filter(row -> row.patientKey().equals("p000") && row.phase().equals("REPLAY")).findFirst().orElseThrow();
        ObjectNode valid=(ObjectNode)JSON.valueToTree(DatasetRecipe.observationResource(example,"Patient/"+patientId("p000"),"Encounter/"+encounterId(example.encounterKey()),null));
        valid.put("id","invalid-valid-sibling");
        ObjectNode invalid=valid.deepCopy();invalid.put("id","invalid-bad-sibling");((ObjectNode)invalid.path("valueQuantity")).put("code","invalid-assessment-unit");
        ObjectNode bundle=JSON.createObjectNode().put("resourceType","Bundle").put("type","transaction");
        var entries=bundle.putArray("entry");entries.add(JSON.valueToTree(entry(valid,DatasetRecipe.urn("invalid-valid-sibling"),"Observation")));entries.add(JSON.valueToTree(entry(invalid,DatasetRecipe.urn("invalid-bad-sibling"),"Observation")));
        String body=JSON.writeValueAsString(bundle);writeBytes(directory.resolve("requests/invalid.json"),body.getBytes(StandardCharsets.UTF_8));
        long before=resultsTotal("p000",DatasetRecipe.CLINICIAN_A,"CLINICIAN");
        Reply rejected=post(body,DatasetRecipe.VERSION+"-invalid",DatasetRecipe.CLINICIAN_A);
        require(rejected.status()==400 && "transaction-response".equals(rejected.json().path("type").asText()),"INVALID_BUNDLE_NOT_REJECTED");
        require(rejected.json().at("/entry/0/response/status").asText().startsWith("424")
                && rejected.json().at("/entry/1/response/status").asText().startsWith("400")
                && rejected.json().at("/entry/1/response/outcome/issue/0/expression/0").asText().equals("Bundle.entry[1]"),"INVALID_ENTRY_NOT_IDENTIFIED");
        require(resultsTotal("p000",DatasetRecipe.CLINICIAN_A,"CLINICIAN")==before,"INVALID_BUNDLE_CHANGED_TOTAL");
        manifest.putObject("invalidEvidence").put("httpStatus",400).put("invalidEntryIndex",1).put("totalBefore",before).put("totalAfter",before).put("wholeBundleRejected",true);
    }
    private String existingBundle(DatasetRecipe.ObservationSpec observation) throws Exception {
        String correction=observation.correctionOf()==null?null:"Observation/"+observationId(observation.correctionOf());
        var resource=DatasetRecipe.observationResource(observation,"Patient/"+patientId(observation.patientKey()),"Encounter/"+encounterId(observation.encounterKey()),correction);
        return JSON.writeValueAsString(Map.of("resourceType","Bundle","type","transaction","entry",List.of(entry(resource,DatasetRecipe.urn(observation.key()),"Observation"))));
    }
    private void mapResponse(String body,String key,String actor,Reply reply) throws Exception {
        JsonNode request=JSON.readTree(body);require(reply.json().path("entry").size()==request.path("entry").size(),"ENTRY_RESPONSE_COUNT_MISMATCH");
        Map<String,String> aliases=new HashMap<>();
        recipe.patients().forEach(row -> aliases.put(DatasetRecipe.urn(row.key()),row.key()));
        recipe.encounters().forEach(row -> aliases.put(DatasetRecipe.urn(row.key()),row.key()));
        recipe.observations().forEach(row -> aliases.put(DatasetRecipe.urn(row.key()),row.key()));
        for(int index=0;index<request.path("entry").size();index++) {
            var input=request.path("entry").get(index);String logical=aliases.get(input.path("fullUrl").asText());
            require(logical!=null,"UNKNOWN_LOGICAL_ALIAS");
            String type=input.path("resource").path("resourceType").asText();String location=reply.json().at("/entry/"+index+"/response/location").asText();
            require(location.startsWith(type+"/") && location.substring(type.length()+1).matches("[0-9a-f-]{36}"),"INVALID_ASSIGNED_LOCATION");
            String id=location.substring(type.length()+1);
            if(type.equals("Patient")) {
                ObjectNode row=((ObjectNode)manifest.path("patients")).putObject(logical);row.put("id",id);row.put("actorId",actor);
                row.put("idempotencyKey",key);row.put("requestHash",sha256(body.getBytes(StandardCharsets.UTF_8)));
            } else {((ObjectNode)manifest.path(type.equals("Encounter")?"encounters":"observations")).put(logical,id);}
        }
        ((ObjectNode)manifest.path("requests")).putObject(key).put("actorId",actor).put("requestHash",sha256(body.getBytes(StandardCharsets.UTF_8))).put("status",reply.status());
        writeBytes(directory.resolve("requests/"+key+".json"),body.getBytes(StandardCharsets.UTF_8));
    }
    private Reply post(String body,String key,String actor) throws Exception {
        var request=HttpRequest.newBuilder(base.resolve("/fhir")).timeout(Duration.ofSeconds(60)).header("Content-Type","application/fhir+json")
                .header("X-Test-Actor-Id",actor).header("X-Test-Actor-Role","CLINICIAN").header("X-Idempotency-Key",key)
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        var reply=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(reply.statusCode(),reply.body(),JSON.readTree(reply.body()));
    }
    private Reply get(String patientKey,String suffix,String actor,String role) throws Exception {
        var request=HttpRequest.newBuilder(base.resolve("/patients/"+patientId(patientKey)+suffix)).timeout(Duration.ofSeconds(30))
                .header("X-Test-Actor-Id",actor).header("X-Test-Actor-Role",role).GET().build();
        var reply=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        require(reply.headers().firstValue("Cache-Control").orElse("").contains("no-store"),"PRIVACY_HEADER_MISSING");
        return new Reply(reply.statusCode(),reply.body(),JSON.readTree(reply.body()));
    }
    private long resultsTotal(String patientKey,String actor,String role) throws Exception {
        Reply reply=get(patientKey,"/results?limit=1",actor,role);require(reply.status()==200,"RESULT_READ_FAILED");return reply.json().path("total").asLong(-1);
    }
    private List<JsonNode> allResults(String patientKey,String actor,String role) throws Exception {
        List<JsonNode> rows=new ArrayList<>();long total=Long.MAX_VALUE;
        for(int offset=0;offset<total;offset+=200) {
            Reply reply=get(patientKey,"/results?offset="+offset+"&limit=200",actor,role);require(reply.status()==200,"PAGE_READ_FAILED");
            total=reply.json().path("total").asLong(-1);require(total>=0 && total<=100_000,"UNEXPECTED_RESULT_TOTAL");
            reply.json().path("items").forEach(rows::add);
        }
        require(rows.size()==total,"PAGE_TOTAL_MISMATCH");return rows;
    }
    private void restoreManifest() throws Exception {
        JsonNode saved=JSON.readTree(Files.readAllBytes(directory.resolve("load-manifest.json")));
        require(saved.path("version").asText().equals(DatasetRecipe.VERSION) && saved.path("configuredPatients").asInt()==recipe.patients().size()
                && saved.path("configuredObservations").asInt()==recipe.observations().size(),"MANIFEST_CONFIGURATION_MISMATCH");
        manifest.setAll((ObjectNode)saved);
    }
    private DatasetRecipe.PatientSpec patient(String key) {return recipe.patients().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();}
    private String patientId(String key) {String id=manifest.path("patients").path(key).path("id").asText();require(!id.isEmpty(),"PATIENT_MAPPING_UNAVAILABLE");return id;}
    private String encounterId(String key) {String id=manifest.path("encounters").path(key).asText();require(!id.isEmpty(),"ENCOUNTER_MAPPING_UNAVAILABLE");return id;}
    private String observationId(String key) {String id=manifest.path("observations").path(key).asText();require(!id.isEmpty(),"OBSERVATION_MAPPING_UNAVAILABLE");return id;}
    private static Map<String,Object> entry(Object resource,String fullUrl,String type) {return Map.of("fullUrl",fullUrl,"resource",resource,"request",Map.of("method","POST","url",type));}
    private void saveManifest() throws IOException {writeJson(directory.resolve("load-manifest.json"),manifest);}
    private static void writeJson(Path path,Object value) throws IOException {writeBytes(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));}
    private static void writeBytes(Path path,byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.write(temporary,bytes);try {Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException unsupported) {Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static String sha256(byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception error){throw new IllegalStateException("DIGEST_UNAVAILABLE");}}
    private static final class DatasetFailure extends RuntimeException {
        final String category;
        DatasetFailure(String category) {super(category);this.category=category;}
    }
    private static void require(boolean condition,String category) {if(!condition){throw new DatasetFailure(category);}}
    public void close() {http.close();}

    private void verify(WorkflowService workflow) throws Exception {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        for(String teen:List.of("p001","p002")) {
            int age=Period.between(patient(teen).birthDate(),today).getYears();
            require(age>=12 && age<18,"SCENARIO_PROXY_AGE_WINDOW_EXPIRED");
        }
        require(Period.between(patient("p007").birthDate(),today).getYears()>=18
                && Period.between(patient("p008").birthDate(),today).getYears()<18,"SCENARIO_PROXY_AGE_WINDOW_EXPIRED");
        require(manifest.path("patients").size()==100 && manifest.path("observations").size()==10_000
                && manifest.path("encounters").size()==recipe.encounters().size(),"MAPPING_COUNTS_FAILED");
        Set<String> unique=new HashSet<>();manifest.path("observations").forEach(id -> unique.add(id.asText()));
        require(unique.size()==10_000,"SOURCE_IDS_NOT_UNIQUE");
        long sourceCount=0,derivedCount=0;
        Map<String,List<JsonNode>> rowsByPatient=new HashMap<>();
        for(var patient:recipe.patients()) {
            List<JsonNode> rows=allResults(patient.key(),patient.clinicianId(),"CLINICIAN");rowsByPatient.put(patient.key(),rows);
            Set<String> actual=new HashSet<>();
            for(JsonNode row:rows) {if(row.path("derived").asBoolean()) {derivedCount++;}else {actual.add(row.path("id").asText());sourceCount++;}}
            Set<String> expected=new HashSet<>();recipe.observations().stream().filter(row -> row.patientKey().equals(patient.key()))
                    .forEach(row -> expected.add(observationId(row.key())));
            require(actual.equals(expected),"COHORT_SOURCE_READBACK_FAILED");
        }
        require(sourceCount==10_000,"COHORT_SOURCE_COUNT_FAILED");
        ObjectNode scenarios=JSON.createObjectNode();
        List<JsonNode> creat=rowsByPatient.get("p000").stream().filter(row -> row.path("code").asText().equals("2160-0")).toList();
        require(creat.size()==2 && creat.stream().allMatch(row -> row.path("value").decimalValue().compareTo(java.math.BigDecimal.ONE)==0
                && row.path("unitCode").asText().equals("mg/dL")),"CREATININE_NORMALIZATION_FAILED");
        List<JsonNode> egfr=rowsByPatient.get("p000").stream().filter(row -> row.path("code").asText().equals("98979-8")).toList();
        require(egfr.size()==2 && egfr.stream().allMatch(row -> row.path("computed").asBoolean()
                && row.path("unitCode").asText().equals("mL/s/{1.73_m2}"))
                && egfr.get(0).path("value").decimalValue().compareTo(egfr.get(1).path("value").decimalValue())==0,"CREATININE_DERIVATION_FAILED");
        scenarios.put("DS-01_creatinine_both_units",true);
        String[] teens={"p001","p002"};String[] proxies={"atlas-g6-v1-proxy-teen13","atlas-g6-v1-proxy-teen16"};
        for(int index=0;index<teens.length;index++) {
            String key=teens[index],proxy=proxies[index];
            require(allResults(key,patientId(key),"PATIENT").size()==4 && rowsByPatient.get(key).size()==4,"TEEN_SELF_HISTORY_FAILED");
            require(allResults(key,proxy,"PROXY").size()==1,"TEEN_PROXY_FILTER_FAILED");
            require(get(key,"/trend?code=2951-2",proxy,"PROXY").json().path("total").asInt()==1,"TEEN_TREND_FILTER_FAILED");
            Reply missing=get(key,"/results/00000000-0000-4000-8000-000000000000",proxy,"PROXY");
            for(var observation:recipe.observations()) {
                if(observation.patientKey().equals(key) && !observation.encounterKey().endsWith("-ordinary")) {
                    Reply hidden=get(key,"/results/"+observationId(observation.key()),proxy,"PROXY");
                    require(hidden.status()==404 && missing.status()==404 && hidden.body().equals(missing.body()),"TEEN_DETAIL_DISCLOSURE_FAILED");
                }
            }
            require(get(key,"/access-log",proxy,"PROXY").status()==404,"PROXY_LOG_ACCESS_FAILED");
        }
        scenarios.put("DS-02_teen_protected_encounters",true);
        List<JsonNode> hb=rowsByPatient.get("p003").stream().filter(row -> row.path("code").asText().equals("718-7"))
                .sorted(Comparator.comparing(row -> row.path("effective").asText())).toList();
        require(hb.size()==2 && hb.get(0).path("flag").asText().equals("NORMAL") && hb.get(1).path("flag").asText().equals("LOW")
                && hb.get(0).at("/range/low").decimalValue().compareTo(new java.math.BigDecimal("11"))==0
                && hb.get(1).at("/range/low").decimalValue().compareTo(new java.math.BigDecimal("12"))==0,"AGE14_RANGE_FAILED");
        scenarios.put("DS-03_age14_haemoglobin",true);
        var correction=recipe.observations().stream().filter(row -> row.phase().equals("CORRECTION")).findFirst().orElseThrow();
        Reply corrected=get("p004","/results/"+observationId(correction.key()),patientId("p004"),"PATIENT");
        Reply original=get("p004","/results/"+observationId(correction.correctionOf()),patientId("p004"),"PATIENT");
        require(original.status()==200 && corrected.status()==200
                && corrected.json().path("correctionOf").asText().equals(observationId(correction.correctionOf()))
                && original.json().path("value").decimalValue().compareTo(new java.math.BigDecimal("4"))==0
                && corrected.json().path("value").decimalValue().compareTo(new java.math.BigDecimal("5.8"))==0,"AMENDMENT_RETENTION_FAILED");
        Reply latest=get("p004","/trend?code=2947-0",patientId("p004"),"PATIENT");
        require(latest.status()==200 && latest.json().path("total").asInt()==1
                && latest.json().at("/points/0/id").asText().equals(observationId(correction.key())),"AMENDMENT_TREND_FAILED");
        scenarios.put("DS-04_amendment_both_versions",true);
        List<JsonNode> weightRows=rowsByPatient.get("p005");
        require(weightRows.stream().noneMatch(row -> row.path("code").asText().equals("8302-2")),"WEIGHT_ONLY_HAS_HEIGHT");
        JsonNode bmi=weightRows.stream().filter(row -> row.path("code").asText().equals("39156-5")).findFirst().orElseThrow();
        require(!bmi.path("computed").asBoolean() && bmi.path("value").isNull()
                && bmi.path("cannotComputeReason").asText().equals("HEIGHT_UNAVAILABLE"),"MISSING_HEIGHT_REASON_FAILED");
        scenarios.put("DS-05_weight_without_height",true);
        Reply series=get("p006","/trend?code=2951-2",patientId("p006"),"PATIENT");
        require(series.status()==200 && series.json().path("total").asInt()==94
                && series.json().at("/points/0/effective").asText().startsWith("2019-")
                && series.json().at("/points/93/effective").asText().startsWith("2026-"),"MULTI_YEAR_SERIES_FAILED");
        Instant prior=Instant.MIN;for(JsonNode point:series.json().path("points")) {
            Instant effective=Instant.parse(point.path("effective").asText());require(!effective.isBefore(prior),"TREND_ORDER_FAILED");prior=effective;
        }
        scenarios.put("DS-06_multi_year_series",true);
        require(manifest.at("/duplicateEvidence/sameResponseBytes").asBoolean()
                && manifest.at("/duplicateEvidence/totalBefore").asLong()==manifest.at("/duplicateEvidence/totalAfter").asLong(),"REPLAY_EVIDENCE_FAILED");
        scenarios.put("DS-07_exact_bundle_replay",true);
        require(manifest.at("/invalidEvidence/wholeBundleRejected").asBoolean()
                && manifest.at("/invalidEvidence/httpStatus").asInt()==400
                && manifest.at("/invalidEvidence/totalBefore").asLong()==manifest.at("/invalidEvidence/totalAfter").asLong(),"INVALID_EVIDENCE_FAILED");
        scenarios.put("DS-08_atomic_invalid_bundle",true);
        require(get("p007","/results","atlas-g6-v1-proxy-expired","PROXY").status()==404
                && get("p007","/results",patientId("p007"),"PATIENT").status()==200,"EXPIRED_PROXY_FAILED");
        String self=patientId("p008");
        require(workflow.revokeProxy(new TestActor(self,"PATIENT",true,false),self,"atlas-g6-v1-proxy-revoked").status()==200,"PROXY_REVOKE_FAILED");
        require(get("p008","/results","atlas-g6-v1-proxy-revoked","PROXY").status()==404
                && get("p008","/results","atlas-g6-v1-proxy-retained","PROXY").status()==200,"INDIVIDUAL_PROXY_REVOKE_FAILED");
        List<JsonNode> critical=rowsByPatient.get("p009");
        require(critical.size()==3 && allResults("p009",patientId("p009"),"PATIENT").size()==1,"HELD_CRITICAL_VISIBILITY_FAILED");
        for(var observation:recipe.observations()) {
            if(observation.patientKey().equals("p009")) {
                Reply patientView=get("p009","/results/"+observationId(observation.key()),patientId("p009"),"PATIENT");
                require(patientView.status()==(observation.releaseMode().equals("CRITICAL_FULL")?200:404),"CRITICAL_RELEASE_DETAIL_FAILED");
            }
        }
        require(get("p000","/results","atlas-g6-v1-staff","STAFF").status()==404
                && get("p000","/results",DatasetRecipe.CLINICIAN_B,"CLINICIAN").status()==404
                && get("p000","/access-log","atlas-g6-v1-staff","STAFF").status()==200,"ACTOR_SCOPE_FAILED");
        scenarios.put("additional_held_critical_expired_revoked_scope_cases",true);
        ObjectNode evidence=JSON.createObjectNode();evidence.put("version",DatasetRecipe.VERSION);evidence.put("status","PASS");
        evidence.put("verifiedAtUtc",Instant.now().toString());evidence.put("requestDateUtc",today.toString());
        evidence.put("patients",100);evidence.put("encounters",recipe.encounters().size());evidence.put("sourceObservations",sourceCount);
        evidence.put("derivedRowsInClinicianLists",derivedCount);evidence.set("scenarios",scenarios);
        evidence.set("duplicateEvidence",manifest.path("duplicateEvidence"));evidence.set("invalidEvidence",manifest.path("invalidEvidence"));
        evidence.put("clinicalAttestationScope","Synthetic fixture actions only; no real patient contact or clinical sign-off.");
        writeJson(directory.resolve("verification.json"),evidence);
    }
}
