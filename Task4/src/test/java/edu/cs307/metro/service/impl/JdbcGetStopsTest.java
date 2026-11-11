package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.Transactions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JdbcGetStopsTest {
    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void invalidLineIdsFailBeforeOpeningAConnection(long lineId) {
        var lines = new JdbcLineService(new Transactions(
                () -> { throw new AssertionError("Invalid input must not access the database"); }));
        assertEquals(ErrorCode.INVALID_INPUT,
                assertThrows(AppException.class, () -> lines.getStops(lineId)).code());
    }
}
