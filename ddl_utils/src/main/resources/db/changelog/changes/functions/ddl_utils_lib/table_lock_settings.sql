CREATE OR REPLACE FUNCTION ddl_utils_lib.table_lock_settings(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text
)
    RETURNS ddl_utils.lock_settings
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
DECLARE
    l_settings ddl_utils.lock_settings;
BEGIN
    -- Project the columns by name once: a reorder of the composite type or the
    -- resolver cannot silently swap the timeout, sleep and budget values.
    SELECT ls.ddl_lock_timeout,
           ls.sleep_time,
           ls.statement_duration
    INTO STRICT l_settings.ddl_lock_timeout,
        l_settings.sleep_time,
        l_settings.statement_duration
    FROM ddl_utils.get_lock_settings(
                 i_schema_name => i_schema_name,
                 i_table_name => i_table_name
         ) AS ls;

    RETURN l_settings;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
) IS
    'Returns the effective lock settings for a table as one composite, so the '
        'lock-aware wrappers share one named projection.';
