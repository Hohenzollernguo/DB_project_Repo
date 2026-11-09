package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** 经由真实 CLI 入口在同一次启动中完成添加、修改与删除。 */
@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CliLineManagementPostgresTest {
    private PostgresTestDatabase database;

    @BeforeAll
    void createDatabase() throws Exception {
        database = PostgresTestDatabase.create();
    }

    @AfterAll
    void closeDatabase() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void createUpdateAndDeleteCompleteDuringOneCliSession() throws Exception {
        database.reset();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String input = "9\n 77号验收线 \n Line 77 \n"
                + "2\n77\n 修改后88号验收线 \n Line 88 \n"
                + "10\n88\nYES\n0\n";
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(input)) {
            new CliApp(AppBootstrap.create(database.connections())).run(scanner, output);
        }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("添加成功：ID=1，中文名=77号验收线，英文名=Line 77"));
        assertTrue(output.contains("修改成功：ID=1，中文名=修改后88号验收线，英文名=Line 88"));
        assertTrue(output.contains("删除成功：ID=1"));
        try (var connection = database.connections().open();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM public.metro_line")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void multipleCandidatesRequireTheirActualIdsForUpdateAndDelete() throws Exception {
        database.reset();
        var services = AppBootstrap.create(database.connections());
        var main = services.lines().create(new LineDraft("13号线", "Line 13"));
        var north = services.lines().create(
                new LineDraft("13号线北延段", "Line 13 Phase 2 North Extension"));
        var unrelated = services.lines().create(new LineDraft("1号线", "Line 1"));
        String input = "2\n13\n" + north.lineId()
                + "\n13号线北延段新名称\nLine 13 North Extension Updated\n"
                + "10\n13\n" + main.lineId() + "\nYES\n0\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(input)) {
            new CliApp(services).run(scanner, output);
        }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("ID=" + main.lineId() + "，中文名=13号线，英文名=Line 13"));
        assertTrue(output.contains("ID=" + north.lineId() + "，中文名=13号线北延段"));
        assertTrue(output.contains("修改成功：ID=" + north.lineId()));
        assertTrue(output.contains("删除成功：ID=" + main.lineId()));
        assertEquals(1, services.lines().search("13").size());
        assertEquals("Line 13 North Extension Updated", services.lines().search("13").get(0).englishName());
        assertEquals(unrelated, services.lines().search("1").get(0));
    }

    @Test
    void numberOneDoesNotSelectLinesTenOrThirteen() throws Exception {
        database.reset();
        var services = AppBootstrap.create(database.connections());
        var ten = services.lines().create(new LineDraft("10号线", "Line 10"));
        var thirteen = services.lines().create(new LineDraft("13号线", "Line 13"));
        var one = services.lines().create(new LineDraft("1号线", "Line 1"));
        String input = "2\n1\n1号线新名称\nLine 1 Updated\n10\n1\nYES\n0\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(input)) {
            new CliApp(services).run(scanner, output);
        }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("修改成功：ID=" + one.lineId()));
        assertTrue(output.contains("删除成功：ID=" + one.lineId()));
        assertTrue(services.lines().search("1").isEmpty());
        assertEquals(ten, services.lines().search("10").get(0));
        assertEquals(thirteen, services.lines().search("13").get(0));
    }
}
