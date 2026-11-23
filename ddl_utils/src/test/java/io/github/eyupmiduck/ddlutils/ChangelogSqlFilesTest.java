package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogSqlFilesContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;

/**
 * Verifies that every SQL file in the Liquibase changelog is referenced by a
 * changelog XML file reachable from the master changelog, inheriting the shared
 * {@link ChangelogSqlFilesContract}.
 */
class ChangelogSqlFilesTest extends ChangelogSqlFilesContract {

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
