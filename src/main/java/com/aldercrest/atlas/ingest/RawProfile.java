package com.aldercrest.atlas.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Reject unhandled semantics before HAPI can discard unknown properties/primitive extensions. */
public final class RawProfile {
    private static final Set<String> SHARED=Set.of("resourceType","id","meta","identifier");
    public void resource(JsonNode resource) {
        String type=resource.path("resourceType").asText();
        Set<String> allowed=new HashSet<>(SHARED);
        allowed.addAll(switch(type) {
            case "Patient" -> Set.of("active","name","gender","birthDate","extension");
            case "Encounter" -> Set.of("status","class","subject","participant");
            case "Observation" -> Set.of("status","code","subject","encounter","effectiveDateTime","valueQuantity","dataAbsentReason","component","interpretation","extension","category");
            default -> throw new IllegalArgumentException("UNSUPPORTED_RESOURCE");
        });
        fields(resource,allowed);
        forbiddenRecursively(resource,0,false);
        if(resource.has("meta")) { fields(resource.get("meta"),Set.of("versionId","lastUpdated")); }
        if(resource.has("identifier")) { for(JsonNode id:array(resource.get("identifier"))) {fields(id,Set.of("use","type","system","value","period"));} }
        if(type.equals("Patient")) {
            if(resource.has("active") && !resource.get("active").asBoolean()) {fail();}
            if(resource.has("name")) {for(JsonNode name:array(resource.get("name"))) {fields(name,Set.of("use","text","family","given","prefix","suffix","period"));}}
        } else if(type.equals("Encounter")) {
            coding(resource.get("class")); reference(resource.get("subject"));
            for(JsonNode participant:array(resource.get("participant"))) {
                fields(participant,Set.of("type","individual")); reference(participant.get("individual"));
                if(participant.has("type")) {for(JsonNode concept:array(participant.get("type"))) {concept(concept);}}
            }
        } else {
            concept(resource.get("code")); reference(resource.get("subject"));
            if(resource.has("encounter")) {reference(resource.get("encounter"));}
            if(resource.has("valueQuantity")) {quantity(resource.get("valueQuantity"));}
            if(resource.has("dataAbsentReason")) {concept(resource.get("dataAbsentReason"));}
            for(String concepts:new String[]{"interpretation","category"}) {
                if(resource.has(concepts)) {for(JsonNode concept:array(resource.get(concepts))) {concept(concept);}}
            }
            if(resource.has("component")) {for(JsonNode component:array(resource.get("component"))) {
                fields(component,Set.of("code","valueQuantity","dataAbsentReason","interpretation")); concept(component.get("code"));
                if(component.has("valueQuantity")) {quantity(component.get("valueQuantity"));}
                if(component.has("dataAbsentReason")) {concept(component.get("dataAbsentReason"));}
                if(component.has("interpretation")) {for(JsonNode concept:array(component.get("interpretation"))) {concept(concept);}}
            }}
        }
    }

    public void fields(JsonNode node,Set<String> allowed) {
        if(node==null || !node.isObject()) {fail();}
        node.fieldNames().forEachRemaining(name -> {if(!allowed.contains(name)) {fail();}});
    }
    public JsonNode array(JsonNode node) {if(node==null || !node.isArray()) {fail();} return node;}
    private void reference(JsonNode node) {fields(node,Set.of("reference","type","display"));}
    private void concept(JsonNode node) {
        fields(node,Set.of("coding","text"));
        if(node.has("coding")) {for(JsonNode coding:array(node.get("coding"))) {coding(coding);}}
    }
    private void coding(JsonNode node) {fields(node,Set.of("system","version","code","display","userSelected"));}
    private void quantity(JsonNode node) {
        fields(node,Set.of("value","unit","system","code"));
        if(node.has("value")) {
            if(!node.get("value").isNumber()) {fail();}
            if(Math.abs((long)node.get("value").decimalValue().scale())>1000) {throw new IllegalArgumentException("NUMERIC_REPRESENTATION_BOUND");}
        }
    }
    private void forbiddenRecursively(JsonNode node,int depth,boolean inExtension) {
        if(node.isObject()) {
            Iterator<Map.Entry<String,JsonNode>> fields=node.fields();
            while(fields.hasNext()) {
                var field=fields.next();
                if(field.getKey().startsWith("_") || Set.of("modifierExtension","security","contained").contains(field.getKey())) {fail();}
                if(field.getKey().equals("extension") && depth>0 && !inExtension) {fail();}
                forbiddenRecursively(field.getValue(),depth+1,inExtension || field.getKey().equals("extension"));
            }
        } else if(node.isArray()) {node.forEach(child -> forbiddenRecursively(child,depth+1,inExtension));}
    }
    private static void fail() {throw new IllegalArgumentException("UNSUPPORTED_PROFILE_CONTENT");}
}
