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
BEGIN
    -- A named column that does not resolve must fail loudly. Otherwise a
    -- pg_attribute join silently drops it and reports a misleading definition
    -- mismatch instead of the missing column.
    IF (SELECT pg_catalog.count(*)
        FROM pg_catalog.unnest(i_column_names) AS t(name)
                 JOIN pg_catalog.pg_attribute AS a
                      ON a.attrelid = i_relation
                          AND a.attname = t.name
                          AND a.attnum > 0
                          AND NOT a.attisdropped)
        <> pg_catalog.cardinality(i_column_names) THEN
        RAISE EXCEPTION '%: a column of %.% does not exist',
            i_context, i_schema_name, i_table_name
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
