package edu.cs307.metro.dao;

import edu.cs307.metro.model.StopDraft;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.OptionalInt;

public class LineStationDao {

    public long countStops(Connection connection, long lineId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM public.line_station WHERE line_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    public boolean containsStation(Connection connection, long lineId, long stationId)
            throws SQLException {
        String sql = "SELECT 1 FROM public.line_station WHERE line_id = ? AND station_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);
            statement.setLong(2, stationId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public int shiftForInsertion(Connection connection, long lineId, int position, int count)
            throws SQLException {
        String sql = """
                UPDATE public.line_station
                SET position = position + ?
                WHERE line_id = ? AND position >= ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, count);
            statement.setLong(2, lineId);
            statement.setInt(3, position);
            return statement.executeUpdate();
        }
    }

    public int insertStop(Connection connection, long lineId, int position, StopDraft stop)
            throws SQLException {
        String sql = """
                INSERT INTO public.line_station (
                    line_id, station_id, position, status, opening_year,
                    start_time, end_time, source_latitude, source_longitude)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);
            statement.setLong(2, stop.stationId());
            statement.setInt(3, position);
            statement.setString(4, stop.status());
            statement.setInt(5, stop.openingYear());
            statement.setObject(6, stop.startTime(), Types.TIME);
            statement.setObject(7, stop.endTime(), Types.TIME);
            statement.setBigDecimal(8, stop.sourceLatitude());
            statement.setBigDecimal(9, stop.sourceLongitude());
            return statement.executeUpdate();
        }
    }

    public OptionalInt deleteReturningPosition(
            Connection connection,
            long lineId,
            long stationId
    ) throws SQLException {
        String sql = """
                DELETE FROM public.line_station
                WHERE line_id = ? AND station_id = ?
                RETURNING position
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);
            statement.setLong(2, stationId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return OptionalInt.of(rows.getInt("position"));
                }
                return OptionalInt.empty();
            }
        }
    }

    public int shiftAfterRemoval(
            Connection connection,
            long lineId,
            int removedPosition
    ) throws SQLException{
        String sql= """
                UPDATE public.line_station
                SET position = position - 1
                WHERE line_id = ?
                  AND position > ?;
                """;
        try (PreparedStatement statement= connection.prepareStatement(sql)){
            statement.setLong(1,lineId);
            statement.setInt(2,removedPosition);
            return statement.executeUpdate();
        }
    }

    public boolean isPositionContinuous(
            Connection connection,
            long lineId
    ) throws SQLException {
        String sql = """
            SELECT COUNT(*) = 0
                   OR (
                       MIN(position) = 1
                       AND MAX(position) = COUNT(*)
                       AND COUNT(DISTINCT position) = COUNT(*)
                   ) AS continuous
            FROM public.line_station
            WHERE line_id = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);

            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean("continuous");
            }
        }
    }
}
