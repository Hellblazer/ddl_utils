package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies the reciprocal-call lock ordering of the
 * {@code ddl_utils.add_foreign_key} wrapper: two sessions adding foreign keys
 * in opposite directions pre-lock both tables in OID order before the
 * {@code ALTER TABLE}, so neither can hold the lower-OID table while waiting
 * for the higher-OID one in the opposite order.
 */
class ForeignKeyPreLockTest extends PostgresTestBase {

    private static final String LEFT = "fk_pre_lock_left";
    private static final String RIGHT = "fk_pre_lock_right";
    private static final String FORWARD = "fk_left_to_right";
    private static final String REVERSE = "fk_right_to_left";
    private static final String WORKER_STATEMENT_TIMEOUT = "15s";

    private static void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    for (Future<Void> pending : futures) {
                        pending.cancel(true);
                    }
                    fail("a concurrent add_foreign_key call did not finish within 30 s", e);
                } catch (ExecutionException e) {
                    fail("a concurrent add_foreign_key call failed: " + e.getCause(), e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @BeforeEach
    void createTables() {
        createTestTable(LEFT, "id int PRIMARY KEY, right_id int");
        createTestTable(RIGHT, "id int PRIMARY KEY, left_id int");
    }

    @AfterEach
    void dropTables() {
        // Drop the keys first: one of them may depend on the other table, and a
        // failed test may leave only one in place.
        dropConstraintIfExists(LEFT, FORWARD);
        dropConstraintIfExists(RIGHT, REVERSE);
        dropTestTable(LEFT);
        dropTestTable(RIGHT);
    }

    /**
     * Two sessions add reciprocal foreign keys through the wrapper at the same
     * time. The pre-lock takes both tables in the same OID order, so both keys
     * are created without a deadlock error; without the pre-lock, PostgreSQL
     * could take the tables in opposite orders inside the ALTER.
     */
    @Test
    void reciprocalWrapperCallsDoNotDeadlock() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<Void>> tasks = new ArrayList<>();
        tasks.add(() -> addForeignKey(barrier, LEFT, FORWARD, "right_id", RIGHT));
        tasks.add(() -> addForeignKey(barrier, RIGHT, REVERSE, "left_id", LEFT));

        runConcurrently(tasks);

        assertTrue(constraintExists(PUBLIC_SCHEMA, LEFT, FORWARD),
                "the forward foreign key should have been created");
        assertTrue(constraintExists(PUBLIC_SCHEMA, RIGHT, REVERSE),
                "the reverse foreign key should have been created");
    }

    private Void addForeignKey(CyclicBarrier barrier, String sourceTable, String name,
                               String sourceColumn, String referencedTable) throws Exception {
        try (Connection connection = openConnectionWithStatementTimeout(WORKER_STATEMENT_TIMEOUT)) {
            barrier.await(10, TimeUnit.SECONDS);
            Routines.addForeignKey(
                    DSL.using(connection, SQLDialect.POSTGRES).configuration(),
                    PUBLIC_SCHEMA, sourceTable, name, new String[]{sourceColumn},
                    PUBLIC_SCHEMA, referencedTable, new String[]{"id"});
        }
        return null;
    }

    private void dropConstraintIfExists(String table, String constraint) {
        dsl.execute("ALTER TABLE IF EXISTS " + PUBLIC_SCHEMA + "." + table
                + " DROP CONSTRAINT IF EXISTS " + constraint);
    }
}
