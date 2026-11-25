package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies {@code ddl_utils_lib.operation_lock_key}: the advisory-lock key is
 * deterministic, independent of key order (so reciprocal foreign-key calls
 * share a lock), and sensitive to the context and every key.
 */
class OperationLockKeyTest extends PostgresTestBase {

    /**
     * The same keys in either order produce the same lock, which lets
     * {@code ensure_foreign_key(A -> B)} and {@code (B -> A)} serialize.
     */
    @Test
    void isOrderIndependent() {
        assertEquals(
                operationLockKey("ddl_utils.ensure_foreign_key", "public.a", "public.b"),
                operationLockKey("ddl_utils.ensure_foreign_key", "public.b", "public.a"));
    }

    /**
     * The context and the keys are all part of the key, so different operations
     * or tables do not serialize against each other.
     */
    @Test
    void dependsOnContextAndEveryKey() {
        assertNotEquals(
                operationLockKey("ddl_utils.ensure_not_null", "public.a", "id"),
                operationLockKey("ddl_utils.ensure_check_constraint", "public.a", "id"));
        assertNotEquals(
                operationLockKey("ddl_utils.ensure_not_null", "public.a", "id"),
                operationLockKey("ddl_utils.ensure_not_null", "public.a", "note"));
    }

    private long operationLockKey(String context, String... keys) {
        String placeholders = String.join(", ", Collections.nCopies(keys.length, "?"));
        Object[] bindings = new Object[keys.length + 1];
        bindings[0] = context;
        System.arraycopy(keys, 0, bindings, 1, keys.length);
        return ((Number) dsl.fetchValue(
                "SELECT ddl_utils_lib.operation_lock_key(?, ARRAY[" + placeholders
                        + "]::ddl_utils.non_empty_non_null_text_array)",
                bindings)).longValue();
    }
}
