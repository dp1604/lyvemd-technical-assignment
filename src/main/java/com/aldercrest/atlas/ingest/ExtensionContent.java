package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.clinical.Analyte;
import com.aldercrest.atlas.clinical.HistoricalContext;
import com.aldercrest.atlas.clinical.HistoricalContext.SexAssertion;
import com.aldercrest.atlas.clinical.ClinicalModels.Sex;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExtensionContent {
    private final RawProfile profile=new RawProfile();
    public List<SexAssertion> sexAssertions(JsonNode patient) {
        List<SexAssertion> assertions=new ArrayList<>();
        for(JsonNode extension:extensions(patient)) {
            profile.fields(extension,Set.of("url","extension"));
            if(!FhirDefinitions.SEX_CONTEXT.equals(text(extension.get("url")))) {fail();}
            Map<String,JsonNode> children=new HashMap<>();
            for(JsonNode child:profile.array(extension.get("extension"))) {
                String name=text(child.get("url"));
                if(!Set.of("code","validFrom","validToExclusive").contains(name) || children.putIfAbsent(name,child)!=null) {fail();}
                profile.fields(child,Set.of("url",name.equals("code") ? "valueCode" : "valueDate"));
            }
            if(!children.containsKey("code") || !children.containsKey("validFrom")) {fail();}
            String code=text(children.get("code").get("valueCode"));
            if(!Set.of("male","female","unknown").contains(code)) {fail();}
            LocalDate from=fullDate(text(children.get("validFrom").get("valueDate")));
            LocalDate to=children.containsKey("validToExclusive") ? fullDate(text(children.get("validToExclusive").get("valueDate"))) : null;
            assertions.add(new SexAssertion(Sex.valueOf(code.toUpperCase(java.util.Locale.ROOT)),from,to));
        }
        return new HistoricalContext().validate(assertions);
    }

    public record ObservationExtensions(String correctionReference,boolean standardized) { }
    public ObservationExtensions observation(JsonNode observation,Analyte analyte) {
        String correction=null; boolean standardized=false; boolean seenStandardized=false;
        for(JsonNode extension:extensions(observation)) {
            String url=text(extension.get("url"));
            if(FhirDefinitions.CORRECTION.equals(url)) {
                profile.fields(extension,Set.of("url","valueReference"));
                profile.fields(extension.get("valueReference"),Set.of("reference"));
                if(correction!=null) {fail();}
                correction=text(extension.get("valueReference").get("reference"));
            } else if(FhirDefinitions.STANDARDIZED.equals(url)) {
                profile.fields(extension,Set.of("url","valueBoolean"));
                if(seenStandardized || analyte!=Analyte.CREATININE || !extension.path("valueBoolean").isBoolean()) {fail();}
                seenStandardized=true; standardized=extension.get("valueBoolean").booleanValue();
            } else {fail();}
        }
        return new ObservationExtensions(correction,standardized);
    }
    private JsonNode extensions(JsonNode resource) {return resource.has("extension") ? profile.array(resource.get("extension")) : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();}
    public static String text(JsonNode value) {
        if(value==null || !value.isTextual() || value.textValue().isEmpty()) {throw new IllegalArgumentException("TEXT_REQUIRED");}
        return value.textValue();
    }
    public static LocalDate fullDate(String text) {
        if(!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {throw new IllegalArgumentException("FULL_DATE_REQUIRED");}
        return LocalDate.parse(text);
    }
    private static void fail() {throw new IllegalArgumentException("INVALID_SUPPORTED_EXTENSION");}
}
