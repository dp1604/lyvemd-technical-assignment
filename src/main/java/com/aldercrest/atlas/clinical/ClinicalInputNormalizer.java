package com.aldercrest.atlas.clinical;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

/** Clinical mapping for the later transaction adapter; no database, entitlement or HTTP bypass. */
public final class ClinicalInputNormalizer {
    public static final String LOINC = "http://loinc.org";
    private final UnitNormalizer units = new UnitNormalizer();

    public Measurement normalize(Observation input) {
        Analyte analyte = code(input.getCode());
        if (analyte.derived()) { throw new ClinicalInputException("DERIVED_INPUT_UNSUPPORTED"); }
        if (analyte == Analyte.SYSTOLIC || analyte == Analyte.DIASTOLIC) {
            throw new ClinicalInputException("BLOOD_PRESSURE_PANEL_REQUIRED");
        }
        if (analyte == Analyte.BLOOD_PRESSURE) {
            if (input.hasValue() || input.hasDataAbsentReason() || input.getComponent().size() != 2) {
                throw new ClinicalInputException("INVALID_BLOOD_PRESSURE_PANEL");
            }
            List<Component> components = new ArrayList<>();
            Set<Analyte> seen = new HashSet<>();
            for (Observation.ObservationComponentComponent component : input.getComponent()) {
                Analyte part = code(component.getCode());
                if ((part != Analyte.SYSTOLIC && part != Analyte.DIASTOLIC) || !seen.add(part)) {
                    throw new ClinicalInputException("INVALID_BLOOD_PRESSURE_COMPONENTS");
                }
                CanonicalQuantity quantity = quantity(part, component.getValue(), component.hasDataAbsentReason());
                components.add(new Component(part, quantity));
            }
            return new Measurement(analyte, new CanonicalQuantity(null, analyte.unitCode(), null), components);
        }
        if (input.hasComponent()) { throw new ClinicalInputException("UNSUPPORTED_COMPONENTS"); }
        return new Measurement(analyte, quantity(analyte, input.getValue(), input.hasDataAbsentReason()), List.of());
    }

    private CanonicalQuantity quantity(Analyte analyte, org.hl7.fhir.r4.model.Type value, boolean absent) {
        if (value == null && absent) { return new CanonicalQuantity(null, analyte.unitCode(), null); }
        if (!(value instanceof Quantity quantity) || absent) {
            throw new ClinicalInputException("QUANTITY_OR_ABSENCE_REQUIRED");
        }
        return units.normalize(analyte, quantity);
    }

    private Analyte code(CodeableConcept concept) {
        List<Coding> loinc = concept.getCoding().stream().filter(c -> LOINC.equals(c.getSystem())).toList();
        if (loinc.size() != 1 || !loinc.getFirst().hasCode()) { throw new ClinicalInputException("ONE_LOINC_CODE_REQUIRED"); }
        return Analyte.fromCode(loinc.getFirst().getCode());
    }
}
