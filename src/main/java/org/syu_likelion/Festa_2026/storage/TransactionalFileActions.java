package org.syu_likelion.Festa_2026.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TransactionalFileActions {
    private static final Logger log = LoggerFactory.getLogger(TransactionalFileActions.class);

    private TransactionalFileActions() { }

    public static boolean deleteOnRollback(Runnable deletion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) runSafely(deletion, "rollback");
            }
        });
        return true;
    }

    public static void deleteAfterCommit(Runnable deletion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(deletion, "immediate");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(deletion, "after-commit");
            }
        });
    }

    private static void runSafely(Runnable deletion, String phase) {
        try {
            deletion.run();
        } catch (RuntimeException exception) {
            log.error("Media storage cleanup failed phase={} type={}",
                    phase, exception.getClass().getName(), exception);
        }
    }
}
