CREATE OR REPLACE FUNCTION ddl_utils_lib.constraint_of_other_kind_exists(
    i_relation regclass,
    i_constraint_name ddl_utils.non_null_text,
    i_contype text
)
    RETURNS boolean
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- A same-named constraint of another kind makes the structured ADD fail
    -- with a generic duplicate-object error; the caller names the conflict.
    RETURN EXISTS (SELECT 1
                   FROM pg_catalog.pg_constraint AS c
                   WHERE c.conname = i_constraint_name
                     AND c.conrelid = i_relation
                     AND c.contype::text <> i_contype);
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.constraint_of_other_kind_exists(
    regclass,
    ddl_utils.non_null_text,
    text
    ) IS
    'Returns whether a same-named constraint of a different contype exists on '
        'the relation, so the caller can name the duplicate-object conflict.';
