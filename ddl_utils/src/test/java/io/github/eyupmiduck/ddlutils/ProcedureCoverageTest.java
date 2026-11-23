package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.PlpgsqlCheck;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that the routine tooling covers stored procedures, not just functions:
 * {@code plpgsql_check} reports findings for a deliberately broken procedure, so
 * a future broken procedure cannot slip through the analyser. The procedure-kind
 * check itself is part of {@link RoutineSecurityTest}, which declares the
 * expected procedures in the shared contract.
 */
class ProcedureCoverageTest extends PostgresTestBase {

    /**
     * {@code plpgsql_check} reports findings for a broken procedure, proving the
     * analyser inspects procedures and not only functions.
     */
    @Test
    void plpgsqlCheckReportsBrokenProcedure() throws Exception {
        dsl.execute("DROP PROCEDURE IF EXISTS " + PUBLIC_SCHEMA + ".broken_procedure_probe()");
        dsl.execute("CREATE OR REPLACE PROCEDURE " + PUBLIC_SCHEMA + ".broken_procedure_probe()"
                + " LANGUAGE plpgsql AS $$\n"
                + "DECLARE l_x int;\n"
                + "BEGIN\n"
                + "    SELECT nonexistent_col INTO l_x FROM pg_class;\n"
                + "END;\n"
                + "$$");
        try (Connection owner = openOwnerConnection()) {
            List<PlpgsqlCheck.Finding> findings =
                    PlpgsqlCheck.findFindings(owner, List.of(PUBLIC_SCHEMA));

            PlpgsqlCheck.Finding broken = findings.stream()
                    .filter(finding -> finding.function() != null
                            && finding.function().contains("broken_procedure_probe"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "no plpgsql_check finding for the deliberately broken procedure; findings: " + findings));
            String message = broken.message();
            assertNotNull(message, "the finding must carry a message");
            assertTrue(message.contains("nonexistent_col"),
                    () -> "the finding should name the missing column: " + message);
        } finally {
            try {
                dsl.execute("DROP PROCEDURE IF EXISTS " + PUBLIC_SCHEMA + ".broken_procedure_probe()");
            } catch (RuntimeException ignored) {
                // Do not let a cleanup failure replace the primary assertion.
            }
        }
    }
}
