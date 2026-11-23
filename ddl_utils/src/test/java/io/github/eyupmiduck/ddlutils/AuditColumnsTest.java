package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.AuditColumnsContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Applies the shared {@link AuditColumnsContract} to the {@code ddl_utils}
 * schema, verifying that every lock-settings table follows the project-wide
 * audit-column convention and that the shared trigger refreshes
 * {@code updated_at} and preserves {@code created_at} on a real row.
 */
class AuditColumnsTest extends AuditColumnsContract {

    /**
     * The probe schema and table whose lock-settings rows seed the behavioral
     * probe. The seed and the cleanup share these constants so a drifted
     * cleanup cannot silently leave rows behind.
     */
    private static final String PROBE_SCHEMA = "audit_columns_test_schema";
    private static final String PROBE_TABLE = "audit_columns_test_table";

    private static final Map<String, Collection<String>> PROBE_TABLES = Map.of(
            PostgresTestBase.DDL_UTILS_SCHEMA,
            List.of("database_lock_settings", "schema_lock_settings", "table_lock_settings"));

    /**
     * Seeds the schema- and table-level lock-settings rows the behavioral probe
     * needs; the database row is part of the changelog.
     */
    @BeforeEach
    void seedAuditFixtures() {
        dsl.fetchOne("SELECT ddl_utils.set_schema_lock_settings(?, ?, ?, ?)",
                PROBE_SCHEMA, 100, 1000, 30);
        dsl.fetchOne("SELECT ddl_utils.set_table_lock_settings(?, ?, ?, ?, ?)",
                PROBE_SCHEMA, PROBE_TABLE, 100, 1000, 30);
    }

    /**
     * Removes the rows the behavioral probe seeds, even when the test fails.
     * Both cleanups are attempted; the first failure is preserved and the second
     * is attached as suppressed.
     */
    @AfterEach
    void clearAuditFixtures() {
        RuntimeException failure = null;
        try {
            dsl.fetchOne("SELECT ddl_utils.clear_table_lock_settings(?, ?)", PROBE_SCHEMA, PROBE_TABLE);
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            dsl.fetchOne("SELECT ddl_utils.clear_schema_lock_settings(?)", PROBE_SCHEMA);
        } catch (RuntimeException e) {
            if (failure != null) {
                failure.addSuppressed(e);
            } else {
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * The lock-settings tables are probed on a real row.
     *
     * @return the probe tables
     */
    @Override
    protected Map<String, Collection<String>> probeTables() {
        return PROBE_TABLES;
    }

    /**
     * The ddl_utils profile, so this contract uses the project's roles, image,
     * changelog and schemas rather than the library defaults.
     *
     * @return the ddl_utils profile
     */
    @Override
    protected ProjectProfile projectProfile() {
        return PostgresTestBase.PROFILE;
    }
}
