package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import io.github.eyupmiduck.changelogvalidator.testing.PublishedRelease;
import io.github.eyupmiduck.changelogvalidator.testing.UpgradeContract;
import org.junit.jupiter.api.Disabled;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the incremental upgrade from the most recent published release of
 * ddl_utils to the current codebase, inheriting the shared
 * {@link UpgradeContract}: the released changelog's SQL is applied to a scratch
 * database, then the current changelog, so an upgrade that only works on an
 * empty database fails here.
 *
 * <p>The previous release is resolved from GitHub Packages automatically, and
 * the container configuration is shared with the regular test base (see
 * {@link PostgresTestBase}).
 *
 * <p><strong>Disabled for the living-changelog baseline rework</strong> (epic
 * {@code lv-ahk}, bead {@code lv-ahk.1}). The changelog is being reworked in
 * place and no longer matches the last published release, so this test cannot
 * pass until a normal baseline release has been cut from {@code main}. It is
 * re-enabled as {@code BaselineUpgradeTest} after that release; the shared
 * upgrade support and the {@code upgrade.current.version} property are
 * deliberately retained for that step.
 */
@Disabled("epic lv-ahk / bead lv-ahk.1: changelog baseline rework; "
        + "re-enable as BaselineUpgradeTest after the baseline release")
class LiquibaseUpgradeTest extends UpgradeContract {

    /**
     * The published coordinates and the current version used to find the
     * previous release. The version is supplied by the Maven build through the
     * {@code upgrade.current.version} system property; running this test outside
     * Maven fails fast rather than silently using an unresolvable snapshot.
     *
     * @return the release coordinates
     */
    @Override
    protected PublishedRelease.Coordinates releaseCoordinates() {
        String currentVersion = System.getProperty("upgrade.current.version");
        if (currentVersion == null || currentVersion.isBlank()) {
            throw new IllegalStateException(
                    "upgrade.current.version is not set; run through Maven or pass "
                            + "-Dupgrade.current.version=<version>");
        }
        return new PublishedRelease.Coordinates(
                "https://maven.pkg.github.com/eyupmiduck/ddl_utils",
                "io.github.eyupmiduck", "ddl_utils", currentVersion);
    }

    /**
     * Returns the same project profile as the regular test base, so the two
     * classes cannot drift.
     *
     * @return the project profile
     */
    @Override
    protected ProjectProfile projectProfile() {
        return PostgresTestBase.PROFILE;
    }

    /**
     * The upgraded database has every managed schema. The schema names come
     * from {@link #managedSchemas()} rather than being repeated in the query, so
     * the expected value and the thing it counts cannot drift apart.
     */
    @Override
    protected void assertUpgradedDatabase() {
        Collection<String> schemas = managedSchemas();
        if (schemas.isEmpty()) {
            throw new IllegalStateException("managedSchemas() must not be empty");
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(schemas.size(), "?"));
        Integer found = upgradeDsl().fetchOne(
                        "SELECT count(*) FROM pg_namespace WHERE nspname IN (" + placeholders + ")",
                        schemas.toArray())
                .get(0, Integer.class);
        assertEquals(schemas.size(), found,
                () -> "the upgraded database should have the managed schemas " + schemas);
    }
}
