import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Генерирует совершенный связный лабиринт алгоритмом DFS.
 * В совершенном лабиринте между любыми двумя клетками существует ровно один путь.
 * Поэтому число внутренних стен определяется размером поля и не настраивается вручную.
 * Самая дальняя от старта граничная клетка становится выходом с сыром.
 */
public final class MazeGenerator {
    private MazeGenerator() {
    }

    public static Maze generate(
            int rows,
            int cols,
            Random random,
            int delayMs,
            Consumer<Maze> snapshotConsumer) throws InterruptedException {

        validateParameters(rows, cols);
        Maze maze = new Maze(rows, cols);
        maze.setStart(new Position(0, 0));

        dfsGenerate(maze, random, delayMs, snapshotConsumer);

        int expectedInternalWalls = (rows - 1) * (cols - 1);
        int actualInternalWalls = maze.countInternalWalls();
        if (actualInternalWalls != expectedInternalWalls) {
            throw new IllegalStateException(
                    "Ошибка DFS: получено " + actualInternalWalls + " внутренних стен вместо "
                            + expectedInternalWalls);
        }
        if (!maze.isConnected()) {
            throw new IllegalStateException("DFS создал несвязный лабиринт.");
        }

        List<Position> pathToExit = farthestBoundaryCellPath(maze, maze.getStart());
        if (!pathToExit.isEmpty()) {
            maze.setCheese(pathToExit.getLast());
        }

        emit(maze, snapshotConsumer, delayMs);
        return maze;
    }

    private static void dfsGenerate(
            Maze maze,
            Random random,
            int delayMs,
            Consumer<Maze> snapshotConsumer) throws InterruptedException {

        Position start = maze.getStart();
        Deque<Position> stack = new ArrayDeque<>();
        stack.push(start);
        maze.getCell(start).setVisited(true);
        emit(maze, snapshotConsumer, delayMs);

        while (!stack.isEmpty()) {
            Position current = stack.peek();
            List<Direction> directions = new ArrayList<>(List.of(Direction.values()));
            Collections.shuffle(directions, random);

            Position next = null;
            for (Direction direction : directions) {
                Position candidate = new Position(
                        current.row() + direction.rowDelta(),
                        current.col() + direction.colDelta());
                if (maze.isInside(candidate) && !maze.getCell(candidate).isVisited()) {
                    next = candidate;
                    break;
                }
            }

            if (next == null) {
                stack.pop();
                emit(maze, snapshotConsumer, delayMs);
                continue;
            }

            maze.connectAdjacentCells(current, next);
            maze.getCell(next).setVisited(true);
            stack.push(next);
            emit(maze, snapshotConsumer, delayMs);
        }

        clearVisitedFlags(maze);
    }

    /** BFS-проверка связности, полезная для тестов и будущих изменений лабиринта. */
    public static boolean bfsIsConnected(Maze maze) {
        return maze.isConnected();
    }

    private static List<Position> farthestBoundaryCellPath(Maze maze, Position from) {
        int rows = maze.getRows();
        int cols = maze.getCols();
        int[][] distance = new int[rows][cols];
        Position[][] parent = new Position[rows][cols];

        for (int r = 0; r < rows; r++) {
            java.util.Arrays.fill(distance[r], -1);
        }

        Deque<Position> queue = new ArrayDeque<>();
        queue.add(from);
        distance[from.row()][from.col()] = 0;

        Position farthestBoundary = null;
        int farthestDistance = -1;

        while (!queue.isEmpty()) {
            Position current = queue.removeFirst();
            if (isBoundary(maze, current)
                    && distance[current.row()][current.col()] > farthestDistance) {
                farthestBoundary = current;
                farthestDistance = distance[current.row()][current.col()];
            }

            for (Direction direction : Direction.values()) {
                if (!maze.canMove(current, direction)) {
                    continue;
                }
                Position next = maze.move(current, direction);
                if (distance[next.row()][next.col()] != -1) {
                    continue;
                }
                distance[next.row()][next.col()] = distance[current.row()][current.col()] + 1;
                parent[next.row()][next.col()] = current;
                queue.addLast(next);
            }
        }

        if (farthestBoundary == null) {
            return List.of(from);
        }

        List<Position> path = new ArrayList<>();
        Position current = farthestBoundary;
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

    private static boolean isBoundary(Maze maze, Position position) {
        return position.row() == 0
                || position.row() == maze.getRows() - 1
                || position.col() == 0
                || position.col() == maze.getCols() - 1;
    }

    private static void clearVisitedFlags(Maze maze) {
        for (int r = 0; r < maze.getRows(); r++) {
            for (int c = 0; c < maze.getCols(); c++) {
                maze.getCell(r, c).setVisited(false);
            }
        }
    }

    private static void emit(Maze maze, Consumer<Maze> snapshotConsumer, int delayMs)
            throws InterruptedException {
        if (snapshotConsumer != null) {
            snapshotConsumer.accept(maze.copy());
        }
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }

    private static void validateParameters(int rows, int cols) {
        if (rows < MazeConfig.MIN_SIZE || cols < MazeConfig.MIN_SIZE) {
            throw new IllegalArgumentException(
                    "Размер должен быть не меньше " + MazeConfig.MIN_SIZE + "x" + MazeConfig.MIN_SIZE + ".");
        }
        if (rows > MazeConfig.MAX_SIZE || cols > MazeConfig.MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Размер ограничен значением " + MazeConfig.MAX_SIZE + "x" + MazeConfig.MAX_SIZE + ".");
        }
    }
}
