package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.NeighborPair;
import edu.cs307.metro.service.NeighborService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CliNeighborQueryTest {
    private String run(NeighborService neighbors, String input) {
        var services = new AppServices(null, null, neighbors, null, null, null, null);
        var bytes = new ByteArrayOutputStream();
        try (var scanner = new Scanner(input);
             var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new CliApp(services).run(scanner, output);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void passesEnglishNamesAndCountAndDisplaysBothSidesBeforeReturningToMenu() {
        String output = run((station, line, n) -> {
            assertEquals("Mawan", station);
            assertEquals("Line 5", line);
            assertEquals(2, n);
            return new NeighborPair("Previous", "Next");
        }, "4\n Mawan \n Line 5 \n 2 \n0\n");
        assertTrue(output.contains("向前第 2 站（站序减小）：Previous"));
        assertTrue(output.contains("向后第 2 站（站序增大）：Next"));
        assertFalse(output.contains("前后第 n 站（待接入）"));
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("：Next"));
    }

    @Test
    void preservesAnAbsentFirstSideAndExplainsTheBoundary() {
        String output = run((station, line, n) -> new NeighborPair(null, "Next"),
                "4\nMawan\nLine 5\n2\n0\n");
        assertTrue(output.contains("向前第 2 站（站序减小）：无此站（超出线路范围）"));
        assertTrue(output.contains("向后第 2 站（站序增大）：Next"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1.5", "2147483648"})
    void malformedCountReturnsToMenuWithoutCallingTheService(String count) {
        String output = run((station, line, n) -> {
            throw new AssertionError("Invalid count must not reach the service");
        }, "4\nMawan\nLine 5\n" + count + "\n0\n");
        assertTrue(output.contains("[INVALID_INPUT]"));
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("[INVALID_INPUT]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4\n", "4\nMawan\n", "4\nMawan\nLine 5\n"})
    void incompleteInputExitsWithoutCallingTheService(String input) {
        assertDoesNotThrow(() -> run((station, line, n) -> {
            throw new AssertionError("Incomplete input must not reach the service");
        }, input));
    }

    @Test
    void displaysServiceErrorsAndAllowsTheNextOperation() {
        String output = run((station, line, n) -> {
            throw new AppException(ErrorCode.NOT_FOUND, "目标不存在。");
        }, "4\nMissing\nLine 5\n2\n0\n");
        assertTrue(output.contains("[NOT_FOUND] 目标不存在。"));
        assertFalse(output.contains("向前第 2 站"));
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("[NOT_FOUND]"));
    }
}
