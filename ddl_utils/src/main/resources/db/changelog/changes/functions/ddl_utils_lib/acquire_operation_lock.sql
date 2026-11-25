CREATE OR REPLACE FUNCTION ddl_utils_lib.acquire_operation_lock(
    i_context ddl_utils.non_null_text,
    i_keys ddl_utils.non_empty_non_null_text_array
)
    RETURNS void
    LANGUAGE plpgsql
    VOLATILE
    SECURITY INVOKER
AS
$$
BEGIN
    -- Transaction-scoped: released by the next COMMIT in a committed procedure,
    -- and re-acquired at the start of every step. It serializes concurrent
    -- calls but does not block external DDL.
    PERFORM pg_catalog.pg_advisory_xact_lock(
            ddl_utils_lib.operation_lock_key(i_context, i_keys));
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.acquire_operation_lock(
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
) IS
    'Takes the transaction-scoped advisory lock for an operation context and '
        'its identifier keys; released by COMMIT and re-taken per step.';
