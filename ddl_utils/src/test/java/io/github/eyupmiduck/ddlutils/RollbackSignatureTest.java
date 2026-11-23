package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import io.github.eyupmiduck.changelogvalidator.testing.RollbackSignatureContract;
import io.github.eyupmiduck.changelogvalidator.testing.TestResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the routine rollback files, inheriting the shared
 * {@link RollbackSignatureContract}: every routine must have a
 * {@code DROP FUNCTION/PROCEDURE IF EXISTS <signature>} rollback that resolves
 * to an existing routine, and no signature may be dropped twice.
 *
 * <p>Because {@code IF EXISTS} makes a stale DROP a silent no-op, a signature
 * change that is not propagated to the rollback would otherwise go unnoticed
 * until a rollback left the routine behind.
 */
class RollbackSignatureTest extends RollbackSignatureContract {

    /**
     * Every configured routine schema holds at least one routine and every
     * rollback directory holds at least one SQL file, so the inherited contract
     * cannot pass by inspecting an empty set on either side.
     *
     * @throws java.io.IOException if a rollback directory cannot be read
     */
    @Test
    void routineSchemasAndRollbackDirectoriesArePopulated() throws java.io.IOException {
        PostgresTestBase.assertRoutineSchemasArePopulated(dsl, routineSchemas());
        assertFalse(rollbackDirectories().isEmpty(), "at least one rollback directory is required");
        for (String directory : rollbackDirectories()) {
            assertFalse(TestResources.readSqlFiles(directory).isEmpty(),
                    () -> "no rollback SQL files found in " + directory);
        }
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
