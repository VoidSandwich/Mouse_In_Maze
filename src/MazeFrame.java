import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public final class MazeFrame extends JFrame {
    private final MazeSession session;
    private final MazePanel mazePanel = new MazePanel();

    private final JLabel statusLabel = new JLabel("Поле пустое. Нажмите «Создать лабиринт».");
    private final JLabel wallLabel = new JLabel("Стен: —");
    private final JLabel scoreLabel = new JLabel("Очки: —");
    private final JLabel episodeLabel = new JLabel("Эпоха: —");
    private final JLabel bestLabel = new JLabel("Лучший результат: —");
    private final JLabel epsilonLabel = new JLabel("Epsilon: —");

    private final JButton createButton = new JButton("Создать лабиринт");
    private final JButton placeElementsButton = new JButton("Разместить воду / ток");
    private final JButton startButton = new JButton("Запустить симуляцию");
    private final JButton stopButton = new JButton("Остановить процесс");
    private final JButton stepButton = new JButton("Одна итерация");
    private final JButton saveButton = new JButton("Сохранить лабиринт и путь");
    private final JButton backButton = new JButton("Назад");

    private final Random random = new Random();
    private final MazeJsonStore store = new MazeJsonStore(MazeConfig.SAVE_DIRECTORY);

    private SwingWorker<Maze, Maze> generationWorker;
    private Maze maze;
    private QLearningAgent agent;
    private Thread simulationThread;
    private volatile boolean simulationRunning;

    /** Последнее состояние, ожидающее отрисовки. */
    private final AtomicReference<SimulationViewState> pendingView = new AtomicReference<>();
    private final AtomicBoolean viewUpdateScheduled = new AtomicBoolean(false);
    private volatile long lastViewPublishNanos = 0L;

    public MazeFrame(MazeSession session) {
        super("Мышь в лабиринте — обучение Q-Learning");
        this.session = session;
        buildWindow();
        configureForEmptyMaze();
    }

    public MazeFrame(SavedMazeModel model) {
        super("Мышь в лабиринте — загруженная модель: " + model.name());
        this.session = new MazeSession(
                model.maze().getRows(),
                model.maze().getCols(),
                model.initialEnergy(),
                model.maze().getWater().size(),
                model.maze().getTraps().size(),
                MazeConfig.SIMULATION_STEP_DELAY_MS);
        buildWindow();

        maze = model.maze().copy();
        mazePanel.setMaze(maze);
        wallLabel.setText("Стен: " + maze.countInternalWalls());
        agent = new QLearningAgent(
                maze,
                model.initialEnergy(),
                random,
                model.epsilon(),
                model.completedEpisodes(),
                model.qTable(),
                model.bestScore(),
                model.bestPath(),
                model.shortestSuccessfulPath(),
                model.shortestPathScore());
        updateSimulationViewNow();
        statusLabel.setText("Модель загружена. Обучение можно продолжить с сохранённой Q-таблицей.");
        createButton.setEnabled(false);
        placeElementsButton.setEnabled(false);
        startButton.setEnabled(true);
        stepButton.setEnabled(true);
        saveButton.setEnabled(!agent.getBestPath().isEmpty());
    }

    private void buildWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 900);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(new JLabel("Размер: " + session.rows() + " × " + session.cols()));
        top.add(new JLabel("| Задержка мыши: " + session.simulationStepDelayMs() + " мс"));
        top.add(new JLabel("| γ=" + MazeConfig.GAMMA));
        top.add(new JLabel("| α=" + MazeConfig.ALPHA));
        top.add(new JLabel("| Лимит эпизода: "
                + session.rows() * session.cols() * MazeConfig.MAX_STEPS_MULTIPLIER
                + " шагов"));

        JPanel buttons = new JPanel(new GridLayout(2, 4, 8, 8));
        buttons.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        buttons.add(createButton);
        buttons.add(placeElementsButton);
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(stepButton);
        buttons.add(saveButton);
        buttons.add(backButton);
        buttons.add(new JLabel());

        JPanel stats = new JPanel(new GridLayout(2, 3, 14, 4));
        stats.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        stats.add(scoreLabel);
        stats.add(episodeLabel);
        stats.add(bestLabel);
        stats.add(epsilonLabel);
        stats.add(wallLabel);
        stats.add(statusLabel);

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.NORTH);
        south.add(stats, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(mazePanel, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        createButton.addActionListener(e -> startGeneration());
        placeElementsButton.addActionListener(e -> placeElements());
        startButton.addActionListener(e -> startContinuousSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        stepButton.addActionListener(e -> runSingleEpisode());
        saveButton.addActionListener(e -> saveCurrentModel());
        backButton.addActionListener(e -> goBack());

        stopButton.setEnabled(false);
        startButton.setEnabled(false);
        stepButton.setEnabled(false);
        placeElementsButton.setEnabled(false);
        saveButton.setEnabled(false);
    }

    private void configureForEmptyMaze() {
        scoreLabel.setText("Очки: —");
        episodeLabel.setText("Эпоха: —");
        bestLabel.setText("Лучший результат: —");
        epsilonLabel.setText("Epsilon: —");
    }

    private void startGeneration() {
        if (generationWorker != null && !generationWorker.isDone()) {
            return;
        }
        stopSimulation();
        createButton.setEnabled(false);
        placeElementsButton.setEnabled(false);
        startButton.setEnabled(false);
        stepButton.setEnabled(false);
        saveButton.setEnabled(false);
        statusLabel.setText("DFS строит лабиринт...");

        generationWorker = new SwingWorker<>() {
            @Override
            protected Maze doInBackground() throws Exception {
                return MazeGenerator.generate(
                        session.rows(),
                        session.cols(),
                        random,
                        MazeConfig.GENERATION_STEP_DELAY_MS,
                        this::publish);
            }

            @Override
            protected void process(List<Maze> chunks) {
                if (!chunks.isEmpty()) {
                    maze = chunks.get(chunks.size() - 1);
                    mazePanel.setMaze(maze);
                    wallLabel.setText("Стен: " + maze.countInternalWalls());
                }
            }

            @Override
            protected void done() {
                try {
                    maze = get();
                    mazePanel.setMaze(maze);
                    wallLabel.setText("Стен: " + maze.countInternalWalls());
                    statusLabel.setText("Лабиринт создан. Выход находится в клетке с сыром. Теперь разместите воду и ток.");
                    placeElementsButton.setEnabled(true);
                } catch (CancellationException ex) {
                    // Нормальная отмена.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ex) {
                    createButton.setEnabled(true);
                    Throwable cause = ex.getCause();
                    showError(cause != null ? cause.getMessage() : ex.getMessage());
                } finally {
                    generationWorker = null;
                }
            }
        };
        generationWorker.execute();
    }

    private void placeElements() {
        if (maze == null) {
            return;
        }
        try {
            maze.placeRandomElements(session.waterCount(), session.trapCount(), random);
            mazePanel.setMaze(maze.copy());
            mazePanel.clearSimulationState();

            agent = new QLearningAgent(maze, session.initialEnergy(), random);
            updateSimulationViewNow();
            statusLabel.setText("Объекты размещены. Мышь готова к обучению.");

            placeElementsButton.setEnabled(false);
            startButton.setEnabled(true);
            stepButton.setEnabled(true);
            saveButton.setEnabled(false);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError(ex.getMessage());
        }
    }

    private void startContinuousSimulation() {
        if (!canRunSimulation() || simulationRunning) {
            return;
        }

        simulationRunning = true;
        setSimulationButtons(true);
        statusLabel.setText("Обучение Q-Learning идёт непрерывно; epsilon уменьшается автоматически...");
        simulationThread = new Thread(() -> {
            try {
                while (simulationRunning) {
                    runUntilEpisodeFinished();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> showError(ex.getMessage()));
            } finally {
                simulationRunning = false;
                SwingUtilities.invokeLater(() -> setSimulationButtons(false));
            }
        }, "q-learning-simulation");
        simulationThread.start();
    }

    private void runSingleEpisode() {
        if (!canRunSimulation() || simulationRunning) {
            return;
        }

        simulationRunning = true;
        setSimulationButtons(true);
        statusLabel.setText("Выполняется один эпизод Q-Learning...");
        simulationThread = new Thread(() -> {
            try {
                runUntilEpisodeFinished();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> showError(ex.getMessage()));
            } finally {
                simulationRunning = false;
                SwingUtilities.invokeLater(() -> setSimulationButtons(false));
            }
        }, "q-learning-single-episode");
        simulationThread.start();
    }

    private void runUntilEpisodeFinished() throws InterruptedException {
        while (simulationRunning) {
            AgentStepResult result = agent.step();
            publishSimulationState(false);

            if (result.episodeFinished()) {
                try {
                    Thread.sleep(session.simulationStepDelayMs());
                } finally {
                    agent.resetEpisode(session.initialEnergy());
                }

                publishSimulationState(true);

                final String message;
                if (result.success()) {
                    message = "Успех: сыр найден. " + result.finishReason()
                            + ", награда шага " + format(result.reward()) + ".";
                } else {
                    message = "Эпизод завершён: " + result.finishReason() + ".";
                }
                SwingUtilities.invokeLater(() -> statusLabel.setText(message));
                return;
            }

            Thread.sleep(session.simulationStepDelayMs());
        }
    }

    /**
     * Публикует неизменяемый снимок состояния.
     * При 1 мс обновления объединяются: Swing получает не тысячи задач,
     * а не более одного ожидающего обновления с самым свежим состоянием.
     */
    private void publishSimulationState(boolean force) {
        if (agent == null) {
            return;
        }

        long now = System.nanoTime();
        long minIntervalNanos = MazeConfig.UI_UPDATE_MIN_INTERVAL_MS * 1_000_000L;
        if (!force && now - lastViewPublishNanos < minIntervalNanos) {
            return;
        }
        lastViewPublishNanos = now;

        SimulationViewState snapshot = captureViewState();
        pendingView.set(snapshot);

        if (viewUpdateScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::applyPendingView);
        }
    }

    private SimulationViewState captureViewState() {
        return new SimulationViewState(
                agent.getPosition(),
                List.copyOf(agent.getCurrentPath()),
                List.copyOf(agent.getBestPath()),
                SetCopy.copy(agent.getConsumedWater()),
                SetCopy.copy(agent.getConsumedTraps()),
                agent.getCurrentScore(),
                agent.getCurrentEpisodeNumber(),
                agent.getBestScore(),
                agent.getEpsilon());
    }

    private void applyPendingView() {
        SimulationViewState snapshot = pendingView.getAndSet(null);
        if (snapshot != null && agent != null) {
            updateSimulationView(snapshot);
        }

        viewUpdateScheduled.set(false);
        if (pendingView.get() != null && viewUpdateScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::applyPendingView);
        }
    }

    private void updateSimulationViewNow() {
        if (agent == null) {
            return;
        }
        updateSimulationView(captureViewState());
    }

    private void updateSimulationView(SimulationViewState state) {
        scoreLabel.setText("Очки: " + format(state.score()));
        episodeLabel.setText("Эпоха: " + state.episodeNumber());
        bestLabel.setText(Double.isFinite(state.bestScore())
                ? "Лучший результат: " + format(state.bestScore())
                : "Лучший результат: —");
        epsilonLabel.setText("Epsilon: " + formatEpsilon(state.epsilon()));
        mazePanel.setSimulationState(
                state.position(),
                state.currentPath(),
                state.bestPath(),
                state.consumedWater(),
                state.consumedTraps(),
                state.episodeNumber());
        saveButton.setEnabled(!state.bestPath().isEmpty() && !simulationRunning);
    }

    private boolean canRunSimulation() {
        return agent != null && maze != null;
    }

    private void setSimulationButtons(boolean running) {
        startButton.setEnabled(!running && canRunSimulation());
        stepButton.setEnabled(!running && canRunSimulation());
        createButton.setEnabled(!running && maze == null);
        placeElementsButton.setEnabled(!running && agent == null && maze != null);
        stopButton.setEnabled(running);
        saveButton.setEnabled(!running && agent != null && !agent.getBestPath().isEmpty());
        backButton.setEnabled(!running);
    }

    private void stopSimulation() {
        simulationRunning = false;
        if (simulationThread != null) {
            simulationThread.interrupt();
            simulationThread = null;
        }
        if (agent != null) {
            statusLabel.setText("Обучение остановлено. Q-таблица и прогресс сохранены в памяти.");
            updateSimulationViewNow();
        }
        setSimulationButtons(false);
    }

    private void saveCurrentModel() {
        if (agent == null || agent.getBestPath().isEmpty()) {
            showError("Сохранение доступно после хотя бы одного успешного достижения сыра.");
            return;
        }

        String name = JOptionPane.showInputDialog(
                this,
                "Введите название сохранения:",
                "Сохранение лабиринта",
                JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }

        try {
            SavedMazeModel model = new SavedMazeModel(
                    name.trim(),
                    maze.copy(),
                    session.initialEnergy(),
                    agent.copyQTable(),
                    agent.getCompletedEpisodes(),
                    agent.getEpsilon(),
                    agent.getBestScore(),
                    agent.getBestPath(),
                    agent.getShortestSuccessfulPath(),
                    agent.getShortestPathScore());
            store.save(model);
            statusLabel.setText("Сохранено: " + name.trim());
        } catch (IOException | RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void goBack() {
        stopSimulation();
        if (generationWorker != null && !generationWorker.isDone()) {
            generationWorker.cancel(true);
        }
        generationWorker = null;
        new SettingsFrame().setVisible(true);
        dispose();
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private static String formatEpsilon(double value) {
        return value == 0.0 ? "0.0000" : String.format("%.4f", value);
    }

    private void showError(String message) {
        String safeMessage = (message == null || message.isBlank())
                ? "Неизвестная ошибка."
                : message;
        JOptionPane.showMessageDialog(this, safeMessage, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private record SimulationViewState(
            Position position,
            List<Position> currentPath,
            List<Position> bestPath,
            java.util.Set<Position> consumedWater,
            java.util.Set<Position> consumedTraps,
            double score,
            long episodeNumber,
            double bestScore,
            double epsilon) {
    }

    private static final class SetCopy {
        private SetCopy() {
        }

        static java.util.Set<Position> copy(java.util.Set<Position> source) {
            return java.util.Set.copyOf(source);
        }
    }
}
