package com.aldercrest.atlas.results;

import com.aldercrest.atlas.clinical.ClinicalInterpreter;
import java.util.*;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

public final class ResultRendering {
    private ResultRendering() { }
    public static Map<String,Object> observation(Observation observation,ClinicalViewRepository.View view) {
        var interpretation=new ClinicalInterpreter().interpret(observation);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",observation.id());result.put("code",observation.analyte().code());result.put("value",observation.value());
        result.put("unit",observation.analyte().unit());result.put("unitCode",observation.analyte().unitCode());
        result.put("effective",observation.effective().toString());result.put("status",observation.status());
        result.put("range",range(interpretation.interpretation().range()));result.put("flag",interpretation.interpretation().flag().name());
        result.put("flagReason",interpretation.interpretation().reason().name());result.put("correctionOf",observation.correctionOf());
        result.put("correctedBy",view.correctedBy().getOrDefault(observation.id(),List.of()));
        result.put("releaseState",view.states().get(observation.id()).releaseState());result.put("criticality",view.states().get(observation.id()).criticality());
        result.put("sourceCritical",view.states().get(observation.id()).sourceCritical());
        result.put("components",interpretation.components().stream().map(component -> {
            Map<String,Object> value=new LinkedHashMap<>();value.put("code",component.code());value.put("value",component.value());
            value.put("unit",component.unit());value.put("unitCode",component.unitCode());value.put("range",range(component.interpretation().range()));
            value.put("flag",component.interpretation().flag().name());value.put("flagReason",component.interpretation().reason().name());return value;
        }).toList());
        return result;
    }
    private static Map<String,Object> range(Range range) {return range==null?null:Map.of("low",range.low(),"high",range.high());}
}
