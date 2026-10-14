import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Классический табличный Q-Learning с одношаговым TD(0)-обновлением.
 * Состояние — координаты мыши, действие — одно из четырёх направлений.
 * Q-таблица обновляется после каждого действия по стандартной формуле TD(0).
 * Epsilon автоматически уменьшается; после достижения нуля выбор становится жадным.
 */
public final class QLearningAgent {
    private final Maze maze;
    private final Random random;
    private final double[][][] qTable;
    private final double initialScore;

    private double epsilon;
    private boolean cheeseDiscovered;
    private long completedEpisodes;
    private double currentScore;
    private int episodeStep;
    private Position position;

    private final List<Position> currentPath = new ArrayList<>();
    private final Set<Position> consumedWater = new HashSet<>();
    private final Set<Position> consumedTraps = new HashSet<>();

    private double bestScore = Double.NEGATIVE_INFINITY;
    private List<Position> bestPath = List.of();
    private List<Position> shortestSuccessfulPath = List.of();
    private double shortestPathScore = Double.NEGATIVE_INFINITY;

    public QLearningAgent(Maze maze, double initialScore, Random random) {
        this(
                maze,
                initialScore,
                random,
                MazeConfig.EPSILON_INITIAL,
                0,
                createInitialQTable(maze),
                Double.NEGATIVE_INFINITY,
                List.of(),
                List.of(),
                Double.NEGATIVE_INFINITY);
    }

    public QLearningAgent(
            Maze maze,
            double initialScore,
            Random random,
            double epsilon,
            long completedEpisodes,
            double[][][] qValues,
            double bestScore,
            List<Position> bestPath,
            List<Position> shortestSuccessfulPath,
            double shortestPathScore) {
        if (initialScore <= 0) {
            throw new IllegalArgumentException("Начальные очки должны быть больше нуля.");
        }
        if (!Double.isFinite(epsilon) || epsilon < MazeConfig.EPSILON_MIN || epsilon > 1.0) {
            throw new IllegalArgumentException(
                    "Epsilon должен быть в диапазоне [" + MazeConfig.EPSILON_MIN + "; 1.0].");
        }
        if (qValues.length != maze.getRows()) {
            throw new IllegalArgumentException("Размер Q-таблицы не совпадает с лабиринтом.");
        }

        this.maze = maze;
        this.random = random;
        this.initialScore = initialScore;
        this.epsilon = epsilon;
        this.completedEpisodes = completedEpisodes;
        this.qTable = copyQValues(qValues, maze.getRows(), maze.getCols());
        this.bestScore = bestScore;
        this.bestPath = List.copyOf(bestPath);
        this.shortestSuccessfulPath = List.copyOf(shortestSuccessfulPath);
        this.shortestPathScore = shortestPathScore;
        this.cheeseDiscovered = !bestPath.isEmpty();
        resetEpisode(initialScore);
    }

    public void resetEpisode(double initialScore) {
        currentScore = initialScore;
        episodeStep = 0;
        position = maze.getStart();
        currentPath.clear();
        currentPath.add(position);
        consumedWater.clear();
        consumedTraps.clear();
    }

