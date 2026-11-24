package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ExtensionContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;

import java.util.List;

/**
 * Verifies that the extension compiled into the custom image is available to
 * install and installed in every test database: {@code plpgsql_check}, which the
 * routine analyser uses. The shared {@link ExtensionContract} performs the
 * checks.
 */
class PostgresExtensionsTest extends ExtensionContract {

    /**
     * The extension the image provides.
     *
     * @return the extension names
     */
    @Override
    protected List<String> extensions() {
        return List.of("plpgsql_check");
    }

    /**
     * The ddl_utils profile, so the contract reads the same container and
     * changelog as the other tests.
     *
     * @return the ddl_utils profile
     */
    @Override
    protected ProjectProfile projectProfile() {
        return PostgresTestBase.PROFILE;
    }
}
