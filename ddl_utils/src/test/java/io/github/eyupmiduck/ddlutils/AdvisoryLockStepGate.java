package io.github.eyupmiduck.ddlutils;

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Pauses a committed procedure between its steps by queuing session-level
 * advisory locks on the key the procedure re-acquires at the start of every
 * step.
 *
 * <p>PostgreSQL grants advisory locks in FIFO order. The gate holds the key
 * before the procedure starts, waits until the procedure is queued for its next
 * step, then queues a new gate behind it. Releasing the current gate lets the
 * procedure run exactly one step; the next gate is granted only after that
 * step's transaction commits, so a test can run external DDL in a deterministic
 * window between two steps.
 *
 * <p>The procedure uses the transaction-scoped
 * {@code pg_advisory_xact_lock(bigint)} and the gate uses the session-scoped
 * {@code pg_advisory_lock(bigint)}; both occupy the same bigint lock space, and
 * a transaction-level lock is released by the step's {@code COMMIT}. The
 * constructor verifies that the procedure really waits on the supplied key, so
 * a key drift in a procedure fails the test instead of silently removing the
 * interleaving.
 */
final class AdvisoryLockStepGate implements AutoCloseable {

    /**
     * The procedure call under test, run on the worker connection.
     */
    @FunctionalInterface
    interface ProcedureCall {

        /**
         * Runs the procedure.
         *
         * @param connection the worker connection
         * @throws Exception if the call fails
         */
        void run(Connection connection) throws Exception;
    }

    /**
     * Opens a test-role connection for the worker or a gate.
     */
    @FunctionalInterface
    interface ConnectionFactory {

        /**
         * Opens a connection.
         *
         * @return the connection
         * @throws SQLException if the connection cannot be opened
         */
        Connection open() throws SQLException;
    }

    private static final long POLL_INTERVAL_MILLIS = 50;
    private static final int POLL_ATTEMPTS = 600;
    private static final int WORKER_TIMEOUT_SECONDS = 60;

    private final DSLContext monitor;
    private final ConnectionFactory connections;
    private final long key;
    private final ExecutorService executor;
    private final Connection holder;
    private final Connection worker;
    private final int workerPid;
    private final List<Connection> gates = new ArrayList<>();
    private Connection currentGate;
    private Future<Throwable> result;

