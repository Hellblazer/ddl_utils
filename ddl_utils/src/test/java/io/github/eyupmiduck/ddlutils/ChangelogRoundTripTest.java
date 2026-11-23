package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogRoundTripContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;

/**
 * Applies the changelog, rolls every changeset back, checks the managed schemas
 * are gone, and re-applies, inheriting the shared
 * {@link ChangelogRoundTripContract}.
 */
class ChangelogRoundTripTest extends ChangelogRoundTripContract {

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
