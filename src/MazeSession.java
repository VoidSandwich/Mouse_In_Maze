/** Параметры одного запуска лабиринта. */
public record MazeSession(
        int rows,
        int cols,
        double initialEnergy,
        int waterCount,
        int trapCount,
        int simulationStepDelayMs) {
}
