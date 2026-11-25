package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogTestSupport;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

import static io.github.eyupmiduck.ddlutils.jooq.ddl_utils.tables.DatabaseLockSettings.DATABASE_LOCK_SETTINGS;
import static io.github.eyupmiduck.ddlutils.jooq.ddl_utils.tables.GetDatabaseLockSettings.GET_DATABASE_LOCK_SETTINGS;
import static io.github.eyupmiduck.ddlutils.jooq.ddl_utils.tables.GetLockSettings.GET_LOCK_SETTINGS;
import static io.github.eyupmiduck.ddlutils.jooq.ddl_utils.tables.GetSchemaLockSettings.GET_SCHEMA_LOCK_SETTINGS;
import static io.github.eyupmiduck.ddlutils.jooq.ddl_utils.tables.GetTableLockSettings.GET_TABLE_LOCK_SETTINGS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ddl_utils' PostgreSQL test base: the shared
 * {@link io.github.eyupmiduck.changelogvalidator.testing.PostgresTestBase}
 * configured for the ddl_utils roles, schemas and changelog, plus the
 * ddl_utils lock-settings and {@code ensure_not_null} state helpers. The
 * generic lock, catalog and table helpers are inherited.
 *
 * <p>The shared base starts one container, migrates a template database from
 * the production changelog and clones a private database per test class as the
 * {@code ddl_utils_test} role. The custom image's init script creates the
 * roles and installs {@code plpgsql_check}.
 */
abstract class PostgresTestBase extends io.github.eyupmiduck.changelogvalidator.testing.PostgresTestBase {

    /**
     * The per-attempt lock timeout in ms used by tests that call the
     * explicit-settings {@code ddl_utils_lib} helpers.
     */
    protected static final int DDL_LOCK_TIMEOUT = 1000;

    /**
     * The retry sleep in ms used by tests that call the explicit-settings
     * helpers.
     */
    protected static final int SLEEP_TIME = 10;

    /**
     * The statement budget in ms used by tests that call the explicit-settings
     * helpers.
     */
    protected static final int STATEMENT_DURATION = 5000;

    /**
     * The application schemas managed by this changelog, shared by the tests that
     * enumerate them.
     */
    static final String DDL_UTILS_SCHEMA = "ddl_utils";
    static final String DDL_UTILS_LIB_SCHEMA = "ddl_utils_lib";

    /**
     * The schemas that hold the project's routines, derived from the schema
     * constants so a rename cannot leave an enumeration query inspecting a
     * schema that no longer exists.
     */
    static final List<String> ROUTINE_SCHEMAS = List.of(DDL_UTILS_SCHEMA, DDL_UTILS_LIB_SCHEMA);

    /**
     * The rollback directories that mirror the per-routine function and
     * procedure SQL files, shared by the rollback contract test.
     */
    static final List<String> ROUTINE_ROLLBACK_DIRECTORIES = List.of(
            "db/changelog/changes/functions-rollback",
            "db/changelog/changes/procedures-rollback");

    /**
     * The application role the grant changeset grants EXECUTE to.
     */
    static final String DDL_UTILS_CALLER = "ddl_utils_caller";

    static final String DATABASE_CHANGELOG_TABLE = "ddl_utils_databasechangelog";
    static final String DATABASE_CHANGELOG_LOCK_TABLE = "ddl_utils_databasechangeloglock";

    /**
     * The container configuration, shared by this base and
     * {@link BaselineUpgradeTest} so the two cannot drift (the container's
     * template database is prepared once per JVM from the first class that
     * runs, so an inconsistent configuration would be order-dependent).
     */
    static final String POSTGRES_IMAGE = "ddl-utils-postgres:17-alpine";
    static final String DATABASE = "ddl_utils";
    static final String OWNER_USER = "ddl_utils_owner";
    static final String OWNER_PASSWORD = "ddl_utils_owner";
    static final String TEST_USER = "ddl_utils_test";
    static final String TEST_PASSWORD = "ddl_utils_test";

