package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineNumber;
import edu.cs307.metro.support.LineCreateProbe;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberedLineCreateTest {
    @Test
    void maximumIsNumericAndIncludesExtensionsButNotInternalIdsOrUnrelatedDigits() {
        LineCreateProbe probe = new LineCreateProbe();
        probe.generatedId = 900;
        probe.existingEnglishNames = List.of("Line 9", "Line 13 Phase 2 North Extension",
                "Line 13", "Airport 999", "Line 01", "Line 2");
        var created = AppBootstrap.create(probe.connections()).lines()
                .createNumbered(new LineDraft(" 新线路 ", " Demo Line "));
        assertEquals("14", created.number());
        assertEquals(900, created.lineId());
        assertEquals("新线路", created.chineseName());
        assertEquals("Line 14 Demo Line", created.englishName());
        assertTrue(probe.events.indexOf("lockNumbers") < probe.events.indexOf("readNumbers"));
        assertTrue(probe.events.indexOf("readNumbers") < probe.events.indexOf("insert"));
        assertTrue(probe.events.indexOf("insert") < probe.events.indexOf("commit"));
    }

    @Test
    void emptyDatabaseStartsAtOneAndExistingInputPrefixIsReplaced() {
        LineCreateProbe probe = new LineCreateProbe();
        var created = AppBootstrap.create(probe.connections()).lines()
                .createNumbered(new LineDraft("新线路", "Line 99 Demo"));
        assertEquals("1", created.number());
        assertEquals("Line 1 Demo", created.englishName());
        assertEquals("Line 2", LineNumber.apply("2", "Line 99"));
    }

    @Test
    void numbersBeyondIntegerAndLongRangeDoNotOverflow() {
        LineCreateProbe probe = new LineCreateProbe();
        probe.existingEnglishNames = List.of("Line 9223372036854775808");
        var created = AppBootstrap.create(probe.connections()).lines()
                .createNumbered(new LineDraft("新线路", "Demo"));
        assertEquals("9223372036854775809", created.number());
    }

    @Test
    void failedInsertRollsBackNumberAllocationAndNeverReportsSuccess() {
        LineCreateProbe probe = new LineCreateProbe();
        probe.executionFailure = new SQLException("private detail", "23505");
        var lines = AppBootstrap.create(probe.connections()).lines();
        var failure = assertThrows(AppException.class,
                () -> lines.createNumbered(new LineDraft("重名", "Demo")));
        assertEquals(ErrorCode.DUPLICATE, failure.code());
        assertTrue(probe.events.contains("rollback"));
        assertFalse(probe.events.contains("commit"));
    }

    @Test
    void invalidInputDoesNotAllocateNumbersOrOpenConnection() {
        LineCreateProbe probe = new LineCreateProbe();
        var lines = AppBootstrap.create(probe.connections()).lines();
        assertThrows(AppException.class, () -> lines.createNumbered(new LineDraft(" ", "Demo")));
        assertThrows(AppException.class, () -> lines.createNumbered(null));
        assertTrue(probe.events.isEmpty());
    }
}
