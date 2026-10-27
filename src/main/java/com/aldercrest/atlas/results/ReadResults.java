package com.aldercrest.atlas.results;

import com.aldercrest.atlas.clinical.Analyte;
import com.aldercrest.atlas.clinical.ClinicalCalculations;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

/** Response construction accepts only the already-authorized, complete-ancestry caller view. */
public final class ReadResults {
    private record Row(String id,Instant effective,Map<String,Object> body) { }
    private final ClinicalViewRepository.View view;
    private final ClinicalCalculations calculations=new ClinicalCalculations();
    public ReadResults(ClinicalViewRepository.View view) {this.view=view;}
    public Map<String,Object> page(ReadQueries.Page page) {
        List<Row> all=rows().stream().sorted(Comparator.comparing(Row::effective).reversed().thenComparing(Row::id)).toList();
        var items=all.stream().skip(page.offset()).limit(page.limit()).map(Row::body).toList();
        return Map.of("items",items,"total",all.size(),"offset",page.offset(),"limit",page.limit());
    }
    public Map<String,Object> find(String id) {
        // Same collection is materialized for raw, derived, hidden and absent identifiers.
        return rows().stream().filter(row -> row.id().equals(id)).map(Row::body).findFirst().orElse(null);
    }
    public Map<String,Object> trend(ReadQueries.Trend query) {
        List<Row> points=new ArrayList<>();
        if(query.analyte().derived()) {derived(query.analyte()).forEach(row -> points.add(derivedRow(row)));}
        else {
            Set<String> superseded=new HashSet<>();
            view.series().observations().forEach(row -> {if(row.correctionOf()!=null) {superseded.add(row.correctionOf());}});
            for(Observation row:view.series().observations()) {
                if(superseded.contains(row.id())) {continue;}
                if(row.analyte()==query.analyte()) {points.add(rawTrend(row));}
                else if(row.analyte()==Analyte.BLOOD_PRESSURE &&
                        (query.analyte()==Analyte.SYSTOLIC || query.analyte()==Analyte.DIASTOLIC)) {
                    row.components().stream().filter(component -> component.analyte()==query.analyte()).findFirst()
                            .ifPresent(component -> points.add(componentTrend(row,query.analyte())));
                }
            }
        }
        // Full authorized history precedes display-window filtering, including prior BMI heights.
        var visible=points.stream().filter(row -> query.contains(row.effective()))
                .sorted(Comparator.comparing(Row::effective).thenComparing(Row::id)).map(Row::body).toList();
        return Map.of("code",query.analyte().code(),"points",visible,"total",visible.size());
    }
    private List<Row> rows() {
        List<Row> all=new ArrayList<>();
        view.series().observations().forEach(row -> all.add(new Row(row.id(),row.effective(),raw(row))));
        derived(Analyte.BMI).forEach(row -> all.add(derivedRow(row)));
        derived(Analyte.EGFR).forEach(row -> all.add(derivedRow(row)));
        return all;
    }
    private List<Derived> derived(Analyte analyte) {
        return analyte==Analyte.BMI?calculations.bmiSeries(view.series()):calculations.egfrSeries(view.series());
    }
    private Map<String,Object> raw(Observation row) {
        var body=ResultRendering.observation(row,view);body.put("derived",false);return body;
    }
    private Row rawTrend(Observation row) {
        var body=raw(row);if(!row.eligibleForDerivation()) {gap(body);}return new Row(row.id(),row.effective(),body);
    }
    @SuppressWarnings("unchecked")
    private Row componentTrend(Observation row,Analyte analyte) {
        var body=raw(row);
        var components=(List<Map<String,Object>>)body.get("components");
        var component=components.stream().filter(value -> analyte.code().equals(value.get("code"))).findFirst()
                .orElseThrow(() -> new IllegalStateException("Invalid visible component state."));
        body.putAll(component);body.put("components",List.of());body.put("parentObservationId",row.id());
        if(!row.eligibleForDerivation()) {gap(body);}return new Row(row.id(),row.effective(),body);
    }
    @SuppressWarnings("unchecked")
    private static void gap(Map<String,Object> body) {
        body.put("value",null);body.put("range",null);body.put("flag",Flag.NO_APPLICABLE_FLAG.name());
        body.put("flagReason",Reason.INPUT_STATUS_INELIGIBLE.name());body.put("cannotComputeReason",Reason.INPUT_STATUS_INELIGIBLE.name());
        var components=(List<Map<String,Object>>)body.get("components");
        body.put("components",components.stream().map(component -> {
            Map<String,Object> value=new LinkedHashMap<>(component);value.put("value",null);value.put("range",null);
            value.put("flag",Flag.NO_APPLICABLE_FLAG.name());value.put("flagReason",Reason.INPUT_STATUS_INELIGIBLE.name());return value;
        }).toList());
    }
    private Row derivedRow(Derived derived) {
        String id=derivedId(derived);Map<String,Object> body=new LinkedHashMap<>();
        body.put("id",id);body.put("derived",true);body.put("code",derived.analyte().code());body.put("value",derived.value());
        body.put("unit",derived.analyte().unit());body.put("unitCode",derived.analyte().unitCode());
        body.put("effective",derived.effective().toString());body.put("status","derived");
        Range range=derived.interpretation().range();body.put("range",range==null?null:Map.of("low",range.low(),"high",range.high()));
        body.put("flag",derived.interpretation().flag().name());body.put("flagReason",derived.interpretation().reason().name());
        body.put("computed",derived.computable());body.put("cannotComputeReason",derived.cannotComputeReason().name());
        body.put("sources",derived.sources().stream().map(source -> Map.of("observationId",source.observationId(),"effective",source.effective().toString())).toList());
        body.put("method",derived.method());body.put("notice",derived.notice());body.put("correctionOf",null);
        body.put("correctedBy",List.of());body.put("components",List.of());body.put("releaseState","INHERITED_FROM_VISIBLE_INPUTS");
        body.put("criticality",null);
        body.put("sourceCritical",derived.sources().stream().anyMatch(source -> view.states().get(source.observationId()).sourceCritical()));
        body.put("sourceReleaseStates",derived.sources().stream().map(source -> {
            var state=view.states().get(source.observationId());
            return Map.of("observationId",source.observationId(),"releaseState",state.releaseState(),"criticality",state.criticality());
        }).toList());
        return new Row(id,derived.effective(),body);
    }
    public static String derivedId(Derived derived) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            token(digest,"atlas-derived-id-v1");token(digest,derived.method());token(digest,derived.analyte().code());token(digest,derived.effective().toString());
            derived.sources().stream().sorted(Comparator.comparing(Source::observationId).thenComparing(Source::effective)).forEach(source -> {
                token(digest,source.observationId());token(digest,source.effective().toString());
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException error) {throw new IllegalStateException("Required digest unavailable.",error);}
    }
    private static void token(MessageDigest digest,String token) {
        byte[] bytes=token.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());digest.update(bytes);
    }
}
