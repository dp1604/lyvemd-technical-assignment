package com.aldercrest.atlas.ingest;

import com.aldercrest.atlas.persistence.AuditWriter;
import com.aldercrest.atlas.persistence.RequestTransaction;
import com.aldercrest.atlas.security.TestActor;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Service semantics when pure structural validation is temporarily unavailable; no database needed. */
class IngestionPrevalidationTest {
    private static final TestActor DECLARED=new TestActor("prevalidation-clinician","CLINICIAN",true,false);
    private static final TestActor VERIFIED=DECLARED.withVerifiedRoster();
    private static final String KEY="prevalidation-receipt-test";
    private static final String PATIENT="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final byte[] BODY="""
            {"resourceType":"Bundle","type":"transaction","entry":[
              {"fullUrl":"urn:uuid:11111111-1111-4111-8111-111111111111",
               "resource":{"resourceType":"Patient","active":true,"birthDate":"1990-01-01"},
               "request":{"method":"POST","url":"Patient"}}]}
            """.getBytes(StandardCharsets.UTF_8);
    private final RequestTransaction transaction=mock(RequestTransaction.class);
    private final IngestionRepository repository=mock(IngestionRepository.class);
    private final BundlePlanner planner=mock(BundlePlanner.class);
    private final AuditWriter audit=mock(AuditWriter.class);
    private IngestionService service;
    private boolean insideAction;

    @BeforeEach void setup() {
        when(transaction.execute(any())).thenAnswer(invocation -> {
            Supplier<?> action=invocation.getArgument(0);
            insideAction=true;
            try {return action.get();} finally {insideAction=false;}
        });
        when(repository.known(DECLARED)).thenReturn(true);
        when(planner.prevalidate(any(JsonNode.class))).thenAnswer(invocation -> {
            assertThat(insideAction).as("Pure structural validation must not hold the action snapshot/connection").isFalse();
            return new BundlePlanner.StructuralChecks(Set.of(),false);
        });
        service=new IngestionService(transaction,repository,planner,audit);
    }

    @Test void exactDurableReceiptReplaysOriginalBytesDespiteStructuralValidatorUnavailability() throws Exception {
        String originalBody="{ \"resourceType\" : \"Bundle\", \"type\" : \"transaction-response\", \"entry\" : ["
                +"{ \"response\" : { \"status\" : \"201 Created\", \"location\" : \"Patient/"+PATIENT+"\" } } ] }";
        IngestReply original=new IngestReply(200,originalBody);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BODY));
        when(repository.receipt(VERIFIED,KEY)).thenReturn(new IngestionRecords.Receipt(hash,original,List.of(PATIENT)));

        IngestReply actual=service.ingest(DECLARED,KEY,BODY,0);

        assertThat(actual).isSameAs(original);
        assertThat(actual.status()).isEqualTo(200);assertThat(actual.body()).isEqualTo(originalBody);
        verify(repository).known(DECLARED);verify(repository).lockKey(VERIFIED,KEY);verify(repository).receipt(VERIFIED,KEY);
        verify(planner).prevalidate(any(JsonNode.class));
        verify(planner,never()).plan(any(JsonNode.class),any(TestActor.class),any(BundlePlanner.StructuralChecks.class));
        verify(repository,never()).saveReceipt(any(),anyString(),anyString(),any(),anyList());
        verify(audit).write(VERIFIED,PATIENT,"INGEST","ALLOWED","HTTP");
        verify(transaction).execute(any());
        verifyNoMoreInteractions(repository,planner,audit,transaction);
    }

    @Test void newKeyReturnsAuditedUnavailableWithoutPlanningOrPoisoningACompletedReceipt() {
        IngestReply actual=service.ingest(DECLARED,KEY,BODY,0);

        assertThat(actual.status()).isEqualTo(503);
        assertThat(actual.body()).contains("SERVICE_UNAVAILABLE").doesNotContain(PATIENT,"birthDate","1990-01-01");
        verify(repository).known(DECLARED);verify(repository).lockKey(VERIFIED,KEY);verify(repository).receipt(VERIFIED,KEY);
        verify(planner).prevalidate(any(JsonNode.class));
        verify(planner,never()).plan(any(JsonNode.class),any(TestActor.class),any(BundlePlanner.StructuralChecks.class));
        verify(repository,never()).saveReceipt(any(),anyString(),anyString(),any(),anyList());
        verify(audit).write(eq(VERIFIED),isNull(),eq("INGEST"),eq("DENIED"),eq("HTTP"));
        verify(transaction).execute(any());
        verifyNoMoreInteractions(repository,planner,audit,transaction);
    }

    @Test void unknownActorRemainsUnavailableWithoutDisclosingValidationOrLookingUpReceipts() {
        when(repository.known(DECLARED)).thenReturn(false);

        IngestReply actual=service.ingest(DECLARED,KEY,BODY,0);

        assertThat(actual.status()).isEqualTo(404);
        assertThat(actual.body()).contains("NOT_AVAILABLE").doesNotContain("SERVICE_UNAVAILABLE","validation",PATIENT,"birthDate");
        verify(repository).known(DECLARED);
        verify(repository,never()).lockKey(any(),anyString());verify(repository,never()).receipt(any(),anyString());
        verify(planner).prevalidate(any(JsonNode.class));
        verify(planner,never()).plan(any(JsonNode.class),any(TestActor.class),any(BundlePlanner.StructuralChecks.class));
        verify(repository,never()).saveReceipt(any(),anyString(),anyString(),any(),anyList());
        verify(audit).write(eq(DECLARED),isNull(),eq("INGEST"),eq("DENIED"),eq("HTTP"));
        verify(transaction).execute(any());
        verifyNoMoreInteractions(repository,planner,audit,transaction);
    }
}
