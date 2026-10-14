import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Панель визуализации лабиринта в неоновом стиле.
 *
 * Шлейф строится не по паре "предыдущая позиция -> текущая позиция",
 * а по новым рёбрам полного currentPath. Поэтому пропуски кадров Swing
 * не могут привести к появлению диагональной линии через карту.
 */
public final class MazePanel extends JPanel {
    private static final Color BACKGROUND = Color.BLACK;
    private static final Color CYAN = new Color(0, 255, 225);
    private static final Color WHITE = new Color(250, 250, 250);
    private static final Color WATER_BLUE = new Color(25, 145, 255);
    private static final Color MAGENTA = new Color(225, 40, 255);
    private static final Color YELLOW = new Color(255, 235, 40);
    private static final Color ORANGE = new Color(255, 145, 30);

    private Maze maze;
    private Position mousePosition;
    private List<Position> currentPath = List.of();
    private List<Position> bestPath = List.of();
    private Set<Position> consumedWater = Collections.emptySet();
    private Set<Position> consumedTraps = Collections.emptySet();

    /** Количество реальных проходов по каждому физическому ребру. */
    private final Map<TrailEdge, Integer> trailVisits = new HashMap<>();

    /** Номер эпохи, чья часть currentPath уже обработана в trailVisits. */
    private long trailEpisodeNumber = -1L;
    private int processedPathSize = 0;

