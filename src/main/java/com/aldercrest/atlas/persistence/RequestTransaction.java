package com.aldercrest.atlas.persistence;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** One bounded, consistent, read-write action; returns only after commit succeeds. */
@Component
public class RequestTransaction {
    private final TransactionTemplate transaction;

    public RequestTransaction(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        transaction.setReadOnly(false); // Mandatory audit writes share the content-read transaction.
    }

    public <T> T execute(Supplier<T> action) {
        // Callers must materialize content here; never return a stream/lazy DB cursor.
        return transaction.execute(status -> action.get());
    }
}
