import java.util.List;

/** Данные лабиринта и результаты обучения, сохраняемые в JSON. */
public record SavedMazeModel(
        String name,
        Maze maze,
        double initialEnergy,
        double[][][] qTable,
        long completedEpisodes,
        double epsilon,
        double bestScore,
        List<Position> bestPath,
        List<Position> shortestSuccessfulPath,
        double shortestPathScore) {
}
