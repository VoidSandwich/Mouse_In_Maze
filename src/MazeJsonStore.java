import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сохраняет модель в JSON без внешних библиотек, чтобы проект оставался
 * обычным Java 21-проектом без Maven/Gradle-зависимостей.
 */
public final class MazeJsonStore {
    private final Path directory;

    public MazeJsonStore(Path directory) {
        this.directory = directory;
    }

    public void save(SavedMazeModel model) throws IOException {
        Files.createDirectories(directory);
        String fileName = sanitizeFileName(model.name()) + ".json";
        Path file = directory.resolve(fileName);
        Files.writeString(
                file,
                toJson(model),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    public List<Path> listFiles() throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    public List<SavedMazeModel> loadAll() throws IOException {
        List<SavedMazeModel> models = new ArrayList<>();
        for (Path path : listFiles()) {
            try {
                models.add(load(path));
            } catch (RuntimeException | IOException ex) {
                // Повреждённый файл не должен ломать список всех остальных сохранений.
            }
        }
        return models;
    }

    public SavedMazeModel load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed = new JsonParser(json).parse();
        if (!(parsed instanceof Map<?, ?> rootRaw)) {
            throw new IllegalArgumentException("JSON корень должен быть объектом.");
        }
        Map<String, Object> root = castObject(rootRaw);

        String name = stringValue(root, "name");
        int rows = intValue(root, "rows");
        int cols = intValue(root, "cols");
        Maze maze = new Maze(rows, cols);
        Position start = positionFrom(root.get("start"));
        Position cheese = positionFrom(root.get("cheese"));
        maze.setStart(start);
        maze.setCheese(cheese);

        Set<String> savedWalls = new HashSet<>();
        for (Object wallObject : listValue(root.get("walls"))) {
            Map<String, Object> wall = castObject(wallObject);
            int row = intValue(wall, "row");
            int col = intValue(wall, "col");
            String direction = stringValue(wall, "direction");
            savedWalls.add(row + ":" + col + ":" + direction);
        }

        // По умолчанию Maze имеет стены везде. Убираем только те внутренние стены,
        // которые отсутствуют среди сохранённых.
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Position current = new Position(r, c);
                if (c + 1 < cols && !savedWalls.contains(r + ":" + c + ":RIGHT")) {
                    maze.connectAdjacentCells(current, new Position(r, c + 1));
                }
                if (r + 1 < rows && !savedWalls.contains(r + ":" + c + ":DOWN")) {
                    maze.connectAdjacentCells(current, new Position(r + 1, c));
                }
            }
        }

        List<Position> water = positionsFrom(root.get("water"));
        List<Position> traps = positionsFrom(root.get("traps"));
        maze.restoreElements(new HashSet<>(water), new HashSet<>(traps));
        maze.setCheese(cheese);

        double initialEnergy = doubleValue(root, "initialEnergy");
        double[][][] qTable = readQTable(listValue(root.get("qTable")), rows, cols);
        long completedEpisodes = longValue(root, "completedEpisodes");
        double epsilon = doubleValue(root, "epsilon");
        double bestScore = doubleValue(root, "bestScore");
        List<Position> bestPath = positionsFrom(root.get("bestPath"));
        List<Position> shortestPath = positionsFrom(root.get("shortestSuccessfulPath"));
        double shortestPathScore = doubleValue(root, "shortestPathScore");

        if (!maze.isConnected()) {
            throw new IllegalArgumentException("Сохранённый лабиринт не является связным.");
        }

