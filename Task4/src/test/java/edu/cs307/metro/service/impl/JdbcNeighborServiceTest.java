package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.Transactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JdbcNeighborServiceTest {
    private final JdbcNeighborService service = new JdbcNeighborService(new Transactions(
            () -> { throw new AssertionError("Invalid input must not open a connection"); }));

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u3000\u2003", "Bad\0Name"})
    void invalidNamesFailBeforeOpeningAConnection(String name) {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> service.get(name, "Line 5", 1)).code());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> service.get("Mawan", name, 1)).code());
    }

    @Test
    void negativeCountFailsBeforeOpeningAConnection() {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> service.get("Mawan", "Line 5", -1)).code());
    }
}