    /**
     * The one container and changelog configuration, returned by this class and
     * by {@link BaselineUpgradeTest} so the two cannot drift.
     */
    static final Configuration CONFIGURATION = new Configuration(
            DATABASE, OWNER_USER, OWNER_PASSWORD, TEST_USER, TEST_PASSWORD,
            POSTGRES_IMAGE, ChangelogTestSupport.MASTER_RESOURCE,
            DATABASE_CHANGELOG_TABLE, DATABASE_CHANGELOG_LOCK_TABLE, null,
            PostgresTestBase::installProjectExtensions);

    /**
     * The one project profile, shared by this base and every contract so a
     * schema, role or directory rename cannot drift between them.
     */
    static final ProjectProfile PROFILE = ProjectProfile.builder(CONFIGURATION)
            .managedSchemas(DDL_UTILS_SCHEMA, DDL_UTILS_LIB_SCHEMA)
            .routineSchemas(ROUTINE_SCHEMAS)
            .callerRole(DDL_UTILS_CALLER)
            .routineRollbackDirectories(ROUTINE_ROLLBACK_DIRECTORIES)
            .triggerRollbackDirectories("db/changelog/changes/triggers-rollback")
            .roundTripSchemas(DDL_UTILS_SCHEMA, DDL_UTILS_LIB_SCHEMA)
            .build();
    /**
     * The database defaults captured by {@link #deleteDatabaseDefaults}, so
     * {@link #restoreDeletedDatabaseDefaults} can put the row back after the
     * test even when it fails before restoring explicitly.
     */
    private Integer deletedDdlLockTimeout;
    private Integer deletedSleepTime;
    private Integer deletedStatementDuration;

    /**
     * Asserts that a lock-settings row holds the expected values.
     *
     * @param row               the settings row
     * @param ddlLockTimeout    the expected {@code ddl_lock_timeout} in ms
     * @param sleepTime         the expected {@code sleep_time} in ms
     * @param statementDuration the expected {@code statement_duration} in ms
     */
    protected static void assertLockSettings(Record row, int ddlLockTimeout, int sleepTime, int statementDuration) {
        assertEquals(ddlLockTimeout, row.get("ddl_lock_timeout", Integer.class));
        assertEquals(sleepTime, row.get("sleep_time", Integer.class));
        assertEquals(statementDuration, row.get("statement_duration", Integer.class));
    }

    /**
     * Asserts that every listed schema exists and holds at least one function
     * or procedure, so an enumeration query built from the list cannot pass by
     * inspecting nothing.
     *
     * @param dsl     the connection to query the catalog with
     * @param schemas the routine schemas to check
     */
    static void assertRoutineSchemasArePopulated(DSLContext dsl, Collection<String> schemas) {
        assertFalse(schemas.isEmpty(), "at least one routine schema is required");
        for (String schema : schemas) {
            Record row = dsl.fetchOne("""
                    SELECT count(*)::int AS routine_count
                    FROM pg_proc AS p
                    JOIN pg_namespace AS n ON n.oid = p.pronamespace
                    WHERE n.nspname = ? AND p.prokind IN ('f', 'p')
                    """, schema);
            assertNotNull(row, () -> "no routine count returned for schema " + schema);
            Integer routines = row.get("routine_count", Integer.class);
            assertTrue(routines != null && routines > 0,
                    () -> "no routines found in schema " + schema);
        }
    }

    /**
     * Installs the static-analysis extension (compiled into the custom image)
     * so a database has it before the changelog runs. Shared with
     * {@link BaselineUpgradeTest}, whose scratch database needs the same
     * extension.
     *
     * @param admin a superuser connection to the target database
     * @throws SQLException if the extension cannot be installed
     */
    static void installProjectExtensions(Connection admin) throws SQLException {
        try (Statement statement = admin.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS plpgsql_check");
        }
    }

    @Override
    protected ProjectProfile projectProfile() {
        return PROFILE;
    }

