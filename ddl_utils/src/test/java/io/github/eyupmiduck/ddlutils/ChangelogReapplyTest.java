package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogReapplyContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;

/**
 * Applies the changelog twice and requires the second run to be a no-op,
 * inheriting the shared {@link ChangelogReapplyContract}.
 */
class ChangelogReapplyTest extends ChangelogReapplyContract {

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
