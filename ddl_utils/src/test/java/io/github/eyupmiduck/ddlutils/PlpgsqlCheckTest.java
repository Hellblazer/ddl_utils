package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.PlpgsqlCheckContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import org.junit.jupiter.api.Test;

/**
 * Runs the {@code plpgsql_check} static analyser over every routine in the
 * {@code ddl_utils} and {@code ddl_utils_lib} schemas, inheriting the shared
 * {@link PlpgsqlCheckContract}.
 *
 * <p>The extension is compiled into the custom image and installed into the
 * template database, so each cloned test database (and the dev database, via
 * {@code scripts/postgres/roles.sql}) has it.
 */
class PlpgsqlCheckTest extends PlpgsqlCheckContract {

    /**
     * Every configured routine schema exists and holds at least one routine, so
     * the inherited contract cannot pass by analysing an empty set.
     */
    @Test
    void routineSchemasArePopulated() {
        PostgresTestBase.assertRoutineSchemasArePopulated(dsl, routineSchemas());
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