    /**
     * Returns the deterministic name of the temporary CHECK constraint the
     * {@code ddl_utils.ensure_not_null} procedure uses for a column, so a test
     * can reproduce the catalog state left by an interrupted run.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the temporary constraint name
     */
    protected String notNullCheckConstraintName(String schema, String table, String column) {
        String digest = dsl.fetchOne("SELECT substr(md5(? || '.' || ? || '.' || ?), 1, 8)",
                schema, table, column).get(0, String.class);
        // Mirror the procedure: replace non-ASCII characters so the prefix is
        // measured in bytes before it is truncated to 45 characters.
        String prefix = (table + "_" + column + "_not_null").replaceAll("[^A-Za-z0-9_]", "_");
        if (prefix.length() > 45) {
            prefix = prefix.substring(0, 45);
        }
        return prefix + "_" + digest;
    }

    /**
     * Reproduces the catalog state left when {@code ddl_utils.ensure_not_null}
     * committed its first step but the call ended before validation: the
     * temporary CHECK constraint exists but is {@code NOT VALID}.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     */
    protected void simulateNotNullCheckAdded(String schema, String table, String column) {
        dsl.execute("ALTER TABLE " + quoteIdentifier(schema) + "." + quoteIdentifier(table)
                + " ADD CONSTRAINT " + quoteIdentifier(notNullCheckConstraintName(schema, table, column))
                + " CHECK (" + quoteIdentifier(column) + " IS NOT NULL) NOT VALID");
    }

    /**
     * Reproduces the catalog state left when {@code ddl_utils.ensure_not_null}
     * committed its first two steps but the call ended before {@code SET NOT
     * NULL}: the temporary CHECK constraint exists and is valid.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     */
    protected void simulateNotNullCheckValidated(String schema, String table, String column) {
        simulateNotNullCheckAdded(schema, table, column);
        dsl.execute("ALTER TABLE " + quoteIdentifier(schema) + "." + quoteIdentifier(table)
                + " VALIDATE CONSTRAINT " + quoteIdentifier(notNullCheckConstraintName(schema, table, column)));
    }

    /**
     * Returns the advisory-lock key {@code ddl_utils.ensure_not_null} uses, so
     * a test can queue on the same key and pause the procedure between
     * committed steps. The expression mirrors the procedure's key; the gate
     * verifies that the procedure actually waits on it, so a key change fails
     * the test instead of silently removing the interleaving.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the advisory-lock key
     */
    protected long ensureNotNullLockKey(String schema, String table, String column) {
        return ((Number) dsl.fetchValue("""
                SELECT pg_catalog.hashtextextended(
                        pg_catalog.format('ddl_utils.ensure_not_null|%I.%I.%I', ?, ?, ?), 0)
                """, schema, table, column)).longValue();
    }

    /**
     * Returns the advisory-lock key {@code ddl_utils.ensure_check_constraint}
     * uses, so a test can pause that procedure between its committed steps.
     *
     * @param schema the table schema
     * @param table  the table name
     * @return the advisory-lock key
     */
    protected long ensureCheckConstraintLockKey(String schema, String table) {
        return ((Number) dsl.fetchValue("""
                SELECT pg_catalog.hashtextextended(
                        pg_catalog.format('ddl_utils.ensure_check_constraint|%I.%I', ?, ?), 0)
                """, schema, table)).longValue();
    }

    /**
     * Returns the advisory-lock key {@code ddl_utils.ensure_foreign_key} uses,
     * so a test can pause that procedure between its committed steps.
     *
     * @param schema           the source table schema
     * @param table            the source table name
     * @param referencedSchema the referenced table schema
     * @param referencedTable  the referenced table name
     * @return the advisory-lock key
     */
    protected long ensureForeignKeyLockKey(String schema, String table,
                                           String referencedSchema, String referencedTable) {
        return ((Number) dsl.fetchValue("""
                SELECT pg_catalog.hashtextextended(
                        pg_catalog.format('ddl_utils.ensure_foreign_key|%s|%s',
                            least(pg_catalog.format('%I.%I', ?, ?),
                                  pg_catalog.format('%I.%I', ?, ?)),
                            greatest(pg_catalog.format('%I.%I', ?, ?),
                                     pg_catalog.format('%I.%I', ?, ?))), 0)
                """, schema, table, referencedSchema, referencedTable,
                schema, table, referencedSchema, referencedTable)).longValue();
    }

