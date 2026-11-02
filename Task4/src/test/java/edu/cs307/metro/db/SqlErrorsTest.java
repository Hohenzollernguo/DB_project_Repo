package edu.cs307.metro.db;

import edu.cs307.metro.common.ErrorCode;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SqlErrorsTest {
    @ParameterizedTest
    @CsvSource({
            "23505, WRITE, DUPLICATE",
            "23503, WRITE, NOT_FOUND",
            "23503, DELETE, IN_USE",
            "23502, WRITE, INVALID_INPUT",
            "23514, WRITE, INVALID_INPUT",
            "22023, READ, INVALID_INPUT",
            "P0002, READ, NOT_FOUND",
            "P0003, READ, AMBIGUOUS_NAME",
            "P0001, READ, DB_ERROR",
            "08001, READ, DB_ERROR"
    })
    void preservesContractMeaning(String state, SqlOperation operation, ErrorCode expected) {
        assertEquals(expected, SqlErrors.translate(new SQLException("detail", state), operation).code());
    }

    @Test
    void nestedBatchExceptionKeepsOriginalCause() {
        SQLException original = new SQLException("batch");
        original.setNextException(new SQLException("unique", "23505"));
        var result = SqlErrors.translate(original, SqlOperation.WRITE);
        assertEquals(ErrorCode.DUPLICATE, result.code());
        assertSame(original, result.getCause());
    }
}