    /** Делает один шаг epsilon-greedy; затухание epsilon выполняется автоматически. */
    public AgentStepResult step() {
        if (episodeFinished()) {
            throw new IllegalStateException("Текущий эпизод уже завершён. Сначала вызовите resetEpisode().");
        }

        Position oldPosition = position;
        Direction action = chooseAction(oldPosition);
        Position nextPosition = maze.move(oldPosition, action);
        boolean wallHit = oldPosition.equals(nextPosition) && !maze.canMove(oldPosition, action);

        double reward;
        boolean waterConsumedNow = false;
        boolean trapConsumedNow = false;
        boolean success = false;
        String finishReason = null;

        if (wallHit) {
            // Столкновение со стеной — обычная самопетля со штрафом, не терминальный переход.
            reward = MazeConfig.WALL_PENALTY;
        } else {
            reward = MazeConfig.STEP_PENALTY;
            CellType targetType = maze.getCell(nextPosition).getType();

            if (targetType == CellType.WATER && !consumedWater.contains(nextPosition)) {
                reward += MazeConfig.WATER_REWARD;
                consumedWater.add(nextPosition);
                waterConsumedNow = true;
            } else if (targetType == CellType.TRAP && !consumedTraps.contains(nextPosition)) {
                reward += MazeConfig.TRAP_PENALTY;
                consumedTraps.add(nextPosition);
                trapConsumedNow = true;
            } else if (targetType == CellType.CHEESE) {
                reward += MazeConfig.CHEESE_REWARD;
                success = true;
                finishReason = "Сыр достигнут";
            }

            position = nextPosition;
            if (!position.equals(oldPosition)) {
                currentPath.add(position);
            }
        }

        currentScore += reward;
        episodeStep++;

        boolean energyDepleted = currentScore <= 0.0;
        if (energyDepleted && !success) {
            finishReason = "Очки достигли нуля";
        }

        boolean stepLimitReached = episodeStep >= maxSteps();
        if (stepLimitReached && !success && !energyDepleted) {
            finishReason = "Превышен лимит шагов";
        }

        boolean terminal = success || energyDepleted || stepLimitReached;
        Transition transition = new Transition(oldPosition, action, reward, nextPosition, terminal);

        // Всегда применяется одно и то же классическое TD(0)-обновление,
        // даже когда epsilon уже стал нулём.
        updateQ(transition);

        if (terminal) {
            completedEpisodes++;
            boolean firstSuccess = success && !cheeseDiscovered;
            if (success) {
                cheeseDiscovered = true;
                updateBestResults();
            }
            updateEpsilon(firstSuccess);
        }

        return new AgentStepResult(
                position,
                action,
                reward,
                currentScore,
                episodeStep,
                completedEpisodes,
                wallHit,
                waterConsumedNow,
                trapConsumedNow,
                terminal,
                success,
                finishReason,
                epsilon,
                bestScore);
    }

    /** Стандартное одношаговое обновление Q-Learning. */
    private void updateQ(Transition transition) {
        int row = transition.state().row();
        int col = transition.state().col();
        int actionIndex = transition.action().ordinal();

        double oldValue = qTable[row][col][actionIndex];
        double futureValue = transition.terminal() ? 0.0 : maxQ(transition.nextState());
        double target = transition.reward() + MazeConfig.GAMMA * futureValue;
        qTable[row][col][actionIndex] = oldValue + MazeConfig.ALPHA * (target - oldValue);
    }

    /**
     * Epsilon-greedy по всем четырём направлениям, включая стены.
     * Пока epsilon > 0 случайность используется для исследования; при epsilon = 0
     * argmax Q выбирается детерминированно даже при равных Q-значениях.
     */
    private Direction chooseAction(Position state) {
        Direction[] actions = Direction.values();
        if (epsilon > 0.0 && random.nextDouble() < epsilon) {
            return actions[random.nextInt(actions.length)];
        }

        double bestValue = Double.NEGATIVE_INFINITY;
        List<Direction> bestActions = new ArrayList<>();
        for (Direction action : actions) {
            double value = qTable[state.row()][state.col()][action.ordinal()];
            if (value > bestValue + 1e-12) {
                bestValue = value;
                bestActions.clear();
                bestActions.add(action);
            } else if (Math.abs(value - bestValue) <= 1e-12) {
                bestActions.add(action);
            }
        }

        // При epsilon=0 не выбираем случайно между равными максимумами:
        // одинаковое состояние даёт одинаковое действие и предсказуемую политику.
        if (epsilon == 0.0) {
            return bestActions.get(0);
        }
        return bestActions.get(random.nextInt(bestActions.size()));
    }

    /** Максимум Q(s,a) по всем четырём действиям, включая попытки войти в стену. */
    private double maxQ(Position state) {
        double max = Double.NEGATIVE_INFINITY;
        for (Direction action : Direction.values()) {
            max = Math.max(max, qTable[state.row()][state.col()][action.ordinal()]);
        }
        return max;
    }

    /**
     * Автоматическое затухание epsilon в конце каждого завершённого эпизода.
     * До первого успеха исследование не опускается ниже заданного минимума.
     * После первого успеха epsilon постепенно стремится к нулю; ниже порога
     * он фиксируется ровно на 0.0, чтобы случайные шаги действительно прекратились.
     */
    private void updateEpsilon(boolean firstSuccess) {
        if (!cheeseDiscovered) {
            epsilon = Math.max(
                    MazeConfig.EPSILON_BEFORE_SUCCESS_MIN,
                    epsilon * MazeConfig.EPSILON_DECAY_BEFORE_SUCCESS);
            return;
        }

        if (firstSuccess) {
            epsilon = Math.min(epsilon, MazeConfig.EPSILON_AFTER_FIRST_SUCCESS);
            return;
        }

        if (epsilon <= 0.0) {
            epsilon = 0.0;
            return;
        }

        epsilon *= MazeConfig.EPSILON_DECAY_AFTER_SUCCESS;
        if (epsilon <= MazeConfig.EPSILON_ZERO_THRESHOLD) {
            epsilon = 0.0;
        }
    }