    /**
     * Returns the number of temporary NOT NULL proof CHECK constraints for a
     * column. Only CHECK constraints count: PostgreSQL 18+ also records the
     * column's NOT NULL as a {@code pg_constraint} row. The count comes from a
     * single aggregate row, so a missing or NULL count is a broken helper, not
     * an empty result, and raises instead of returning a sentinel.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the number of matching constraints
     */
    protected int notNullProofConstraintCount(String schema, String table, String column) {
        Record row = dsl.fetchOne(
                """
                        SELECT count(*)::int AS constraint_count
                        FROM pg_constraint
                        WHERE conrelid = (SELECT oid FROM pg_class WHERE relname = ?
                                            AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?))
                            AND contype = 'c'
                            AND conname = ?
                        """,
                table, schema, notNullCheckConstraintName(schema, table, column));
        assertNotNull(row, () -> "no NOT NULL proof constraint count for " + schema + "." + table + "." + column);
        Integer count = row.get("constraint_count", Integer.class);
        assertNotNull(count, () -> "NULL NOT NULL proof constraint count for " + schema + "." + table + "." + column);
        return count;
    }

    /**
     * Sets the table-level lock settings.
     *
     * @param schema      the table schema
     * @param table       the table name
     * @param lockTimeout the per-attempt lock timeout in ms
     * @param sleepTime   the retry sleep in ms
     * @param duration    the statement budget in ms
     */
    protected void setTableLockSettings(String schema, String table, Integer lockTimeout,
                                        Integer sleepTime, Integer duration) {
        Routines.setTableLockSettings(dsl.configuration(), schema, table, lockTimeout, sleepTime, duration);
    }

    /**
     * Clears the table-level lock settings.
     *
     * @param schema the table schema
     * @param table  the table name
     */
    protected void clearTableLockSettings(String schema, String table) {
        Routines.clearTableLockSettings(dsl.configuration(), schema, table);
    }

    /**
     * Returns the table-level settings row, or {@code null} when there is none.
     *
     * @param schema the table schema
     * @param table  the table name
     * @return the settings row, or {@code null}
     */
    protected Record getTableLockSettings(String schema, String table) {
        return dsl.selectFrom(GET_TABLE_LOCK_SETTINGS.call(schema, table)).fetchOne();
    }

    /**
     * Sets the schema-level lock settings.
     *
     * @param schema      the schema name
     * @param lockTimeout the per-attempt lock timeout in ms
     * @param sleepTime   the retry sleep in ms
     * @param duration    the statement budget in ms
     */
    protected void setSchemaLockSettings(String schema, Integer lockTimeout,
                                         Integer sleepTime, Integer duration) {
        Routines.setSchemaLockSettings(dsl.configuration(), schema, lockTimeout, sleepTime, duration);
    }

    /**
     * Clears the schema-level lock settings.
     *
     * @param schema the schema name
     */
    protected void clearSchemaLockSettings(String schema) {
        Routines.clearSchemaLockSettings(dsl.configuration(), schema);
    }

    /**
     * Returns the schema-level settings row, or {@code null} when there is none.
     *
     * @param schema the schema name
     * @return the settings row, or {@code null}
     */
    protected Record getSchemaLockSettings(String schema) {
        return dsl.selectFrom(GET_SCHEMA_LOCK_SETTINGS.call(schema)).fetchOne();
    }

    /**
     * Sets the database-level lock settings.
     *
     * @param lockTimeout the per-attempt lock timeout in ms
     * @param sleepTime   the retry sleep in ms
     * @param duration    the statement budget in ms
     */
    protected void setDatabaseLockSettings(Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.setDatabaseLockSettings(dsl.configuration(), lockTimeout, sleepTime, duration);
    }

    /**
     * Returns the database-level settings row.
     *
     * @return the settings row
     */
    protected Record getDatabaseLockSettings() {
        return dsl.selectFrom(GET_DATABASE_LOCK_SETTINGS.call()).fetchOne();
    }

