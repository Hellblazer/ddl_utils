CREATE OR REPLACE FUNCTION ddl_utils_lib.lock_foreign_key_tables(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_lock_mode ddl_utils.non_null_text,
    i_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_sleep_time ddl_utils.non_negative_integer,
    i_statement_duration ddl_utils.non_negative_integer,
    i_referenced_schema_name ddl_utils.non_null_text,
    i_referenced_table_name ddl_utils.non_null_text,
    i_referenced_lock_mode ddl_utils.non_null_text,
    i_referenced_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_referenced_sleep_time ddl_utils.non_negative_integer,
    i_referenced_statement_duration ddl_utils.non_negative_integer
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_source_oid      oid;
    l_referenced_oid  oid;
    l_source_name     text;
    l_referenced_name text;
    l_self_mode       text;
BEGIN
    -- PostgreSQL truncates an identifier to NAMEDATALEN - 1 bytes with only a
    -- NOTICE, so an over-long name could resolve to a different relation. Every
    -- name is checked before the OID lookup and the LOCK TABLE statement.
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_schema_name,
            i_context => 'ddl_utils_lib.lock_foreign_key_tables',
            i_argument => 'i_schema_name');
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_table_name,
            i_context => 'ddl_utils_lib.lock_foreign_key_tables',
            i_argument => 'i_table_name');
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_referenced_schema_name,
            i_context => 'ddl_utils_lib.lock_foreign_key_tables',
            i_argument => 'i_referenced_schema_name');
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_referenced_table_name,
            i_context => 'ddl_utils_lib.lock_foreign_key_tables',
            i_argument => 'i_referenced_table_name');

    -- The modes are SQL keyword sequences, not identifiers, so they cannot be
    -- quoted. Accept only the modes this helper uses: ADD FOREIGN KEY takes
    -- SHARE ROW EXCLUSIVE on both tables, and VALIDATE CONSTRAINT takes SHARE
    -- UPDATE EXCLUSIVE on the source and SHARE ROW EXCLUSIVE on the referenced
    -- table.
    PERFORM ddl_utils_lib.assert_allowed_keyword(
            i_value => i_lock_mode,
            i_allowed => ARRAY ['share update exclusive', 'share row exclusive'],
            i_context => 'ddl_utils_lib.lock_foreign_key_tables: i_lock_mode');
    PERFORM ddl_utils_lib.assert_allowed_keyword(
            i_value => i_referenced_lock_mode,
            i_allowed => ARRAY ['share update exclusive', 'share row exclusive'],
            i_context => 'ddl_utils_lib.lock_foreign_key_tables: i_referenced_lock_mode');

    l_source_name := pg_catalog.format('%I.%I', i_schema_name, i_table_name);
    l_referenced_name := pg_catalog.format('%I.%I',
                                           i_referenced_schema_name,
                                           i_referenced_table_name);

    -- Resolve the OIDs only to choose a deterministic lock order. A missing
    -- relation leaves its OID NULL; LOCK TABLE then reports PostgreSQL's own
    -- error (3F000 for a missing schema, 42P01 for a missing table), so the
    -- pre-lock does not change the error a direct ALTER TABLE would raise.
    l_source_oid := pg_catalog.to_regclass(l_source_name)::oid;
    l_referenced_oid := pg_catalog.to_regclass(l_referenced_name)::oid;

    IF l_source_oid IS NOT NULL AND l_source_oid = l_referenced_oid THEN
        -- A self-referencing key locks its one table once. If the two requested
        -- modes differ (add versus validate), take the stronger SHARE ROW
        -- EXCLUSIVE; the source settings are used because both sides are the
        -- same table.
        l_self_mode := CASE
                           WHEN pg_catalog.lower(i_lock_mode) = 'share row exclusive'
                               OR pg_catalog.lower(i_referenced_lock_mode) = 'share row exclusive'
                               THEN 'SHARE ROW EXCLUSIVE'
                           ELSE 'SHARE UPDATE EXCLUSIVE'
            END;

        PERFORM ddl_utils_lib.execute_with_lock_retry(
                i_statement => pg_catalog.format('LOCK TABLE %s IN %s MODE',
                                                 l_source_name,
                                                 l_self_mode),
                i_context => pg_catalog.format(
                        'ddl_utils_lib.lock_foreign_key_tables: could not acquire %s on %s',
                        l_self_mode, l_source_name),
                i_ddl_lock_timeout => i_ddl_lock_timeout,
                i_sleep_time => i_sleep_time,
                i_statement_duration => i_statement_duration
                );
        RETURN;
    END IF;

    -- Lock in OID order so reciprocal operations cannot deadlock on the two
    -- tables. Call this before either table is locked by the caller; the order
    -- only prevents deadlocks when both locks are taken here. If either OID is
    -- unknown (a missing relation), fall back to the quoted names; every
    -- session computes the same order.
    IF (l_source_oid IS NOT NULL AND l_referenced_oid IS NOT NULL
        AND l_source_oid < l_referenced_oid)
        OR ((l_source_oid IS NULL OR l_referenced_oid IS NULL)
            AND l_source_name <= l_referenced_name) THEN
        PERFORM ddl_utils_lib.execute_with_lock_retry(
                i_statement => pg_catalog.format('LOCK TABLE %s IN %s MODE',
                                                 l_source_name,
                                                 pg_catalog.upper(i_lock_mode)),
                i_context => pg_catalog.format(
                        'ddl_utils_lib.lock_foreign_key_tables: could not acquire %s on %s',
                        pg_catalog.upper(i_lock_mode), l_source_name),
                i_ddl_lock_timeout => i_ddl_lock_timeout,
                i_sleep_time => i_sleep_time,
                i_statement_duration => i_statement_duration
                );
        PERFORM ddl_utils_lib.execute_with_lock_retry(
                i_statement => pg_catalog.format('LOCK TABLE %s IN %s MODE',
                                                 l_referenced_name,
                                                 pg_catalog.upper(i_referenced_lock_mode)),
                i_context => pg_catalog.format(
                        'ddl_utils_lib.lock_foreign_key_tables: could not acquire %s on %s',
                        pg_catalog.upper(i_referenced_lock_mode), l_referenced_name),
                i_ddl_lock_timeout => i_referenced_ddl_lock_timeout,
                i_sleep_time => i_referenced_sleep_time,
                i_statement_duration => i_referenced_statement_duration
                );
    ELSE
        PERFORM ddl_utils_lib.execute_with_lock_retry(
                i_statement => pg_catalog.format('LOCK TABLE %s IN %s MODE',
                                                 l_referenced_name,
                                                 pg_catalog.upper(i_referenced_lock_mode)),
                i_context => pg_catalog.format(
                        'ddl_utils_lib.lock_foreign_key_tables: could not acquire %s on %s',
                        pg_catalog.upper(i_referenced_lock_mode), l_referenced_name),
                i_ddl_lock_timeout => i_referenced_ddl_lock_timeout,
                i_sleep_time => i_referenced_sleep_time,
                i_statement_duration => i_referenced_statement_duration
                );
        PERFORM ddl_utils_lib.execute_with_lock_retry(
                i_statement => pg_catalog.format('LOCK TABLE %s IN %s MODE',
                                                 l_source_name,
                                                 pg_catalog.upper(i_lock_mode)),
                i_context => pg_catalog.format(
                        'ddl_utils_lib.lock_foreign_key_tables: could not acquire %s on %s',
                        pg_catalog.upper(i_lock_mode), l_source_name),
                i_ddl_lock_timeout => i_ddl_lock_timeout,
                i_sleep_time => i_sleep_time,
                i_statement_duration => i_statement_duration
                );
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.lock_foreign_key_tables IS
    'Pre-locks the referencing and referenced tables of a foreign key with each '
        'table''s own lock settings, in OID order so reciprocal calls cannot '
        'deadlock. ADD FOREIGN KEY uses SHARE ROW EXCLUSIVE on both; VALIDATE '
        'CONSTRAINT uses SHARE UPDATE EXCLUSIVE on the source and SHARE ROW '
        'EXCLUSIVE on the referenced table. A self-referencing key locks once.';
