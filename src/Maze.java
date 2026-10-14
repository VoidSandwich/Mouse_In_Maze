
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class Maze {
    private final int rows;
    private final int cols;
    private final MazeCell[][] cells;
    private final Set<Position> water = new LinkedHashSet<>();
    private final Set<Position> traps = new LinkedHashSet<>();
    private Position start;
    private Position cheese;

    public Maze(int rows, int cols) {
        if (rows < 2 || cols < 2) {
            throw new IllegalArgumentException("Размеры лабиринта должны быть не меньше 2x2.");
        }
        this.rows = rows;
        this.cols = cols;
        this.cells = new MazeCell[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c] = new MazeCell();
            }
        }
        this.start = new Position(0, 0);
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public MazeCell getCell(int row, int col) {
        return cells[row][col];
    }

    public MazeCell getCell(Position position) {
        return getCell(position.row(), position.col());
    }

    public Position getStart() {
        return start;
    }

    public void setStart(Position start) {
        requireInside(start);
        this.start = start;
    }

    public Position getCheese() {
        return cheese;
    }

    /**
     * Устанавливает сыр. Сыр одновременно является выходом из лабиринта.
     * Выход находится на границе поля, поэтому открывается один внешний
     * участок стены в клетке сыра.
     */
    public void setCheese(Position cheese) {
        requireInside(cheese);
        if (!isBoundary(cheese)) {
            throw new IllegalArgumentException("Клетка выхода (сыр) должна находиться на границе лабиринта.");
        }
        this.cheese = cheese;
        water.remove(cheese);
        traps.remove(cheese);
        openBoundaryExit(cheese);
        getCell(cheese).setType(CellType.CHEESE);
    }

    private void openBoundaryExit(Position exit) {
        if (exit.row() == 0) {
            getCell(exit).removeWall(Direction.UP);
        } else if (exit.row() == rows - 1) {
            getCell(exit).removeWall(Direction.DOWN);
        } else if (exit.col() == 0) {
            getCell(exit).removeWall(Direction.LEFT);
        } else if (exit.col() == cols - 1) {
            getCell(exit).removeWall(Direction.RIGHT);
        }
    }

    private boolean isBoundary(Position position) {
        return position.row() == 0
                || position.row() == rows - 1
                || position.col() == 0
                || position.col() == cols - 1;
    }

    public Set<Position> getWater() {
        return Collections.unmodifiableSet(water);
    }

    public Set<Position> getTraps() {
        return Collections.unmodifiableSet(traps);
    }

    public void clearElements() {
        water.clear();
        traps.clear();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].setType(CellType.EMPTY);
            }
        }
        if (cheese != null) {
            cells[cheese.row()][cheese.col()].setType(CellType.CHEESE);
        }
    }

    public void placeRandomElements(int waterCount, int trapCount, Random random) {
        if (waterCount < 0 || trapCount < 0) {
            throw new IllegalArgumentException("Количество воды и ловушек не может быть отрицательным.");
        }
        if (cheese == null) {
            throw new IllegalStateException("Сначала должен быть выбран сыр.");
        }

        clearElements();
        List<Position> candidates = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Position p = new Position(r, c);
                if (p.equals(start) || p.equals(cheese)) {
                    continue;
                }
                candidates.add(p);
            }
        }

        int required = waterCount + trapCount;
        if (required > candidates.size()) {
            throw new IllegalArgumentException(
                    "Слишком много объектов: свободных клеток для воды и ловушек недостаточно.");
        }

        Collections.shuffle(candidates, random);
        int index = 0;
        for (int i = 0; i < waterCount; i++) {
            Position p = candidates.get(index++);
            water.add(p);
            getCell(p).setType(CellType.WATER);
        }
        for (int i = 0; i < trapCount; i++) {
            Position p = candidates.get(index++);
            traps.add(p);
            getCell(p).setType(CellType.TRAP);
        }
        getCell(cheese).setType(CellType.CHEESE);
    }

    /** Восстанавливает расходуемые элементы при загрузке сохранённой модели. */
    public void restoreElements(Set<Position> waterPositions, Set<Position> trapPositions) {
        Objects.requireNonNull(waterPositions, "waterPositions");
        Objects.requireNonNull(trapPositions, "trapPositions");
        clearElements();

        for (Position p : waterPositions) {
            requireInside(p);
            if (p.equals(start) || p.equals(cheese) || trapPositions.contains(p)) {
                throw new IllegalArgumentException("Некорректная позиция воды: " + p);
            }
            water.add(p);
            getCell(p).setType(CellType.WATER);
        }
        for (Position p : trapPositions) {
            requireInside(p);
            if (p.equals(start) || p.equals(cheese) || waterPositions.contains(p)) {
                throw new IllegalArgumentException("Некорректная позиция ловушки: " + p);
            }
            traps.add(p);
            getCell(p).setType(CellType.TRAP);
        }
        if (cheese != null) {
            getCell(cheese).setType(CellType.CHEESE);
        }
    }

    public void connectAdjacentCells(Position a, Position b) {
        requireInside(a);
        requireInside(b);
        Direction direction = directionBetween(a, b);
        getCell(a).removeWall(direction);
        getCell(b).removeWall(direction.opposite());
    }

    public void disconnectAdjacentCells(Position a, Position b) {
        requireInside(a);
        requireInside(b);
        Direction direction = directionBetween(a, b);
        getCell(a).addWall(direction);
        getCell(b).addWall(direction.opposite());
    }

    public boolean canMove(Position from, Direction direction) {
        requireInside(from);
        if (getCell(from).hasWall(direction)) {
            return false;
        }
        Position to = new Position(from.row() + direction.rowDelta(), from.col() + direction.colDelta());
        return isInside(to);
    }

    public Position move(Position from, Direction direction) {
        if (!canMove(from, direction)) {
            return from;
        }
        return new Position(from.row() + direction.rowDelta(), from.col() + direction.colDelta());
    }

    public int countInternalWalls() {
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols && getCell(r, c).hasWall(Direction.RIGHT)) {
                    count++;
                }
                if (r + 1 < rows && getCell(r, c).hasWall(Direction.DOWN)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int countInternalOpenings() {
        int totalInternalConnections = totalPossibleInternalConnections();
        return totalInternalConnections - countInternalWalls();
    }

    public int totalPossibleInternalConnections() {
        return rows * (cols - 1) + (rows - 1) * cols;
    }

    public int maxInternalWallsInConnectedMaze() {
        return (rows - 1) * (cols - 1);
    }

    public boolean isConnected() {
        boolean[][] visited = new boolean[rows][cols];
        Deque<Position> queue = new ArrayDeque<>();
        queue.add(start);
        visited[start.row()][start.col()] = true;
        int visitedCount = 0;

        while (!queue.isEmpty()) {
            Position current = queue.removeFirst();
            visitedCount++;
            for (Direction direction : Direction.values()) {
                if (!canMove(current, direction)) {
                    continue;
                }
                Position next = move(current, direction);
                if (!visited[next.row()][next.col()]) {
                    visited[next.row()][next.col()] = true;
                    queue.addLast(next);
                }
            }
        }
        return visitedCount == rows * cols;
    }

    public List<Position> bfsPath(Position from, Position to) {
        requireInside(from);
        requireInside(to);
        if (from.equals(to)) {
            return List.of(from);
        }

        Position[][] parent = new Position[rows][cols];
        boolean[][] visited = new boolean[rows][cols];
        Deque<Position> queue = new ArrayDeque<>();
        queue.add(from);
        visited[from.row()][from.col()] = true;

        while (!queue.isEmpty()) {
            Position current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                if (!canMove(current, direction)) {
                    continue;
                }
                Position next = move(current, direction);
                if (!visited[next.row()][next.col()]) {
                    visited[next.row()][next.col()] = true;
                    parent[next.row()][next.col()] = current;
                    if (next.equals(to)) {
                        return reconstructPath(parent, from, to);
                    }
                    queue.addLast(next);
                }
            }
        }
        return List.of();
    }

    public Maze copy() {
        Maze copy = new Maze(rows, cols);
        copy.start = start;
        copy.cheese = cheese;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                copy.cells[r][c] = cells[r][c].copy();
            }
        }
        copy.water.addAll(water);
        copy.traps.addAll(traps);
        return copy;
    }

    private static List<Position> reconstructPath(Position[][] parent, Position from, Position to) {
        List<Position> path = new ArrayList<>();
        Position current = to;
        while (current != null) {
            path.add(current);
            if (current.equals(from)) {
                Collections.reverse(path);
                return path;
            }
            current = parent[current.row()][current.col()];
        }
        return List.of();
    }

    private static Direction directionBetween(Position a, Position b) {
        int dr = b.row() - a.row();
        int dc = b.col() - a.col();
        for (Direction direction : Direction.values()) {
            if (direction.rowDelta() == dr && direction.colDelta() == dc) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Позиции не являются соседними: " + a + " -> " + b);
    }

    public boolean isInside(Position position) {
        return position.row() >= 0 && position.row() < rows
                && position.col() >= 0 && position.col() < cols;
    }

    private void requireInside(Position position) {
        Objects.requireNonNull(position, "position");
        if (!isInside(position)) {
            throw new IllegalArgumentException("Позиция вне лабиринта: " + position);
        }
    }
}
