package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.RoutineSignatureContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;

/**
 * Verifies that every routine name in the {@code ddl_utils} and
 * {@code ddl_utils_lib} schemas has a single signature, inheriting the shared
 * {@link RoutineSignatureContract}.
 *
 * <p>A parameter-type change in a {@code CREATE OR REPLACE} body creates a new
 * overload and leaves the old one installed; the contract fails the build before
 * such a stale signature can reach a release. A deliberate overload must be
 * added to {@link #allowedOverloads()} with its schema-qualified name, and an
 * entry that no longer corresponds to an actual overload fails too.
 */
class RoutineSignatureTest extends RoutineSignatureContract {

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
