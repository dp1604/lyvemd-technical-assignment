package com.aldercrest.atlas.dataset;

import com.aldercrest.atlas.ingest.EntryIssue;
import com.aldercrest.atlas.ingest.FhirReplies;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.aldercrest.atlas.dataset.DatasetFixtureBootstrap.selectInitialRequestKey;
import com.aldercrest.atlas.dataset.DatasetFixtureBootstrap.InitialReceipt;

/** Guard one explicit operational revalidation without touching failed/successful durable receipts. */
class DatasetRequestKeyTest {
    private static final String BASE="atlas-g6-v1-initial-p013",ALTERNATE=BASE+"-revalidated-1";
    private static final String HASH="1".repeat(64),OTHER_HASH="2".repeat(64);
    private static final String SUCCESS="{\"resourceType\":\"Bundle\",\"type\":\"transaction-response\"}";

    @Test void absentBaseKeepsOriginalKeyAndCannotAdoptAnOrphanAlternate() {
        assertThat(selectInitialRequestKey(BASE,HASH,null,null,false)).isEqualTo(BASE);
        assertThat(selectInitialRequestKey(BASE,HASH,null,null,true)).isEqualTo(BASE);
        rejects(null,new InitialReceipt(HASH,200,SUCCESS),false);
        rejects(null,new InitialReceipt(HASH,200,SUCCESS),true);
    }

    @Test void acceptedBaseKeepsOriginalKeyAndRejectsAnyAlternate() {
        InitialReceipt original=new InitialReceipt(HASH,200,SUCCESS);
        assertThat(selectInitialRequestKey(BASE,HASH,original,null,false)).isEqualTo(BASE);
        assertThat(selectInitialRequestKey(BASE,HASH,original,null,true)).isEqualTo(BASE);
        rejects(original,new InitialReceipt(HASH,200,SUCCESS),true);
        rejects(original,new InitialReceipt(HASH,400,failure("FHIR_INVALID")),true);
    }

    @Test void exactFailedStructuralRequestNeedsExplicitFlagForItsOneNewKey() {
        InitialReceipt original=new InitialReceipt(HASH,400,failure(null,"FHIR_INVALID",null));
        rejects(original,null,false);
        assertThat(selectInitialRequestKey(BASE,HASH,original,null,true)).isEqualTo(ALTERNATE);
    }

    @Test void successfulRevalidationResumesUnderSameAlternateEvenWithoutFlag() {
        InitialReceipt original=new InitialReceipt(HASH,400,failure("FHIR_INVALID"));
        InitialReceipt retry=new InitialReceipt(HASH,200,SUCCESS);
        assertThat(selectInitialRequestKey(BASE,HASH,original,retry,false)).isEqualTo(ALTERNATE);
        assertThat(selectInitialRequestKey(BASE,HASH,original,retry,true)).isEqualTo(ALTERNATE);
    }

    @Test void failedAlternateCannotRotateAgainAndUnrecognizedStatusesFailClosed() {
        InitialReceipt original=new InitialReceipt(HASH,400,failure("FHIR_INVALID"));
        rejects(original,new InitialReceipt(HASH,400,failure("FHIR_INVALID")),true);
        rejects(original,new InitialReceipt(HASH,500,SUCCESS),true);
        rejects(new InitialReceipt(HASH,409,SUCCESS),null,true);
        rejects(new InitialReceipt(HASH,503,SUCCESS),null,true);
    }

    @Test void changedBytesCannotBypassAnyStoredOutcome() {
        rejects(new InitialReceipt(OTHER_HASH,200,SUCCESS),null,false);
        rejects(new InitialReceipt(OTHER_HASH,400,failure("FHIR_INVALID")),null,true);
        rejects(new InitialReceipt(HASH,400,failure("FHIR_INVALID")),new InitialReceipt(OTHER_HASH,200,SUCCESS),true);
    }

    @Test void nonstructuralMixedPartialAndMalformedFailuresCannotBeRevalidated() {
        for(String body:List.of(failure("INVALID_UNIT"),failure("FHIR_INVALID","DERIVED_INPUT_UNSUPPORTED"),
                failure("FHIR_INVALID",null).replace("424 Failed Dependency","201 Created"),
                failure((String)null),"{}","[]","not-json",
                "{\"resourceType\":\"Bundle\",\"type\":\"transaction-response\",\"entry\":[]}")) {
            rejects(new InitialReceipt(HASH,400,body),null,true);
            rejects(new InitialReceipt(HASH,400,body),new InitialReceipt(HASH,200,SUCCESS),false);
        }
    }

    @Test void onlyExactInitialNamespaceAndHashFormatCanSelectKeys() {
        for(String key:List.of("different-key","atlas-g6-v1-initial-p100",ALTERNATE,ALTERNATE+"-revalidated-1")) {
            assertThatThrownBy(()->selectInitialRequestKey(key,HASH,null,null,true)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(()->selectInitialRequestKey(BASE,"not-a-hash",null,null,true)).isInstanceOf(IllegalStateException.class);
    }

    private static void rejects(InitialReceipt original,InitialReceipt retry,boolean allow) {
        assertThatThrownBy(()->selectInitialRequestKey(BASE,HASH,original,retry,allow))
                .isInstanceOf(IllegalStateException.class).hasMessage("DATASET_FIXTURE_SETUP_FAILED");
    }
    private static String failure(String... diagnostics) {
        List<EntryIssue> issues=new ArrayList<>();
        for(int index=0;index<diagnostics.length;index++) {if(diagnostics[index]!=null) {issues.add(new EntryIssue(index,diagnostics[index]));}}
        return FhirReplies.rejected(diagnostics.length,issues).body();
    }
}
