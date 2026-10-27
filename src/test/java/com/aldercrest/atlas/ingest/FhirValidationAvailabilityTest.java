package com.aldercrest.atlas.ingest;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.List;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.aldercrest.atlas.ingest.FhirValidation.MessageDisposition.*;

/** Pure classification only; no validator construction, sleep, payload logging, application, or DB. */
class FhirValidationAvailabilityTest {
    @Test void operationalFailureWinsEvenAfterAnEarlierOrdinaryErrorAndRegardlessOfOrdering() {
        var invalid=message(ResultSeverityEnum.ERROR,I18nConstants.ARRAY_CANNOT_BE_EMPTY);
        for(String id:List.of(I18nConstants.TYPE_SPECIFIC_CHECKS_DT_PRIMITIVE_REGEX_EXCEPTION,I18nConstants.REGEX_MATCH_TIMED_OUT)) {
            for(ResultSeverityEnum severity:List.of(ResultSeverityEnum.ERROR,ResultSeverityEnum.FATAL)) {
                var unavailable=message(severity,id);
                assertThat(FhirValidation.classifyMessages(List.of(invalid,unavailable))).isEqualTo(UNAVAILABLE);
                assertThat(FhirValidation.classifyMessages(List.of(unavailable,invalid))).isEqualTo(UNAVAILABLE);
                assertThat(FhirValidation.classifyMessages(List.of(unavailable))).isEqualTo(UNAVAILABLE);
            }
        }
    }

    @Test void ordinaryRegexMismatchUnknownAndMissingIdentifiersRemainInvalidAndAreNeverAccepted() {
        for(String id:new String[]{I18nConstants.TYPE_SPECIFIC_CHECKS_DT_PRIMITIVE_REGEX_TYPE,
                I18nConstants.ARRAY_CANNOT_BE_EMPTY,null,"", "UNKNOWN_PRIVATE_MARKER",
                I18nConstants.REGEX_MATCH_TIMED_OUT+" private-marker",
                "prefix_"+I18nConstants.TYPE_SPECIFIC_CHECKS_DT_PRIMITIVE_REGEX_EXCEPTION}) {
            assertThat(FhirValidation.classifyMessages(List.of(message(ResultSeverityEnum.ERROR,id)))).isEqualTo(INVALID);
        }
    }

    @Test void warningsInformationAndAnEmptyResultDoNotBecomeValidationFailures() {
        assertThat(FhirValidation.classifyMessages(List.of())).isEqualTo(VALID);
        assertThat(FhirValidation.classifyMessages(List.of(
                message(ResultSeverityEnum.WARNING,I18nConstants.TYPE_SPECIFIC_CHECKS_DT_PRIMITIVE_REGEX_EXCEPTION),
                message(ResultSeverityEnum.INFORMATION,I18nConstants.REGEX_MATCH_TIMED_OUT)))).isEqualTo(VALID);
        assertThat(FhirValidation.classifyMessages(List.of(
                message(ResultSeverityEnum.WARNING,I18nConstants.REGEX_MATCH_TIMED_OUT),
                message(ResultSeverityEnum.FATAL,I18nConstants.ARRAY_CANNOT_BE_EMPTY)))).isEqualTo(INVALID);
    }

    private static SingleValidationMessage message(ResultSeverityEnum severity,String id) {
        var message=new SingleValidationMessage();
        message.setSeverity(severity);message.setMessageId(id);
        // Even misleading diagnostic text cannot cause operational classification without the exact ID.
        message.setMessage("Private synthetic text REGEX_MATCH_TIMED_OUT must never be logged or classified.");
        return message;
    }
}
