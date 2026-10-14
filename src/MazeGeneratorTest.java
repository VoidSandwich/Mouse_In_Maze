import java.util.Random;

public final class MazeGeneratorTest {
    public static void main(String[] args) throws Exception {
        testPerfectDfsMaze(12, 14, 12345);
        testSmallMaze(2, 2, 1);
        testRectangularMaze(5, 7, 2);
        testAnotherMaze(8, 9, 3);
        System.out.println("MazeGeneratorTest: OK");
    }

    private static void testPerfectDfsMaze(int rows, int cols, long seed) throws Exception {
        Maze maze = MazeGenerator.generate(rows, cols, new Random(seed), 0, null);
        int expectedInternalWalls = (rows - 1) * (cols - 1);
        assert maze.countInternalWalls() == expectedInternalWalls
                : "Неверное число стен: ожидалось " + expectedInternalWalls;
        assert maze.isConnected() : "Лабиринт не связный";
        assert maze.getCheese() != null : "Сыр не установлен";
        assert !maze.bfsPath(maze.getStart(), maze.getCheese()).isEmpty() : "До сыра нет пути";
        assertExitOnBoundary(maze);
    }

    private static void testSmallMaze(int rows, int cols, long seed) throws Exception {
        Maze maze = MazeGenerator.generate(rows, cols, new Random(seed), 0, null);
        assert maze.countInternalWalls() == (rows - 1) * (cols - 1);
        assert maze.isConnected();
        assertExitOnBoundary(maze);
    }

    private static void testRectangularMaze(int rows, int cols, long seed) throws Exception {
        testPerfectDfsMaze(rows, cols, seed);
    }

    private static void testAnotherMaze(int rows, int cols, long seed) throws Exception {
        testPerfectDfsMaze(rows, cols, seed);
    }

    private static void assertExitOnBoundary(Maze maze) {
        Position exit = maze.getCheese();
        assert exit != null : "Выход (сыр) не установлен";
        assert exit.row() == 0
                || exit.row() == maze.getRows() - 1
                || exit.col() == 0
                || exit.col() == maze.getCols() - 1
                : "Выход должен находиться на границе лабиринта";

        int openBoundaryWalls = 0;
        for (int c = 0; c < maze.getCols(); c++) {
            if (!maze.getCell(0, c).hasWall(Direction.UP)) {
                openBoundaryWalls++;
                assert exit.equals(new Position(0, c)) : "Открыт неправильный участок верхней границы";
            }
            if (!maze.getCell(maze.getRows() - 1, c).hasWall(Direction.DOWN)) {
                openBoundaryWalls++;
                assert exit.equals(new Position(maze.getRows() - 1, c)) : "Открыт неправильный участок нижней границы";
            }
        }
        for (int r = 0; r < maze.getRows(); r++) {
            if (!maze.getCell(r, 0).hasWall(Direction.LEFT)) {
                openBoundaryWalls++;
                assert exit.equals(new Position(r, 0)) : "Открыт неправильный участок левой границы";
            }
            if (!maze.getCell(r, maze.getCols() - 1).hasWall(Direction.RIGHT)) {
                openBoundaryWalls++;
                assert exit.equals(new Position(r, maze.getCols() - 1)) : "Открыт неправильный участок правой границы";
            }
        }
        assert openBoundaryWalls == 1 : "У лабиринта должен быть ровно один выход";
    }
}
