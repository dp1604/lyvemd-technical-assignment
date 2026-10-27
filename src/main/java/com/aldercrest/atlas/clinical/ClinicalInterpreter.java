package com.aldercrest.atlas.clinical;

import java.util.List;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

public final class ClinicalInterpreter {
    private final ReferenceRanges ranges = new ReferenceRanges();

    public ResultInterpretation interpret(Observation row) {
        List<ComponentInterpretation> components = row.components().stream().map(component ->
                new ComponentInterpretation(component.analyte().code(), component.quantity().value(),
                        component.analyte().unitCode(), component.analyte().unit(),
                        ranges.interpret(component.analyte(), component.quantity().value(), row.context(), row.effective())))
                .toList();
        return new ResultInterpretation(ranges.interpret(row.analyte(), row.value(), row.context(), row.effective()), components);
    }
}
