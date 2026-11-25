CREATE OR REPLACE FUNCTION ddl_utils_lib.column_not_null(
    i_relation regclass,
    i_table_name ddl_utils.non_null_text,
    i_column_name ddl_utils.non_null_text,
    i_context ddl_utils.non_null_text
)
    RETURNS boolean
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
DECLARE
    l_not_null boolean;
BEGIN
    SELECT a.attnotnull
    INTO l_not_null
    FROM pg_catalog.pg_attribute AS a
    WHERE a.attrelid = i_relation
      AND a.attname = i_column_name
      AND a.attnum > 0
      AND NOT a.attisdropped;

    IF NOT FOUND THEN
        RAISE EXCEPTION '%: column %.% does not exist',
            i_context, i_table_name, i_column_name
            USING ERRCODE = '42703';
    END IF;

    RETURN l_not_null;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.column_not_null(
    regclass,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) IS
    'Returns pg_attribute.attnotnull for a column, raising 42703 when the '
        'column does not exist; a renamed or dropped column never reads as NULL.';