    /** Запоминает успешные траектории для отображения и сохранения; это не влияет на Q-таблицу. */
    private void updateBestResults() {
        List<Position> successfulPath = List.copyOf(currentPath);

        if (bestPath.isEmpty()
                || currentScore > bestScore + 1e-9
                || (Math.abs(currentScore - bestScore) <= 1e-9
                && successfulPath.size() < bestPath.size())) {
            bestScore = currentScore;
            bestPath = successfulPath;
        }

        if (shortestSuccessfulPath.isEmpty()
                || successfulPath.size() < shortestSuccessfulPath.size()
                || (successfulPath.size() == shortestSuccessfulPath.size()
                && currentScore > shortestPathScore + 1e-9)) {
            shortestSuccessfulPath = successfulPath;
            shortestPathScore = currentScore;
        }
    }

    private int maxSteps() {
        return maze.getRows() * maze.getCols() * MazeConfig.MAX_STEPS_MULTIPLIER;
    }

    private boolean episodeFinished() {
        return currentScore <= 0.0
                || position.equals(maze.getCheese())
                || episodeStep >= maxSteps();
    }

    public Maze getMaze() {
        return maze;
    }

    public Position getPosition() {
        return position;
    }

    public double getCurrentScore() {
        return currentScore;
    }

    public int getEpisodeStep() {
        return episodeStep;
    }

    /** Количество законченных эпизодов с TD-обновлениями Q-таблицы. */
    public long getCompletedEpisodes() {
        return completedEpisodes;
    }

    public long getCurrentEpisodeNumber() {
        return completedEpisodes + 1;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public double getBestScore() {
        return bestScore;
    }

    public boolean hasDiscoveredCheese() {
        return cheeseDiscovered;
    }

    public List<Position> getCurrentPath() {
        return Collections.unmodifiableList(currentPath);
    }

    public List<Position> getBestPath() {
        return bestPath;
    }

    public List<Position> getShortestSuccessfulPath() {
        return shortestSuccessfulPath;
    }

    public double getShortestPathScore() {
        return shortestPathScore;
    }

    public Set<Position> getConsumedWater() {
        return Collections.unmodifiableSet(consumedWater);
    }

    public Set<Position> getConsumedTraps() {
        return Collections.unmodifiableSet(consumedTraps);
    }

    public double[][][] copyQTable() {
        return copyQValues(qTable, maze.getRows(), maze.getCols());
    }

    private static double[][][] createInitialQTable(Maze maze) {
        double[][][] qValues = new double[maze.getRows()][maze.getCols()][Direction.values().length];
        for (int row = 0; row < maze.getRows(); row++) {
            for (int col = 0; col < maze.getCols(); col++) {
                for (int action = 0; action < Direction.values().length; action++) {
                    qValues[row][col][action] = MazeConfig.INITIAL_Q_VALUE;
                }
            }
        }
        return qValues;
    }

    private static double[][][] copyQValues(double[][][] source, int rows, int cols) {
        if (source.length != rows) {
            throw new IllegalArgumentException("Некорректное количество строк Q-таблицы.");
        }

        double[][][] copy = new double[rows][cols][Direction.values().length];
        for (int row = 0; row < rows; row++) {
            if (source[row].length != cols) {
                throw new IllegalArgumentException("Некорректное количество столбцов Q-таблицы.");
            }
            for (int col = 0; col < cols; col++) {
                if (source[row][col].length != Direction.values().length) {
                    throw new IllegalArgumentException("Некорректное количество действий в Q-таблице.");
                }
                System.arraycopy(source[row][col], 0, copy[row][col], 0, Direction.values().length);
            }
        }
        return copy;
    }

    private record Transition(
            Position state,
            Direction action,
            double reward,
            Position nextState,
            boolean terminal) {
    }
}