    private AdvisoryLockStepGate(DSLContext monitor, ConnectionFactory connections, long key,
                                 Connection holder, Connection worker) throws SQLException {
        this.monitor = monitor;
        this.connections = connections;
        this.key = key;
        this.holder = holder;
        this.worker = worker;
        this.workerPid = backendPid(worker);
        this.currentGate = holder;
        this.executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "advisory-lock-gate");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Holds the key, starts the procedure on a worker connection and waits until
     * the procedure is blocked on the first step's lock.
     *
     * @param monitor     the test connection used to poll {@code pg_locks}
     * @param connections opens the worker and gate connections
     * @param key         the procedure's advisory-lock key
     * @param call        the procedure call
     * @return the gate
     * @throws Exception if the connections cannot be opened or the procedure does
     *                   not wait on the key
     */
    static AdvisoryLockStepGate start(DSLContext monitor, ConnectionFactory connections, long key,
                                      ProcedureCall call) throws Exception {
        Connection holder = connections.open();
        Connection worker = null;
        AdvisoryLockStepGate gate = null;
        try {
            worker = connections.open();
            gate = new AdvisoryLockStepGate(monitor, connections, key, holder, worker);
            gate.lock(holder);
            Connection workerConnection = worker;
            gate.result = gate.executor.submit(() -> {
                try {
                    call.run(workerConnection);
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            gate.awaitWaiting(gate.workerPid,
                    "the procedure did not wait on the expected advisory-lock key");
            return gate;
        } catch (Exception e) {
            if (gate != null) {
                gate.close();
            } else {
                closeQuietly(worker);
                closeQuietly(holder);
            }
            throw e;
        }
    }

    /**
     * Advances the procedure through the given number of committed steps and
     * holds it before the next step. The method may only be called after the
     * previous pause (or {@link #start}) and with the procedure still running.
     *
     * @param step the number of steps to let commit
     * @throws Exception if a gate cannot be queued or the procedure does not
     *                   commit the step
     */
    void pauseAfterStep(int step) throws Exception {
        for (int completed = 0; completed < step; completed++) {
            awaitWaiting(workerPid, "the procedure did not queue for its next step");

            Connection next = connections.open();
            gates.add(next);
            int nextPid = backendPid(next);
            Future<?> acquisition = executor.submit(() -> {
                lock(next);
                return null;
            });
            try {
                awaitWaiting(nextPid, "the next gate did not queue behind the procedure");
                unlock(currentGate);
                currentGate = null;
                awaitGranted(nextPid, "the procedure did not commit its step while the gate was queued");
                acquisition.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                acquisition.cancel(true);
                throw e;
            }
            currentGate = next;
        }
    }

    /**
     * Releases the current gate and waits for the procedure call to finish.
     *
     * @return the procedure's failure, or {@code null} when it succeeded
     * @throws Exception if the call does not finish or the gate cannot be
     *                   released
     */
    Throwable resume() throws Exception {
        if (currentGate != null) {
            unlock(currentGate);
            currentGate = null;
        }
        return result.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        // Abort the worker first: closing its connection rolls back the open
        // step, so releasing the gates afterwards cannot let a test that is
        // already unwinding run and commit one more DDL step.
        closeQuietly(worker);
        if (currentGate != null) {
            try {
                unlock(currentGate);
            } catch (Exception ignored) {
                // Closing the connection below also releases the session lock.
            }
            currentGate = null;
        }
        for (Connection gate : gates) {
            closeQuietly(gate);
        }
        gates.clear();
        closeQuietly(holder);
        executor.shutdownNow();
    }

    private void lock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
            statement.setLong(1, key);
            statement.execute();
        }
    }

    private void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean(1)) {
                    throw new IllegalStateException("the gate does not hold the advisory lock");
                }
            }
        }
    }

    private void awaitWaiting(int pid, String message) throws InterruptedException {
        awaitLockState(pid, false, message);
    }

    private void awaitGranted(int pid, String message) throws InterruptedException {
        awaitLockState(pid, true, message);
    }

    private void awaitLockState(int pid, boolean granted, String message) throws InterruptedException {
        // The single bigint-key form stores the lock as objsubid = 1 with the
        // key split across classid (high 32 bits) and objid (low 32 bits).
        // Match the key itself, so a session waiting on a different advisory
        // lock cannot satisfy this gate.
        long classId = key >>> 32;
        long objectId = key & 0xFFFFFFFFL;
        for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
            Object found = monitor.fetchValue("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_locks
                        WHERE locktype = 'advisory'
                          AND objsubid = 1
                          AND classid = ?::oid
                          AND objid = ?::oid
                          AND pid = ?
                          AND granted = ?
                    )
                    """, classId, objectId, pid, granted);
            if (Boolean.TRUE.equals(found)) {
                return;
            }
            failIfWorkerFinished(pid, message);
            if (pid != workerPid && !granted) {
                // A gate expected to queue is granted immediately when the
                // worker dies; surface the worker's real failure instead of
                // spinning to the timeout.
                failIfWorkerFinished(workerPid, message);
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        throw new IllegalStateException(message
                + " (waited " + POLL_ATTEMPTS * POLL_INTERVAL_MILLIS + " ms)");
    }

    private void failIfWorkerFinished(int pid, String message) throws InterruptedException {
        if (pid != workerPid || result == null || !result.isDone()) {
            return;
        }
        try {
            Throwable failure = result.get();
            throw new IllegalStateException(message
                    + (failure == null
                    ? "; the procedure finished successfully without waiting"
                    : "; the procedure finished without waiting: " + failure),
                    failure);
        } catch (ExecutionException e) {
            throw new IllegalStateException(message + "; the procedure failed before waiting", e.getCause());
        }
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT pg_backend_pid()")) {
            if (!rows.next()) {
                throw new SQLException("pg_backend_pid() returned no row");
            }
            return rows.getInt(1);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Best-effort cleanup; the test failure is already being reported.
        }
    }
}
