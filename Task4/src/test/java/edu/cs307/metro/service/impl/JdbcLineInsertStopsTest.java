package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.dao.LineDao;
import edu.cs307.metro.dao.LineStationDao;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.StopDraft;
import edu.cs307.metro.support.LineUpdateProbe;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class JdbcLineInsertStopsTest {
    private static StopDraft stop(long id, String status, int year, String coordinate) {
        BigDecimal latitude = coordinate == null ? null : new BigDecimal(coordinate);
        return new StopDraft(id, status, year, null, null, latitude, null);
    }

    static Stream<Arguments> invalidInputs() {
        StopDraft valid = stop(91, "Operation", 2026, null);
        return Stream.of(
                Arguments.of(0L, 1, List.of(valid), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 0, List.of(valid), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, null, ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, Arrays.asList(valid, null), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(stop(-1, "Operation", 2026, null)), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(stop(91, null, 2026, null)), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(stop(91, "Closed", 2026, null)), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(stop(91, "Operation", 0, null)), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(stop(91, "Operation", 32768, null)), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(stop(91, "Operation", 2026, "1000")), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(stop(91, "Operation", 2026, "999.9999995")), ErrorCode.INVALID_INPUT),
                Arguments.of(42L, 1, List.of(valid, valid), ErrorCode.DUPLICATE));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void invalidRequestsFailBeforeOpeningAConnection(long lineId, int position,
            List<StopDraft> stops, ErrorCode expected) {
        var service = new JdbcLineService(new Transactions(
                () -> { throw new AssertionError("Invalid request must not open a connection"); }));
        AppException error = assertThrows(AppException.class,
                () -> service.insertStops(lineId, position, stops));
        assertEquals(expected, error.code());
    }

    @Test
    void locksShiftsInsertsAndValidatesOnOneConnectionBeforeCommitting() {
        LineUpdateProbe jdbc = new LineUpdateProbe();
        Connection[] shared = new Connection[1];
        LineDao lines = new LineDao() {
            @Override
            public boolean lockById(Connection connection, long lineId) {
                shared[0] = connection;
                assertEquals(42, lineId);
                jdbc.events.add("lock");
                return true;
            }
        };
        LineStationDao stops = new LineStationDao() {
            @Override
            public long countStops(Connection connection, long lineId) {
                assertSame(shared[0], connection);
                jdbc.events.add("count");
                return 3;
            }

            @Override
            public boolean isPositionContinuous(Connection connection, long lineId) {
                assertSame(shared[0], connection);
                jdbc.events.add("continuous");
                return true;
            }

            @Override
            public boolean containsStation(Connection connection, long lineId, long stationId) {
                assertSame(shared[0], connection);
                jdbc.events.add("contains:" + stationId);
                return false;
            }

            @Override
            public int shiftForInsertion(Connection connection, long lineId, int position, int count) {
                assertSame(shared[0], connection);
                assertEquals(2, position);
                assertEquals(2, count);
                jdbc.events.add("shift");
                return 2;
            }

            @Override
            public int insertStop(Connection connection, long lineId, int position, StopDraft stop) {
                assertSame(shared[0], connection);
                assertEquals("Operation", stop.status());
                jdbc.events.add("insert:" + stop.stationId() + ":" + position);
                return 1;
            }
        };
        var service = new JdbcLineService(new Transactions(jdbc.connections()), lines, stops);
        service.insertStops(42, 2, List.of(stop(91, " Operation ", 2026, null),
                stop(92, "Operation", 2026, null)));
        assertEquals(List.of("open", "autoCommit:false", "lock", "count", "continuous",
                "contains:91", "contains:92", "shift", "insert:91:2", "insert:92:3",
                "continuous", "commit", "closeConnection"), jdbc.events);
    }

    @Test
    void commitFailureRollsBackTheInsertion() {
        LineUpdateProbe jdbc = new LineUpdateProbe();
        jdbc.commitFailure = new SQLException("commit failure", "08006");
        LineDao lines = new LineDao() {
            @Override
            public boolean lockById(Connection connection, long lineId) { return true; }
        };
        LineStationDao stops = new LineStationDao() {
            @Override
            public long countStops(Connection connection, long lineId) { return 0; }
            @Override
            public boolean isPositionContinuous(Connection connection, long lineId) { return true; }
            @Override
            public boolean containsStation(Connection connection, long lineId, long stationId) { return false; }
            @Override
            public int shiftForInsertion(Connection connection, long lineId, int position, int count) { return 0; }
            @Override
            public int insertStop(Connection connection, long lineId, int position, StopDraft stop) { return 1; }
        };
        var service = new JdbcLineService(new Transactions(jdbc.connections()), lines, stops);
        AppException error = assertThrows(AppException.class,
                () -> service.insertStops(42, 1, List.of(stop(91, "Operation", 2026, null))));
        assertEquals(ErrorCode.DB_ERROR, error.code());
        assertEquals(List.of("open", "autoCommit:false", "commit", "rollback", "closeConnection"), jdbc.events);
    }
}
