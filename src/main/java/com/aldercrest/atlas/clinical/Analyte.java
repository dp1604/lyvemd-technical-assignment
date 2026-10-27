package com.aldercrest.atlas.clinical;

import java.util.Arrays;

/** The assessment's fixed clinical table, plus its chosen BP panel code. */
public enum Analyte {
    SYSTOLIC("8480-6", "mm[Hg]", "mm[Hg]"),
    DIASTOLIC("8462-4", "mm[Hg]", "mm[Hg]"),
    HEIGHT("8302-2", "cm", "cm"), WEIGHT("29463-7", "kg", "kg"),
    BMI("39156-5", "kg/m2", "kg/m2"), HAEMOGLOBIN("718-7", "g/dL", "g/dL"),
    SODIUM("2951-2", "mmol/L", "mmol/L"), POTASSIUM("2947-0", "mmol/L", "mmol/L"),
    CREATININE("2160-0", "mg/dL", "mg/dL"), HBA1C("4548-4", "%", "%"),
    EGFR("98979-8", "mL/s/{1.73_m2}", "mL/s/1.73m2"),
    BLOOD_PRESSURE("85354-9", "mm[Hg]", "mm[Hg]");

    private final String code;
    private final String unitCode;
    private final String unit;

    Analyte(String code, String unitCode, String unit) {
        this.code = code; this.unitCode = unitCode; this.unit = unit;
    }
    public String code() { return code; }
    public String unitCode() { return unitCode; }
    public String unit() { return unit; }
    public boolean derived() { return this == BMI || this == EGFR; }

    public static Analyte fromCode(String code) {
        return Arrays.stream(values()).filter(a -> a.code.equals(code)).findFirst()
                .orElseThrow(() -> new ClinicalInputException("UNSUPPORTED_ANALYTE"));
    }
}
