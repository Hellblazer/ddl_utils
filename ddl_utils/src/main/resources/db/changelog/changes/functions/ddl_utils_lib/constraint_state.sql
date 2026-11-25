CREATE OR REPLACE FUNCTION ddl_utils_lib.constraint_state(
    i_relation regclass,
    i_constraint_name ddl_utils.non_null_text,
    i_contype text
)
    RETURNS TABLE
            (
                constraint_oid oid,
                convalidated   boolean
            )
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- One statement, so the OID and the validation flag come from the same
    -- snapshot. Reading them separately would let a constraint dropped in the
    -- gap look like "not validated" (a NULL flag) instead of vanishing.
    RETURN QUERY
        SELECT c.oid, c.convalidated
        FROM pg_catalog.pg_constraint AS c
        WHERE c.conname = i_constraint_name
          AND c.conrelid = i_relation
          AND c.contype::text = i_contype;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.constraint_state(
    regclass,
    ddl_utils.non_null_text,
    text
    ) IS
    'Returns the OID and convalidated flag of a named constraint of the given '
        'contype on the relation, or no row when it does not exist.';
