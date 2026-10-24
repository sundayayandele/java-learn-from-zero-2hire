package ng.itlearning.practice.sql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Grades every drill: runs your query from queries/NN-*.sql (or solutions/ with -Psolutions) against PostgreSQL
 * loaded with the practice data, and compares the rows, in order, with expected/NN.csv. Numbers are compared by
 * value, so 12.5 and 12.50 match; column names don't matter, column order does.
 */
class SqlDrillsTest {

    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");
    static final String MARKER = "-- Your query below:";
    static Connection connection;

    @BeforeAll
    static void start() throws Exception {
        DB.start();
        connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        try (Statement s = connection.createStatement()) {
            s.execute(read(Path.of("src/test/resources/db/schema.sql")));
            s.execute(read(Path.of("src/test/resources/db/data.sql")));
        }
        connection.setReadOnly(true);
    }

    @AfterAll
    static void stop() throws Exception {
        connection.close();
        DB.stop();
    }

    @TestFactory
    Stream<DynamicTest> drills() throws IOException {
        Path dir = Path.of(System.getProperty("sql.dir", "queries"));
        String only = System.getProperty("drill", "");
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().matches("\\d\\d-.*\\.sql"))
                    .filter(p -> only.isBlank() || p.getFileName().toString().startsWith(only))
                    .sorted()
                    .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> grade(p)))
                    .toList().stream();
        }
    }

    static void grade(Path file) throws Exception {
        String text = read(file);
        int at = text.indexOf(MARKER);
        String query = (at < 0 ? text : text.substring(at + MARKER.length())).strip();
        if (query.isEmpty()) {
            fail("Not attempted yet: write your query under '" + MARKER + "' in " + file);
        }
        String number = file.getFileName().toString().substring(0, 2);
        List<List<String>> expected = csv(read(Path.of("expected", number + ".csv")));
        List<List<String>> actual = new ArrayList<>();
        int columns;
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(query.replaceAll(";\\s*$", ""))) {
            columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int c = 1; c <= columns; c++) {
                    String v = rs.getString(c);
                    row.add(v == null ? "" : v);
                }
                actual.add(row);
            }
        }
        List<String> header = expected.removeFirst();
        assertThat(columns).as("number of columns (expected %s)", header).isEqualTo(header.size());
        assertThat(actual).as("number of rows").hasSameSizeAs(expected);
        for (int r = 0; r < expected.size(); r++) {
            for (int c = 0; c < header.size(); c++) {
                String want = expected.get(r).get(c);
                String got = actual.get(r).get(c);
                assertThat(same(want, got)).as("row %d, column %s: expected <%s> but was <%s>", r + 1, header.get(c), want, got).isTrue();
            }
        }
    }

    static boolean same(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        try {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (NumberFormatException e) {
            return a.replaceAll("\\.0+$", "").equals(b.replaceAll("\\.0+$", ""));
        }
    }

    static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == '"') {
                    if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = !quoted;
                    }
                } else if (ch == ',' && !quoted) {
                    cells.add(cell.toString());
                    cell.setLength(0);
                } else {
                    cell.append(ch);
                }
            }
            cells.add(cell.toString());
            rows.add(cells);
        }
        return rows;
    }

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
