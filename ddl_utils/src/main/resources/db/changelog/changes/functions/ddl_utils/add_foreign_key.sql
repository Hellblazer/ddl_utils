CREATE OR REPLACE FUNCTION ddl_utils.add_foreign_key(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
    i_referenced_schema_name ddl_utils.non_null_text,
    i_referenced_table_name ddl_utils.non_null_text,
    i_referenced_column_names ddl_utils.non_empty_non_null_text_array
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_settings            ddl_utils.lock_settings;
    l_referenced_settings ddl_utils.lock_settings;
BEGIN
    -- Lock-aware wrapper: resolve each table's effective settings and pre-lock
    -- both before delegating. ADD FOREIGN KEY takes SHARE ROW EXCLUSIVE on both
    -- tables, so the referenced table's override must be honoured too; locking
    -- in a deterministic order also prevents reciprocal calls from deadlocking.
    l_settings := ddl_utils_lib.table_lock_settings(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name);
    l_referenced_settings := ddl_utils_lib.table_lock_settings(
            i_schema_name => i_referenced_schema_name,
            i_table_name => i_referenced_table_name);

    PERFORM ddl_utils_lib.lock_foreign_key_tables(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_lock_mode => 'share row exclusive',
            i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
            i_sleep_time => l_settings.sleep_time,
            i_statement_duration => l_settings.statement_duration,
            i_referenced_schema_name => i_referenced_schema_name,
            i_referenced_table_name => i_referenced_table_name,
            i_referenced_lock_mode => 'share row exclusive',
            i_referenced_ddl_lock_timeout => l_referenced_settings.ddl_lock_timeout,
            i_referenced_sleep_time => l_referenced_settings.sleep_time,
            i_referenced_statement_duration => l_referenced_settings.statement_duration
            );

    PERFORM ddl_utils_lib.add_foreign_key(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_constraint_name => i_constraint_name,
            i_column_names => i_column_names,
            i_referenced_schema_name => i_referenced_schema_name,
            i_referenced_table_name => i_referenced_table_name,
            i_referenced_column_names => i_referenced_column_names,
            i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
            i_sleep_time => l_settings.sleep_time,
            i_statement_duration => l_settings.statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils.add_foreign_key IS
    'Lock-aware wrapper: adds a foreign key as NOT VALID. Resolves and honours '
        'both the referencing and referenced tables'' lock settings, and '
        'pre-locks both in a deterministic order.';
