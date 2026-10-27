package com.aldercrest.atlas.dataset;

import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;

/** Retry only an explicit temporary HTTP503, retaining the exact caller request. */
final class DatasetRequestRetry {
    @FunctionalInterface interface Pause { void sleep(long milliseconds) throws InterruptedException; }
    private DatasetRequestRetry() { }

    static <T> T send(Callable<T> operation,ToIntFunction<T> status,Pause pause) throws Exception {
        for(int attempt=1;attempt<=3;attempt++) {
            T reply=operation.call();
            if(status.applyAsInt(reply)!=503 || attempt==3) {return reply;}
            System.out.println("ATLAS_DATASET HTTP_UNAVAILABLE_RETRY_ATTEMPT="+(attempt+1));
            try {pause.sleep(attempt*1000L);}
            catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw interrupted;}
        }
        throw new IllegalStateException("Unreachable retry state.");
    }
}
