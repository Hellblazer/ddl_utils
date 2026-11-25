DROP FUNCTION IF EXISTS ddl_utils_lib.execute_with_lock_retry(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
);
