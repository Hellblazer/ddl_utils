package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import io.github.eyupmiduck.changelogvalidator.testing.TrackingTablesContract;

/**
 * Verifies that Liquibase keeps its tracking tables out of the application
 * schemas: they live in the dedicated {@code liquibase} schema, prefixed with
 * {@code ddl_utils_}, rather than as the generic {@code databasechangelog} /
 * {@code databasechangeloglock} tables. Inherits the shared
 * {@link TrackingTablesContract}; the schemas come from the project profile.
 */
class LiquibaseTrackingTablesTest extends TrackingTablesContract {

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
