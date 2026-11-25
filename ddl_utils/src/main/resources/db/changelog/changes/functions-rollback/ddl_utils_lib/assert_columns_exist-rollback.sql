DROP FUNCTION IF EXISTS ddl_utils_lib.assert_columns_exist(
    regclass,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_null_text
);
