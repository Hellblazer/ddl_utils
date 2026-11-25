CREATE OR REPLACE FUNCTION ddl_utils_lib.assert_columns_exist(
    i_relation regclass,
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
    i_context ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
DECLARE
    l_missing text[];
BEGIN
    -- A named column that does not resolve must fail loudly. Otherwise a
    -- pg_attribute join silently drops it and reports a misleading definition
    -- mismatch instead of the missing column. Collect every unmatched name so a
    -- multi-column call names the offending columns, not just the relation.
    SELECT pg_catalog.array_agg(t.name ORDER BY t.name)
    INTO l_missing
    FROM pg_catalog.unnest(i_column_names) AS t(name)
    WHERE NOT EXISTS (SELECT 1
                      FROM pg_catalog.pg_attribute AS a
                      WHERE a.attrelid = i_relation
                        AND a.attname = t.name
                        AND a.attnum > 0
                        AND NOT a.attisdropped);

    IF l_missing IS NOT NULL THEN
        RAISE EXCEPTION '%: column(s) % of %.% do not exist',
            i_context, pg_catalog.array_to_string(l_missing, ', '),
            i_schema_name, i_table_name
            USING ERRCODE = '42703';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.assert_columns_exist(
    regclass,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_null_text
    ) IS
    'Raises 42703 unless every named column exists on the relation; used by '
        'the foreign-key helpers and procedure before building a definition check.';
