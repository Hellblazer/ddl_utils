package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogLayoutContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;

/**
 * Verifies the in-place changelog layout, inheriting the shared
 * {@link ChangelogLayoutContract}: a changeset's SQL and rollback paths match its
 * id, and a routine or trigger file declares the object its path names.
 */
class ChangelogLayoutTest extends ChangelogLayoutContract {

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
