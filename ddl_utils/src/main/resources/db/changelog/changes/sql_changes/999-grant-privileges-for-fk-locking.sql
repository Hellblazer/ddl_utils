-- Caller grants for the foreign-key lock helpers added after the frozen
-- 999-grant-privileges changeset.
--
-- This file is applied by a runOnChange changeset, so it is add-only: removing
-- a GRANT line does not revoke the privilege. Express every removal as an
-- explicit REVOKE (as done for PUBLIC) and keep the statements idempotent.
--
-- Keep this file free of dollar-quoted blocks: Liquibase's default sqlFile
-- splitter is not dollar-quote aware.
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.execute_with_lock_retry(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.execute_with_lock_retry(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.lock_foreign_key_tables(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.lock_foreign_key_tables(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