    public MazePanel() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(800, 800));
        setOpaque(true);
    }

    public void setMaze(Maze maze) {
        this.maze = maze;
        clearTrailHistory();
        repaint();
    }

    public Maze getMaze() {
        return maze;
    }

    public void setMousePosition(Position mousePosition) {
        this.mousePosition = mousePosition;
        repaint();
    }

    /**
     * Передаёт в панель целиком согласованный снимок состояния мыши.
     * episodeNumber обязателен для правильного разделения шлейфов разных эпох.
     */
    public void setSimulationState(
            Position mousePosition,
            List<Position> currentPath,
            List<Position> bestPath,
            Set<Position> consumedWater,
            Set<Position> consumedTraps,
            long episodeNumber) {

        List<Position> newCurrentPath = currentPath == null ? List.of() : List.copyOf(currentPath);
        this.bestPath = bestPath == null ? List.of() : List.copyOf(bestPath);
        this.consumedWater = consumedWater == null
                ? Collections.emptySet()
                : Set.copyOf(consumedWater);
        this.consumedTraps = consumedTraps == null
                ? Collections.emptySet()
                : Set.copyOf(consumedTraps);

        if (episodeNumber != trailEpisodeNumber) {
            // Новая эпоха: предыдущий конечный участок и новый старт нельзя соединять.
            trailEpisodeNumber = episodeNumber;
            processedPathSize = 0;
        }

        // Добавляем ВСЕ новые рёбра, а не только последнее.
        // Это особенно важно при задержке 1 мс, когда Swing может пропустить несколько кадров.
        int firstNewEdgeIndex = Math.max(1, processedPathSize);
        for (int i = firstNewEdgeIndex; i < newCurrentPath.size(); i++) {
            Position a = newCurrentPath.get(i - 1);
            Position b = newCurrentPath.get(i);
            addTrailEdgeIfRealMove(a, b);
        }

        processedPathSize = newCurrentPath.size();
        this.currentPath = newCurrentPath;
        this.mousePosition = mousePosition;
        repaint();
    }

    /** Совместимость со старым вызовом, если где-то он остался. */
    public void setSimulationState(
            Position mousePosition,
            List<Position> currentPath,
            List<Position> bestPath,
            Set<Position> consumedWater,
            Set<Position> consumedTraps) {
        long nextEpisode = trailEpisodeNumber < 0 ? 0 : trailEpisodeNumber;
        setSimulationState(
                mousePosition,
                currentPath,
                bestPath,
                consumedWater,
                consumedTraps,
                nextEpisode);
    }

    public void clearSimulationState() {
        mousePosition = null;
        currentPath = List.of();
        bestPath = List.of();
        consumedWater = Collections.emptySet();
        consumedTraps = Collections.emptySet();
        clearTrailHistory();
        repaint();
    }

    private void clearTrailHistory() {
        trailVisits.clear();
        trailEpisodeNumber = -1L;
        processedPathSize = 0;
    }

    private void addTrailEdgeIfRealMove(Position a, Position b) {
        if (maze == null || a == null || b == null || a.equals(b)) {
            return;
        }

        int distance = Math.abs(a.row() - b.row()) + Math.abs(a.col() - b.col());
        if (distance != 1) {
            // Никогда не рисуем диагонали/"прыжки" между дальними клетками.
            return;
        }

        Direction direction = directionBetween(a, b);
        if (!maze.canMove(a, direction)) {
            // Дополнительная страховка: линия должна совпадать с реальным проходом.
            return;
        }

        TrailEdge edge = TrailEdge.of(a, b);
        trailVisits.merge(edge, 1, Integer::sum);
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

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        if (maze == null) {
            drawEmptyMessage((Graphics2D) graphics);
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());

            int margin = 20;
            double cellSize = Math.min(
                    (getWidth() - 2.0 * margin) / maze.getCols(),
                    (getHeight() - 2.0 * margin) / maze.getRows());
            double offsetX = (getWidth() - cellSize * maze.getCols()) / 2.0;
            double offsetY = (getHeight() - cellSize * maze.getRows()) / 2.0;

            paintTrail(g, offsetX, offsetY, cellSize);
            paintBestPath(g, offsetX, offsetY, cellSize);

            for (int r = 0; r < maze.getRows(); r++) {
                for (int c = 0; c < maze.getCols(); c++) {
                    double x = offsetX + c * cellSize;
                    double y = offsetY + r * cellSize;
                    paintElements(g, maze, r, c, x, y, cellSize);
                    paintWalls(g, maze, r, c, x, y, cellSize);
                }
            }

            if (mousePosition != null) {
                paintMouse(g, mousePosition, offsetX, offsetY, cellSize);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintTrail(Graphics2D g, double offsetX, double offsetY, double cellSize) {
        for (Map.Entry<TrailEdge, Integer> entry : trailVisits.entrySet()) {
            TrailEdge edge = entry.getKey();
            int visits = entry.getValue();
            Color color = trailColor(visits);

            double ax = offsetX + (edge.a().col() + 0.5) * cellSize;
            double ay = offsetY + (edge.a().row() + 0.5) * cellSize;
            double bx = offsetX + (edge.b().col() + 0.5) * cellSize;
            double by = offsetY + (edge.b().row() + 0.5) * cellSize;

            drawGlowLine(g, ax, ay, bx, by,
                    color,
                    Math.min(4.0f, 1.8f + visits * 0.12f),
                    3,
                    25);
        }
    }

    private void paintBestPath(Graphics2D g, double offsetX, double offsetY, double cellSize) {
        if (bestPath.size() < 2) {
            return;
        }

        Color softWhite = new Color(220, 220, 220, 90);
        Stroke stroke = new BasicStroke(
                Math.max(1.5f, (float) (cellSize * 0.06)),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND);
        g.setColor(softWhite);
        g.setStroke(stroke);

        for (int i = 1; i < bestPath.size(); i++) {
            Position a = bestPath.get(i - 1);
            Position b = bestPath.get(i);
            if (Math.abs(a.row() - b.row()) + Math.abs(a.col() - b.col()) != 1) {
                continue;
            }
            double ax = offsetX + (a.col() + 0.5) * cellSize;
            double ay = offsetY + (a.row() + 0.5) * cellSize;
            double bx = offsetX + (b.col() + 0.5) * cellSize;
            double by = offsetY + (b.row() + 0.5) * cellSize;
            g.draw(new Line2D.Double(ax, ay, bx, by));
        }
    }

    private void paintElements(
            Graphics2D g,
            Maze maze,
            int r,
            int c,
            double x,
            double y,
            double size) {
        Position p = new Position(r, c);
        MazeCell cell = maze.getCell(r, c);

        if (p.equals(maze.getStart()) && !p.equals(mousePosition)) {
            double d = size * 0.42;
            double cx = x + (size - d) / 2.0;
            double cy = y + (size - d) / 2.0;
            drawGlowCircle(g, cx, cy, d, ORANGE, 5, 35);
            g.setColor(ORANGE);
            g.fill(new Ellipse2D.Double(cx, cy, d, d));
        }

        switch (cell.getType()) {
            case WATER -> {
                if (consumedWater.contains(p)) {
                    return;
                }
                double d = size * 0.36;
                double cx = x + (size - d) / 2.0;
                double cy = y + (size - d) / 2.0;
                drawGlowCircle(g, cx, cy, d, WATER_BLUE, 5, 35);
                g.setColor(WATER_BLUE);
                g.fill(new Ellipse2D.Double(cx, cy, d, d));
            }
            case TRAP -> {
                if (consumedTraps.contains(p)) {
                    return;
                }
                double d = size * 0.34;
                double cx = x + (size - d) / 2.0;
                double cy = y + (size - d) / 2.0;
                drawGlowSquare(g, cx, cy, d, MAGENTA, 5, 35);
                g.setColor(MAGENTA);
                g.fill(new Rectangle2D.Double(cx, cy, d, d));
            }
            case CHEESE -> {
                double d = size * 0.54;
                double cx = x + (size - d) / 2.0;
                double cy = y + (size - d) / 2.0;
                drawGlowCircle(g, cx, cy, d, YELLOW, 6, 45);
                g.setColor(YELLOW);
                g.fill(new Ellipse2D.Double(cx, cy, d, d));
                g.setColor(Color.BLACK);
                if (size >= 16) {
                    g.drawString("C", (float) (x + size * 0.42), (float) (y + size * 0.60));
                }
            }
            case EMPTY -> {
                // Ничего не рисуем.
            }
        }
    }

    private void paintWalls(
            Graphics2D g,
            Maze maze,
            int r,
            int c,
            double x,
            double y,
            double size) {
        MazeCell cell = maze.getCell(r, c);

        if (cell.hasWall(Direction.RIGHT)) {
            drawGlowLine(g, x + size, y, x + size, y + size, CYAN, 2.0f, 5, 42);
        }
        if (cell.hasWall(Direction.DOWN)) {
            drawGlowLine(g, x, y + size, x + size, y + size, CYAN, 2.0f, 5, 42);
        }
        if (c == 0 && cell.hasWall(Direction.LEFT)) {
            drawGlowLine(g, x, y, x, y + size, CYAN, 2.0f, 5, 42);
        }
        if (r == 0 && cell.hasWall(Direction.UP)) {
            drawGlowLine(g, x, y, x + size, y, CYAN, 2.0f, 5, 42);
        }
    }

    private static Color trailColor(int visits) {
        double t = 1.0 - Math.exp(-visits / 3.5);
        int channel = (int) Math.round(65 + t * 165);
        channel = Math.max(65, Math.min(230, channel));
        int alpha = (int) Math.round(110 + t * 100);
        return new Color(channel, channel, channel, alpha);
    }

    private static void drawGlowLine(
            Graphics2D g,
            double x1,
            double y1,
            double x2,
            double y2,
            Color color,
            float coreWidth,
            int layers,
            int alphaStep) {
        for (int i = layers; i >= 1; i--) {
            float width = coreWidth + i * 2.6f;
            int alpha = Math.max(8, Math.min(120, alphaStep - i * 4));
            g.setColor(withAlpha(color, alpha));
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }

        g.setColor(color);
        g.setStroke(new BasicStroke(coreWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void drawGlowCircle(
            Graphics2D g,
            double x,
            double y,
            double size,
            Color color,
            int layers,
            int alphaStep) {
        double centerX = x + size / 2.0;
        double centerY = y + size / 2.0;
        for (int i = layers; i >= 1; i--) {
            double glow = i * 3.0;
            int alpha = Math.max(8, Math.min(110, alphaStep - i * 4));
            g.setColor(withAlpha(color, alpha));
            g.fill(new Ellipse2D.Double(
                    centerX - size / 2.0 - glow / 2.0,
                    centerY - size / 2.0 - glow / 2.0,
                    size + glow,
                    size + glow));
        }
    }

    private static void drawGlowSquare(
            Graphics2D g,
            double x,
            double y,
            double size,
            Color color,
            int layers,
            int alphaStep) {
        double centerX = x + size / 2.0;
        double centerY = y + size / 2.0;
        for (int i = layers; i >= 1; i--) {
            double glow = i * 2.7;
            int alpha = Math.max(8, Math.min(115, alphaStep - i * 4));
            g.setColor(withAlpha(color, alpha));
            g.fill(new Rectangle2D.Double(
                    centerX - size / 2.0 - glow / 2.0,
                    centerY - size / 2.0 - glow / 2.0,
                    size + glow,
                    size + glow));
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void paintMouse(
            Graphics2D g,
            Position position,
            double offsetX,
            double offsetY,
            double cellSize) {
        double d = cellSize * 0.55;
        double x = offsetX + position.col() * cellSize + (cellSize - d) / 2.0;
        double y = offsetY + position.row() * cellSize + (cellSize - d) / 2.0;

        Color softWhite = new Color(255, 255, 255, 100);
        drawGlowCircle(g, x, y, d, softWhite, 3, 52);
        g.setColor(WHITE);
        g.fill(new Ellipse2D.Double(x, y, d, d));

        g.setColor(Color.BLACK);
        double eye = Math.max(2.0, d * 0.11);
        double eyeY = y + d * 0.30;
        g.fill(new Ellipse2D.Double(x + d * 0.28, eyeY, eye, eye));
        g.fill(new Ellipse2D.Double(x + d * 0.61, eyeY, eye, eye));
    }

    private void drawEmptyMessage(Graphics2D g) {
        g.setColor(new Color(180, 180, 180));
        String message = "Нажмите «Создать лабиринт»";
        int textWidth = g.getFontMetrics().stringWidth(message);
        g.drawString(message, (getWidth() - textWidth) / 2, getHeight() / 2);
    }

    private record TrailEdge(Position a, Position b) {
        private static TrailEdge of(Position first, Position second) {
            if (first.row() < second.row()
                    || (first.row() == second.row() && first.col() <= second.col())) {
                return new TrailEdge(first, second);
            }
            return new TrailEdge(second, first);
        }
    }
}
