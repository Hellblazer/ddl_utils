CREATE OR REPLACE FUNCTION ddl_utils_lib.operation_lock_key(
    i_context ddl_utils.non_null_text,
    i_keys ddl_utils.non_empty_non_null_text_array
)
    RETURNS bigint
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- Sort the keys so a caller cannot deadlock itself by supplying the same
    -- relations in the opposite order (reciprocal foreign-key calls). The
    -- context and each key are quoted by the caller; '|' is a literal
    -- separator because the context values are fixed routine names.
    RETURN pg_catalog.hashtextextended(
            i_context || '|' || pg_catalog.array_to_string(
                    ARRAY(SELECT t.key
                          FROM pg_catalog.unnest(i_keys) AS t(key)
                          ORDER BY t.key),
                    '|'),
            0);
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.operation_lock_key(
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
    ) IS
    'Builds a deterministic, order-independent advisory-lock key from a '
        'routine context and its quoted identifier keys.';