        return new SavedMazeModel(
                name,
                maze,
                initialEnergy,
                qTable,
                completedEpisodes,
                epsilon,
                bestScore,
                bestPath,
                shortestPath,
                shortestPathScore);
    }

    public void delete(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    public String toJson(SavedMazeModel model) {
        StringBuilder out = new StringBuilder(64_000);
        out.append("{\n");
        appendString(out, "name", model.name(), 1);
        appendNumber(out, "rows", model.maze().getRows(), 1);
        appendNumber(out, "cols", model.maze().getCols(), 1);
        appendPosition(out, "start", model.maze().getStart(), 1);
        appendPosition(out, "cheese", model.maze().getCheese(), 1);
        appendNumber(out, "initialEnergy", model.initialEnergy(), 1);

        indent(out, 1).append("\"walls\": [\n");
        List<String> walls = new ArrayList<>();
        for (int r = 0; r < model.maze().getRows(); r++) {
            for (int c = 0; c < model.maze().getCols(); c++) {
                Position p = new Position(r, c);
                if (c + 1 < model.maze().getCols()
                        && model.maze().getCell(p).hasWall(Direction.RIGHT)) {
                    walls.add(wallJson(r, c, "RIGHT"));
                }
                if (r + 1 < model.maze().getRows()
                        && model.maze().getCell(p).hasWall(Direction.DOWN)) {
                    walls.add(wallJson(r, c, "DOWN"));
                }
            }
        }
        appendJsonLines(out, walls, 2);
        indent(out, 1).append("],\n");

        appendPositionsArray(out, "water", new ArrayList<>(model.maze().getWater()), 1);
        out.append(",\n");
        appendPositionsArray(out, "traps", new ArrayList<>(model.maze().getTraps()), 1);
        out.append(",\n");

        indent(out, 1).append("\"qTable\": [\n");
        List<String> qValues = new ArrayList<>();
        double[][][] q = model.qTable();
        for (int r = 0; r < q.length; r++) {
            for (int c = 0; c < q[r].length; c++) {
                for (int a = 0; a < q[r][c].length; a++) {
                    qValues.add(formatDouble(q[r][c][a]));
                }
            }
        }
        appendJsonLines(out, qValues, 2);
        indent(out, 1).append("],\n");

        appendNumber(out, "completedEpisodes", model.completedEpisodes(), 1);
        appendNumber(out, "epsilon", model.epsilon(), 1);
        appendNumber(out, "bestScore", model.bestScore(), 1);
        appendPositionsArray(out, "bestPath", model.bestPath(), 1);
        out.append(",\n");
        appendPositionsArray(out, "shortestSuccessfulPath", model.shortestSuccessfulPath(), 1);
        out.append(",\n");
        appendNumber(out, "shortestPathScore", model.shortestPathScore(), 1, false);
        out.append("\n}\n");
        return out.toString();
    }

    private static String wallJson(int row, int col, String direction) {
        return "{\"row\":" + row + ",\"col\":" + col + ",\"direction\":\"" + direction + "\"}";
    }

    private static void appendJsonLines(StringBuilder out, List<String> values, int indent) {
        for (int i = 0; i < values.size(); i++) {
            indent(out, indent).append(values.get(i));
            if (i + 1 < values.size()) {
                out.append(',');
            }
            out.append('\n');
        }
    }

    private static void appendPosition(StringBuilder out, String key, Position position, int indent) {
        indent(out, indent)
                .append('"').append(key).append("\":[")
                .append(position.row()).append(',').append(position.col()).append("],\n");
    }

    private static void appendPositionsArray(StringBuilder out, String key, List<Position> positions, int indent) {
        indent(out, indent).append('"').append(key).append("\":[");
        for (int i = 0; i < positions.size(); i++) {
            Position p = positions.get(i);
            out.append('[').append(p.row()).append(',').append(p.col()).append(']');
            if (i + 1 < positions.size()) {
                out.append(',');
            }
        }
        out.append(']');
    }

    private static void appendString(StringBuilder out, String key, String value, int indent) {
        indent(out, indent).append('"').append(key).append("\":\"")
                .append(escape(value)).append("\",\n");
    }

    private static void appendNumber(StringBuilder out, String key, double value, int indent) {
        appendNumber(out, key, value, indent, true);
    }

    private static void appendNumber(StringBuilder out, String key, double value, int indent, boolean comma) {
        indent(out, indent).append('"').append(key).append("\":").append(formatDouble(value));
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static String formatDouble(double value) {
        if (Double.isFinite(value)) {
            return Double.toString(value);
        }
        return "0.0";
    }

    private static StringBuilder indent(StringBuilder out, int count) {
        out.append("    ".repeat(count));
        return out;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String sanitizeFileName(String name) {
        String sanitized = name.trim().replaceAll("[^a-zA-Zа-яА-Я0-9._-]+", "_");
        return sanitized.isBlank() ? "maze" : sanitized;
    }

    private static Map<String, Object> castObject(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Ожидался JSON-объект.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<Object> listValue(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Ожидался JSON-массив.");
        }
        return new ArrayList<>(list);
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("Поле " + key + " отсутствует или имеет неверный тип.");
        }
        return string;
    }

    private static int intValue(Map<String, Object> map, String key) {
        return (int) Math.round(doubleValue(map, key));
    }

    private static long longValue(Map<String, Object> map, String key) {
        return Math.round(doubleValue(map, key));
    }

    private static double doubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Поле " + key + " отсутствует или имеет неверный тип.");
        }
        return number.doubleValue();
    }

    private static Position positionFrom(Object value) {
        List<Object> values = listValue(value);
        if (values.size() != 2) {
            throw new IllegalArgumentException("Позиция должна содержать [row, col].");
        }
        return new Position(numberAsInt(values.get(0)), numberAsInt(values.get(1)));
    }

    private static List<Position> positionsFrom(Object value) {
        List<Position> positions = new ArrayList<>();
        for (Object item : listValue(value)) {
            positions.add(positionFrom(item));
        }
        return positions;
    }

    private static int numberAsInt(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Ожидалось число.");
        }
        return number.intValue();
    }

    private static double[][][] readQTable(List<Object> flat, int rows, int cols) {
        int actions = Direction.values().length;
        if (flat.size() != rows * cols * actions) {
            throw new IllegalArgumentException("Размер сохранённой Q-таблицы не совпадает с лабиринтом.");
        }
        double[][][] q = new double[rows][cols][actions];
        int index = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                for (int a = 0; a < actions; a++) {
                    Object value = flat.get(index++);
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException("Q-таблица содержит нечисловое значение.");
                    }
                    q[r][c][a] = number.doubleValue();
                }
            }
        }
        return q;
    }

    /** Маленький JSON-парсер для формата, который генерирует этот же класс. */
    private static final class JsonParser {
        private final String text;
        private int index;

        private JsonParser(String text) {
            this.text = text;
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw error("Лишние символы после JSON.");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("Неожиданный конец JSON.");
            }
            return switch (text.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char ch = text.charAt(index++);
                if (ch == '"') {
                    return result.toString();
                }
                if (ch != '\\') {
                    result.append(ch);
                    continue;
                }
                if (index >= text.length()) {
                    throw error("Незавершённая escape-последовательность.");
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw error("Некорректный unicode escape.");
                        }
                        String hex = text.substring(index, index + 4);
                        index += 4;
                        result.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw error("Неизвестный escape: \\" + escaped);
                }
            }
            throw error("Незавершённая строка.");
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw error("Ожидалось " + literal);
            }
            index += literal.length();
            return value;
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (peek('.')) {
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String number = text.substring(start, index);
            try {
                return number.contains(".") || number.contains("e") || number.contains("E")
                        ? Double.parseDouble(number)
                        : Long.parseLong(number);
            } catch (NumberFormatException ex) {
                throw error("Некорректное число: " + number);
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw error("Ожидался символ '" + expected + "'.");
            }
            index++;
        }

        private boolean peek(char ch) {
            return index < text.length() && text.charAt(index) == ch;
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " Позиция: " + index);
        }
    }
}
