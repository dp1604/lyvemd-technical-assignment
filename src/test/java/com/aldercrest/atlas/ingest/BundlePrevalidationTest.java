package com.aldercrest.atlas.ingest;

import ca.uhn.fhir.parser.DataFormatException;
import com.aldercrest.atlas.security.TestActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Local validation boundary only; no database, clock waiting or application context. */
class BundlePrevalidationTest {
    private static final TestActor ACTOR=new TestActor("known-clinician","CLINICIAN",true,true);

    @Test void expensiveStructuralChecksRunOutsideTheTransactionAndTheirResultIsConsumedOnce() throws Exception {
        FhirValidation validation=mock(FhirValidation.class);IngestionRepository repository=mock(IngestionRepository.class);
        when(validation.valid(anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return true;
        });
        when(validation.parse(anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new Patient().setActive(true);
        });
        var planner=new BundlePlanner(validation,repository);JsonNode bundle=bundle();
        var checks=planner.prevalidate(bundle);
        assertThat(checks.available()).isTrue();assertThat(checks.invalidEntries()).isEmpty();
        verify(validation,times(1)).valid(anyString());verify(validation,times(1)).parse(anyString());verifyNoInteractions(repository);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            var plan=planner.plan(bundle,ACTOR,checks);
            assertThat(plan.accepted()).isTrue();assertThat(plan.patients()).hasSize(1);
        } finally {TransactionSynchronizationManager.clear();}
        // Patient planning consumes checks without structural validation or strict parsing again.
        verify(validation,times(1)).valid(anyString());verify(validation,times(1)).parse(anyString());verifyNoInteractions(repository);
    }

    @Test void invalidIndexesAndStrictParseErrorsRetainTheIndexedFailureAndUnsupportedProfilesSkipHapi() throws Exception {
        FhirValidation validation=mock(FhirValidation.class);IngestionRepository repository=mock(IngestionRepository.class);
        var planner=new BundlePlanner(validation,repository);
        var plan=planner.plan(bundle(),ACTOR,new BundlePlanner.StructuralChecks(Set.of(0),true));
        assertThat(plan.accepted()).isFalse();assertThat(plan.reply().status()).isEqualTo(400);
        JsonNode rejected=IngestionJson.mapper().readTree(plan.reply().body());
        assertThat(rejected.at("/entry/0/response/outcome/issue/0/diagnostics").asText()).isEqualTo("FHIR_INVALID");
        assertThat(rejected.at("/entry/0/response/outcome/issue/0/expression/0").asText()).isEqualTo("Bundle.entry[0]");
        verifyNoInteractions(validation,repository); // An invalid Patient never triggers a stored-patient read.

        when(validation.valid(anyString())).thenReturn(true);
        when(validation.parse(anyString())).thenThrow(new DataFormatException("private parser detail"));
        var parseChecks=planner.prevalidate(bundle());
        assertThat(parseChecks.available()).isTrue();assertThat(parseChecks.invalidEntries()).containsExactly(0);
        reset(validation);
        ObjectNode unsupported=bundle();((ObjectNode)unsupported.at("/entry/0/resource")).put("unsupportedField","private marker");
        var profileChecks=planner.prevalidate(unsupported);
        assertThat(profileChecks.available()).isTrue();assertThat(profileChecks.invalidEntries()).isEmpty();
        var profileFailure=planner.plan(unsupported,ACTOR,profileChecks);
        assertThat(profileFailure.accepted()).isFalse();assertThat(profileFailure.reply().status()).isEqualTo(400);
        assertThat(profileFailure.reply().body()).contains("INVALID_PROFILE").doesNotContain("private marker");
        verifyNoInteractions(validation,repository);
    }

    @Test void entryBoundsMonotonicBudgetAndUnexpectedValidatorFailuresNeverConsumeDatabaseWork() throws Exception {
        FhirValidation validation=mock(FhirValidation.class);IngestionRepository repository=mock(IngestionRepository.class);
        var planner=new BundlePlanner(validation,repository);
        ObjectNode oversized=bundle();var entries=oversized.putArray("entry");
        for(int index=0;index<1001;index++) {entries.add(bundle().path("entry").get(0));}
        var boundChecks=planner.prevalidate(oversized);
        assertThat(boundChecks.available()).isTrue();
        var failed=planner.plan(oversized,ACTOR,boundChecks);
        assertThat(failed.reply().status()).isEqualTo(400);assertThat(failed.reply().body()).contains("ENTRY_COUNT_BOUND");
        verifyNoInteractions(validation,repository);

        long budget=TimeUnit.SECONDS.toNanos(45);AtomicInteger reads=new AtomicInteger();
        var expiredBefore=new BundlePlanner(validation,repository,()->reads.getAndIncrement()==0?0L:budget);
        assertThat(expiredBefore.prevalidate(bundle()).available()).isFalse();verifyNoInteractions(validation,repository);
        AtomicLong now=new AtomicLong();
        when(validation.valid(anyString())).thenAnswer(call->{now.set(budget);return true;});
        when(validation.parse(anyString())).thenReturn(new Patient().setActive(true));
        var expiredAfter=new BundlePlanner(validation,repository,now::get);
        assertThat(expiredAfter.prevalidate(bundle()).available()).isFalse();
        verify(validation,times(1)).valid(anyString());verify(validation,times(1)).parse(anyString());
        reset(validation);
        when(validation.valid(anyString())).thenThrow(new IllegalStateException("private operational diagnostic"));
        var unavailable=planner.prevalidate(bundle());
        assertThat(unavailable.available()).isFalse();assertThat(unavailable.invalidEntries()).isEmpty();
        assertThatThrownBy(()->planner.plan(bundle(),ACTOR,unavailable)).isInstanceOf(IllegalStateException.class)
                .hasMessage("FHIR validation unavailable.");
        verifyNoInteractions(repository);
    }

    private static ObjectNode bundle() throws Exception {
        return (ObjectNode)IngestionJson.mapper().readTree("""
                {"resourceType":"Bundle","type":"transaction","entry":[{
                  "fullUrl":"urn:uuid:8d331c10-2b0f-4b0e-bf44-573e2b81b7a7",
                  "resource":{"resourceType":"Patient","id":"synthetic-patient","active":true,"birthDate":"1990-01-01"},
                  "request":{"method":"POST","url":"Patient"}
                }]}
                """);
    }
}