    /**
     * Deletes the singleton database defaults row, for tests that exercise the
     * missing-row path. The row's values are captured and restored
     * automatically after the test, so the cleanup cannot be forgotten; the
     * row must exist first, so a test cannot believe it exercised the
     * missing-row path when the row was already gone.
     */
    protected void deleteDatabaseDefaults() {
        // Read the table directly: ddl_utils.get_database_lock_settings() raises
        // P0002 when the row is missing, so it can never return an empty result
        // and the null guard below would be dead code.
        Record row = dsl.select(DATABASE_LOCK_SETTINGS.DDL_LOCK_TIMEOUT,
                        DATABASE_LOCK_SETTINGS.SLEEP_TIME,
                        DATABASE_LOCK_SETTINGS.STATEMENT_DURATION)
                .from(DATABASE_LOCK_SETTINGS)
                .where(DATABASE_LOCK_SETTINGS.ID.eq(1))
                .fetchOne();
        if (row == null) {
            throw new IllegalStateException(
                    "the database defaults row is already missing; nothing was deleted");
        }
        deletedDdlLockTimeout = row.get(DATABASE_LOCK_SETTINGS.DDL_LOCK_TIMEOUT);
        deletedSleepTime = row.get(DATABASE_LOCK_SETTINGS.SLEEP_TIME);
        deletedStatementDuration = row.get(DATABASE_LOCK_SETTINGS.STATEMENT_DURATION);
        try (Connection connection = openOwnerConnection()) {
            DSL.using(connection, SQLDialect.POSTGRES)
                    .deleteFrom(DATABASE_LOCK_SETTINGS)
                    .where(DATABASE_LOCK_SETTINGS.ID.eq(1))
                    .execute();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to delete the database defaults", e);
        }
    }

    /**
     * Restores the database defaults deleted by {@link #deleteDatabaseDefaults}
     * with the values captured at deletion time. Runs after every test and is a
     * no-op when nothing was deleted, so the delete/restore pair is structural
     * rather than a convention the caller must remember.
     */
    @AfterEach
    void restoreDeletedDatabaseDefaults() {
        Integer ddlLockTimeout = deletedDdlLockTimeout;
        Integer sleepTime = deletedSleepTime;
        Integer statementDuration = deletedStatementDuration;
        deletedDdlLockTimeout = null;
        deletedSleepTime = null;
        deletedStatementDuration = null;
        if (ddlLockTimeout == null || sleepTime == null || statementDuration == null) {
            return;
        }
        restoreDatabaseDefaults(ddlLockTimeout, sleepTime, statementDuration);
    }

    /**
     * Recreates the singleton database defaults row with the given values, for
     * tests that deleted it. A raw INSERT is needed because
     * {@code set_database_lock_settings} raises when the row is missing.
     *
     * @param ddlLockTimeout    the {@code ddl_lock_timeout} to restore
     * @param sleepTime         the {@code sleep_time} to restore
     * @param statementDuration the {@code statement_duration} to restore
     */
    protected void restoreDatabaseDefaults(int ddlLockTimeout, int sleepTime, int statementDuration) {
        try (Connection connection = openOwnerConnection()) {
            DSL.using(connection, SQLDialect.POSTGRES)
                    .insertInto(DATABASE_LOCK_SETTINGS,
                            DATABASE_LOCK_SETTINGS.ID,
                            DATABASE_LOCK_SETTINGS.DDL_LOCK_TIMEOUT,
                            DATABASE_LOCK_SETTINGS.SLEEP_TIME,
                            DATABASE_LOCK_SETTINGS.STATEMENT_DURATION)
                    .values(1, ddlLockTimeout, sleepTime, statementDuration)
                    .onConflict(DATABASE_LOCK_SETTINGS.ID)
                    .doUpdate()
                    .set(DATABASE_LOCK_SETTINGS.DDL_LOCK_TIMEOUT, ddlLockTimeout)
                    .set(DATABASE_LOCK_SETTINGS.SLEEP_TIME, sleepTime)
                    .set(DATABASE_LOCK_SETTINGS.STATEMENT_DURATION, statementDuration)
                    .execute();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to restore the database defaults", e);
        }
    }

    /**
     * Returns the effective settings for a table (table, then schema, then
     * database).
     *
     * @param schema the table schema
     * @param table  the table name
     * @return the settings row
     */
    protected Record getLockSettings(String schema, String table) {
        return dsl.selectFrom(GET_LOCK_SETTINGS.call(schema, table)).fetchOne();
    }
}
