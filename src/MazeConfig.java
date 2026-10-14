import java.nio.file.Path;
import java.nio.file.Paths;

/** Общие настройки приложения и гиперпараметры Q-Learning. */
public final class MazeConfig {
    private MazeConfig() {
    }

    // Задержка анимации DFS при генерации лабиринта.
    public static final int GENERATION_STEP_DELAY_MS = 100;

    // Значение по умолчанию; пользователь может изменить его в окне настроек.
    // Меньше миллисекунд — быстрее движется мышь.
    public static final int SIMULATION_STEP_DELAY_MS = 60;
    public static final int SIMULATION_STEP_DELAY_MIN_MS = 1;
    public static final int SIMULATION_STEP_DELAY_MAX_MS = 1000;

    // Ограничения интерфейса.
    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 100;

    // Награды и штрафы.
    public static final double INITIAL_ENERGY = 50.00;
    public static final double WATER_REWARD = 0.50;
    public static final double TRAP_PENALTY = -1.00;
    public static final double STEP_PENALTY = -0.05;
    public static final double WALL_PENALTY = -0.20;
    public static final double CHEESE_REWARD = 15.00;

    // Гиперпараметры классического табличного Q-Learning.
    public static final double GAMMA = 0.995;
    public static final double ALPHA = 0.20;

    // Epsilon-greedy во время обучения.
    public static final double EPSILON_INITIAL = 1.00;
    // Допускаем настоящий ноль: после достаточного числа эпизодов агент
    // автоматически переходит к детерминированному жадному выбору argmax Q.
    public static final double EPSILON_MIN = 0.0;

    // До первого успеха исследование плавно уменьшается, но не ниже 0.20.
    public static final double EPSILON_DECAY_BEFORE_SUCCESS = 0.99;
    public static final double EPSILON_BEFORE_SUCCESS_MIN = 0.20;

    // После первого успеха epsilon ограничивается 0.20 и далее затухает
    // после каждого завершённого эпизода. Когда остаётся <= 0.001, ставим 0.0.
    // Коэффициент 0.995 даёт примерно 1000 эпизодов для постепенного перехода.
    public static final double EPSILON_AFTER_FIRST_SUCCESS = 0.20;
    public static final double EPSILON_DECAY_AFTER_SUCCESS = 0.995;
    public static final double EPSILON_ZERO_THRESHOLD = 0.001;

    // Максимальная длина одного эпизода в шагах.
    public static final int MAX_STEPS_MULTIPLIER = 12;

    // Не перерисовываем Swing на каждом шаге, если мышь движется очень быстро.
    public static final int UI_UPDATE_MIN_INTERVAL_MS = 20;

    // Нейтральная начальная оценка Q(s,a).
    public static final double INITIAL_Q_VALUE = 0.0;

    // Каталог сохранённых лабиринтов и моделей.
    public static final Path SAVE_DIRECTORY = Paths.get("saved_mazes");
}
