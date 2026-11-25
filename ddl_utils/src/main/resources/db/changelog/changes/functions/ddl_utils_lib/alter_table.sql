CREATE OR REPLACE FUNCTION ddl_utils_lib.alter_table(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_alter_table_fragment ddl_utils.non_null_text,
    i_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_sleep_time ddl_utils.non_negative_integer,
    i_statement_duration ddl_utils.non_negative_integer
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_statement text;
BEGIN
    -- PostgreSQL truncates an over-long identifier with only a NOTICE, so an
    -- over-long schema or table name would silently target a different relation.
    -- Guard the runner itself: every structured helper reaches ALTER TABLE here.
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_schema_name,
            i_context => 'ddl_utils_lib.alter_table',
            i_argument => 'i_schema_name');
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_table_name,
            i_context => 'ddl_utils_lib.alter_table',
            i_argument => 'i_table_name');

    IF pg_catalog.btrim(i_alter_table_fragment) = '' THEN
        RAISE EXCEPTION 'ddl_utils_lib.alter_table: the alter table fragment must not be blank'
            USING ERRCODE = '22023';
    END IF;

    -- The fragment is arbitrary DDL, so it cannot be parameterised. Reject a
    -- statement separator or comment that appears *outside* quoted text, where
    -- it could terminate the statement or swallow the rest of it. Quoted
    -- literals (DEFAULT 'a;b'), quoted identifiers ("x--y") and dollar-quoted
    -- values are data and must pass. The scanner validates the exact text that
    -- is executed and is shared with has_top_level_comma.
    --
    -- The function is SECURITY INVOKER, so this is a best-effort guard: the
    -- caller already holds the privileges the fragment would use. It is not an
    -- authorization boundary.
    IF ddl_utils_lib.has_unquoted_token(
            i_value => i_alter_table_fragment,
            i_tokens => ARRAY [';', '--', '/*', '*/'],
            i_top_level_only => false
       ) THEN
        RAISE EXCEPTION 'ddl_utils_lib.alter_table: the alter table fragment contains a statement separator or comment'
            USING ERRCODE = '22023';
    END IF;

    l_statement := pg_catalog.format(
            'ALTER TABLE %I.%I %s',
            i_schema_name,
            i_table_name,
            pg_catalog.btrim(i_alter_table_fragment)
                   );

    -- Execution and the lock-acquisition retry live in the internal runner so
    -- ALTER TABLE and the foreign-key pre-locking use one implementation. The
    -- retry budget bounds only lock acquisition; a statement that has its locks
    -- may run for as long as it needs (for example a long SET NOT NULL scan).
    PERFORM ddl_utils_lib.execute_with_lock_retry(
            i_statement => l_statement,
            i_context => pg_catalog.format(
                    'ddl_utils_lib.alter_table: could not acquire a lock on %I.%I',
                    i_schema_name,
                    i_table_name),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.alter_table IS
    'Internal runner: builds an ALTER TABLE statement from a caller-provided '
        'fragment and runs it through execute_with_lock_retry, which applies the '
        'bounded lock_timeout and retry budget.';
