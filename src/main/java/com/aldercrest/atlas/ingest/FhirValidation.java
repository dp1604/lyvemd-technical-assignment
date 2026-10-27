package com.aldercrest.atlas.ingest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.springframework.stereotype.Component;

@Component
public class FhirValidation {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(FhirValidation.class);
    private static final Map<String,String> DIAGNOSTIC_RULES=diagnosticRules();
    private final FhirContext context;
    private final FhirValidator validator;

    public FhirValidation(FhirContext context) throws IOException {
        // Validation uses bundled R4 resources and our local definitions. Never resolve URLs over HTTP.
        org.hl7.fhir.utilities.http.ManagedWebAccess.setAccessPolicy(
                org.hl7.fhir.utilities.http.ManagedWebAccess.WebAccessPolicy.PROHIBITED);
        this.context = context;
        PrePopulatedValidationSupport local = new PrePopulatedValidationSupport(context);
        try(var stream=getClass().getResourceAsStream("/fhir/CodeSystem-encounter-class.json")) {
            if(stream==null) {throw new IllegalStateException("Missing local encounter class definition.");}
            local.addCodeSystem(context.newJsonParser().parseResource(org.hl7.fhir.r4.model.CodeSystem.class,
                    new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        }
        for (String name : new String[]{"clinical-sex-context","correction-of","standardized-creatinine"}) {
            try (var stream = getClass().getResourceAsStream("/fhir/StructureDefinition-"+name+".json")) {
                if (stream == null) { throw new IllegalStateException("Missing local FHIR definition."); }
                local.addStructureDefinition(context.newJsonParser().parseResource(StructureDefinition.class,
                        new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
            }
        }
        ValidationSupportChain support = new ValidationSupportChain(local,new DefaultProfileValidationSupport(context),
                new CommonCodeSystemsTerminologyService(context),new InMemoryTerminologyServerValidationSupport(context),
                new SnapshotGeneratingValidationSupport(context));
        FhirInstanceValidator module = new FhirInstanceValidator(support);
        module.setAnyExtensionsAllowed(false);
        module.setErrorForUnknownProfiles(true);
        validator = context.newValidator().registerValidatorModule(module);
        // Load local base definitions before the serving socket opens, not inside a request transaction.
        validator.validateWithResult(new Patient().setActive(true));
    }

    public boolean valid(String json) {
        try {
            for(var message:validator.validateWithResult(json).getMessages()) {
                if(message.getSeverity()==ResultSeverityEnum.ERROR || message.getSeverity()==ResultSeverityEnum.FATAL) {
                    // First failure category only. Never log message text, location, payload or a throwable.
                    LOG.warn("ATLAS_FHIR_VALIDATION_REJECTED severity={} rule={}",message.getSeverity().name(),diagnosticRule(message.getMessageId()));
                    return false;
                }
            }
            return true;
        } catch (ca.uhn.fhir.parser.DataFormatException error) {
            LOG.warn("ATLAS_FHIR_VALIDATOR_PARSE_REJECTED");return false;
        }
        catch (RuntimeException error) {throw new IllegalStateException("FHIR validation unavailable.",error);}
    }
    public org.hl7.fhir.r4.model.Resource parse(String json) {
        try {
            return (org.hl7.fhir.r4.model.Resource)context.newJsonParser()
                    .setParserErrorHandler(new ca.uhn.fhir.parser.StrictErrorHandler()).parseResource(json);
        } catch(ca.uhn.fhir.parser.DataFormatException error) {LOG.warn("ATLAS_FHIR_STRICT_PARSE_REJECTED");throw error;}
        catch(RuntimeException error) {throw new IllegalStateException("FHIR parser unavailable.",error);}
    }

    /** Return a trusted compiled constant NAME, never any portion of an unrecognized message ID. */
    static String diagnosticRule(String messageId) {return messageId==null?"UNKNOWN":DIAGNOSTIC_RULES.getOrDefault(messageId,"UNKNOWN");}
    private static Map<String,String> diagnosticRules() {
        Map<String,String> rules=new HashMap<>();
        for(var field:I18nConstants.class.getFields()) {
            int modifiers=field.getModifiers();String name=field.getName();
            if(field.getType()!=String.class || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
                    || !name.matches("[A-Za-z0-9_]{1,200}")) {continue;}
            try {
                String value=(String)field.get(null);
                if(value!=null) {rules.merge(value,name,(first,second)->first.compareTo(second)<=0?first:second);}
            } catch(IllegalAccessException ignored) { /* Unknown codes remain a fixed UNKNOWN category. */ }
        }
        return Map.copyOf(rules);
    }
}
