package com.aldercrest.atlas.clinical;

/** Safe categorical validation error: never retain the supplied value/payload in an exception. */
public class ClinicalInputException extends RuntimeException {
    public ClinicalInputException(String code) { super(code); }
}
