package com.aldercrest.atlas.clinical;

import java.math.BigDecimal;
import java.math.MathContext;
import org.hl7.fhir.r4.model.Quantity;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

public final class UnitNormalizer {
    public static final String UCUM = "http://unitsofmeasure.org";
    public static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal CREATININE_FACTOR = new BigDecimal("88.4");

    public CanonicalQuantity normalize(Analyte analyte, Quantity quantity) {
        if (quantity == null || !UCUM.equals(quantity.getSystem()) || !quantity.hasCode()
                || quantity.hasComparator()) { throw new ClinicalInputException("UNSUPPORTED_QUANTITY"); }
        String unitCode = quantity.getCode();
        boolean convert = analyte == Analyte.CREATININE && "umol/L".equals(unitCode);
        if (!convert && !analyte.unitCode().equals(unitCode)) {
            throw new ClinicalInputException("UNSUPPORTED_UCUM_CODE");
        }
        BigDecimal value = quantity.hasValue() ? quantity.getValue() : null;
        try {
            if (convert && value != null) { value = value.divide(CREATININE_FACTOR, PRECISION); }
        } catch (ArithmeticException error) { throw new ClinicalInputException("UNREPRESENTABLE_NUMBER"); }
        // Quantity.unit is presentation only and never governs conversion.
        return new CanonicalQuantity(value, analyte.unitCode(), unitCode);
    }
}
