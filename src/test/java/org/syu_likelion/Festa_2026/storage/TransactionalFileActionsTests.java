package org.syu_likelion.Festa_2026.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionalFileActionsTests {
    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletionRunsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger deletions = new AtomicInteger();

        TransactionalFileActions.deleteAfterCommit(deletions::incrementAndGet);
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertThat(deletions).hasValue(0);

        callbacks.forEach(TransactionSynchronization::afterCommit);
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(deletions).hasValue(1);
    }

    @Test
    void uploadedFileCleanupRunsOnlyOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger deletions = new AtomicInteger();

        assertThat(TransactionalFileActions.deleteOnRollback(deletions::incrementAndGet)).isTrue();
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(deletions).hasValue(1);
    }
}
