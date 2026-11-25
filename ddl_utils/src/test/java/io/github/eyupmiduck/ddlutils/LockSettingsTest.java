package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;

import java.sql.Connection;

import static io.github.eyupmiduck.ddlutils.jooq.ddl_utils.tables.DatabaseLockSettings.DATABASE_LOCK_SETTINGS;
import static io.github.eyupmiduck.ddlutils.jooq.ddl_utils.tables.GetDatabaseLockSettings.GET_DATABASE_LOCK_SETTINGS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies {@code ddl_utils.database_lock_settings} and its accessors: the single row is
 * readable by the caller, {@code get_database_lock_settings} returns it, and
 * {@code set_database_lock_settings} updates it (SECURITY DEFINER, since the caller has
 * only SELECT on the table).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LockSettingsTest extends PostgresTestBase {

    private static final int DEFAULT_DDL_LOCK_TIMEOUT = 100;
    private static final int DEFAULT_SLEEP_TIME = 1000;
    private static final int DEFAULT_STATEMENT_DURATION = 30000;

    /**
     * Resets the row to the seeded defaults after every test, so tests are
     * independent of each other's updates. Cleanup runs after the test rather
     * than before it so {@link #lockSettingsRowIsSeeded} observes the values the
     * migration actually seeded, not a value this class wrote.
     */
    @AfterEach
    void resetLockSettings() {
        restoreDatabaseDefaults(DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
    }

    /**
     * The lock settings table holds the seeded row.
     */
    @Test
    @Order(1)
    void lockSettingsRowIsSeeded() {
        Record row = tableRow();

        assertNotNull(row);
        assertLockSettings(row, DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
    }

    /**
     * The caller may read the table but not modify it: SELECT succeeds while
     * UPDATE, INSERT and DELETE are denied with an insufficient-privilege error.
     */
    @Test
    void callerCanSelectButNotWriteLockSettings() {
        assertNotNull(tableRow());

        assertSqlState("42501", () -> dsl.update(DATABASE_LOCK_SETTINGS)
                .set(DATABASE_LOCK_SETTINGS.SLEEP_TIME, 1)
                .where(DATABASE_LOCK_SETTINGS.ID.eq(1))
                .execute());
        assertSqlState("42501", () -> dsl.insertInto(DATABASE_LOCK_SETTINGS,
                        DATABASE_LOCK_SETTINGS.ID,
                        DATABASE_LOCK_SETTINGS.DDL_LOCK_TIMEOUT,
                        DATABASE_LOCK_SETTINGS.SLEEP_TIME,
                        DATABASE_LOCK_SETTINGS.STATEMENT_DURATION)
                .values(1, 1, 1, 1)
                .execute());
        assertSqlState("42501", () -> dsl.deleteFrom(DATABASE_LOCK_SETTINGS)
                .where(DATABASE_LOCK_SETTINGS.ID.eq(1))
                .execute());
    }

    /**
     * The table is a singleton: it holds exactly one row, and a second row is
     * rejected by the fixed-id check even for the owner (who has write
     * privileges).
     */
    @Test
    void databaseLockSettingsIsSingleton() throws Exception {
        assertEquals(1, dsl.fetchCount(DATABASE_LOCK_SETTINGS));

        try {
            try (Connection owner = openOwnerConnection()) {
                assertSqlState("23514", () -> DSL.using(owner, SQLDialect.POSTGRES)
                        .insertInto(DATABASE_LOCK_SETTINGS,
                                DATABASE_LOCK_SETTINGS.ID,
                                DATABASE_LOCK_SETTINGS.DDL_LOCK_TIMEOUT,
                                DATABASE_LOCK_SETTINGS.SLEEP_TIME,
                                DATABASE_LOCK_SETTINGS.STATEMENT_DURATION)
                        .values(2, 1, 1, 1)
                        .execute());
            }
        } finally {
            // A regression that lets the insert succeed must not leave a second
            // row behind for the rest of the class.
            try (Connection owner = openOwnerConnection()) {
                DSL.using(owner, SQLDialect.POSTGRES)
                        .deleteFrom(DATABASE_LOCK_SETTINGS)
                        .where(DATABASE_LOCK_SETTINGS.ID.eq(2))
                        .execute();
            }
        }
    }

    /**
     * get_database_lock_settings returns the current values.
     */
    @Test
    void getLockSettingsReturnsCurrentValues() {
        Record row = getDatabaseLockSettings();

        assertNotNull(row);
        assertLockSettings(row, DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
    }

    /**
     * get_database_lock_settings raises no_data_found when the singleton row is
     * missing, rather than returning an empty result.
     */
    @Test
    void getDatabaseLockSettingsRaisesWhenRowMissing() {
        deleteDatabaseDefaults();
        try {
            assertSqlState("P0002",
                    () -> dsl.selectFrom(GET_DATABASE_LOCK_SETTINGS.call()).fetch());
        } finally {
            restoreDatabaseDefaults(DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
        }
    }

    /**
     * set_database_lock_settings updates the row, and the change is visible to both the
     * table and get_database_lock_settings.
     */
    @Test
    void setLockSettingsUpdatesValues() {
        setDatabaseLockSettings(250, 500, 60);

        Record row = tableRow();
        assertNotNull(row);
        assertLockSettings(row, 250, 500, 60);
        Record accessorRow = getDatabaseLockSettings();
        assertNotNull(accessorRow);
        assertLockSettings(accessorRow, 250, 500, 60);
    }

    /**
     * The documented lower boundary zero is accepted for every setting.
     */
    @Test
    void setLockSettingsAcceptsZero() {
        setDatabaseLockSettings(0, 0, 0);

        Record row = tableRow();
        assertNotNull(row);
        assertLockSettings(row, 0, 0, 0);
        Record accessorRow = getDatabaseLockSettings();
        assertNotNull(accessorRow);
        assertLockSettings(accessorRow, 0, 0, 0);
    }

    /**
     * set_database_lock_settings rejects null and negative values through the
     * non_negative_integer domain.
     */
    @Test
    void setLockSettingsRejectsInvalidValues() {
        assertDomainViolation(() -> setDatabaseLockSettings(null, 1000, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, null, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, 1000, null));
        assertDomainViolation(() -> setDatabaseLockSettings(-1, 1000, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, -1, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, 1000, -1));
    }

    private Record tableRow() {
        return dsl.select(DATABASE_LOCK_SETTINGS.DDL_LOCK_TIMEOUT,
                        DATABASE_LOCK_SETTINGS.SLEEP_TIME,
                        DATABASE_LOCK_SETTINGS.STATEMENT_DURATION)
                .from(DATABASE_LOCK_SETTINGS)
                .where(DATABASE_LOCK_SETTINGS.ID.eq(1))
                .fetchOne();
    }

}
