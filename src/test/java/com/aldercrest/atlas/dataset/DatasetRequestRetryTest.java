package com.aldercrest.atlas.dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DatasetRequestRetryTest {
    @Test void explicit503CanRecoverUsingTheSameOperation() throws Exception {
        AtomicInteger calls=new AtomicInteger();List<Long> pauses=new ArrayList<>();
        int reply=DatasetRequestRetry.send(()->calls.incrementAndGet()==1?503:200,Integer::intValue,pauses::add);
        assertThat(reply).isEqualTo(200);assertThat(calls.get()).isEqualTo(2);assertThat(pauses).containsExactly(1000L);
    }
    @Test void repeated503IsBoundedAndReturnsTheFinalFailure() throws Exception {
        AtomicInteger calls=new AtomicInteger();List<Long> pauses=new ArrayList<>();
        int reply=DatasetRequestRetry.send(()->{calls.incrementAndGet();return 503;},Integer::intValue,pauses::add);
        assertThat(reply).isEqualTo(503);assertThat(calls.get()).isEqualTo(3);assertThat(pauses).containsExactly(1000L,2000L);
    }
    @Test void completedInvalidResponsesAndOtherStatusesNeverRetry() throws Exception {
        for(int status:List.of(200,400,404,409,429,500)) {
            AtomicInteger calls=new AtomicInteger();
            int reply=DatasetRequestRetry.send(()->{calls.incrementAndGet();return status;},Integer::intValue,
                    ignored->{throw new AssertionError("Unexpected pause");});
            assertThat(reply).isEqualTo(status);assertThat(calls.get()).isEqualTo(1);
        }
    }
    @Test void unknownOperationFailureDoesNotStartARetryLoop() {
        AtomicInteger calls=new AtomicInteger();
        assertThatThrownBy(()->DatasetRequestRetry.send(()->{calls.incrementAndGet();throw new java.io.IOException();},
                ignored->503,ignored->{throw new AssertionError("Unexpected pause");})).isInstanceOf(java.io.IOException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void interruptionStopsRetriesAndPreservesTheInterruptSignal() {
        AtomicInteger calls=new AtomicInteger();
        try {
            assertThatThrownBy(()->DatasetRequestRetry.send(()->{calls.incrementAndGet();return 503;},Integer::intValue,
                    ignored->{throw new InterruptedException();})).isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();assertThat(calls.get()).isEqualTo(1);
        } finally {Thread.interrupted();}
    }
}
