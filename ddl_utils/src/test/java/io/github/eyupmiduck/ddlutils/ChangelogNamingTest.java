package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogNamingContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;

/**
 * Verifies the naming conventions enforced on the Liquibase changelog, inheriting
 * the shared {@link ChangelogNamingContract}: SQL files must start with a
 * three-digit prefix, and changeSet ids must follow the shared pattern. The
 * routine/trigger directories are exempt from the file-name rule, as documented
 * in AGENTS.md.
 */
class ChangelogNamingTest extends ChangelogNamingContract {

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
