import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class SettingsFrame extends JFrame {
    private final JSpinner rowsSpinner;
    private final JSpinner colsSpinner;
    private final JSpinner energySpinner;
    private final JSpinner waterSpinner;
    private final JSpinner trapsSpinner;
    private final JSpinner simulationDelaySpinner;

    public SettingsFrame() {
        super("Мышь в лабиринте — настройки");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 430);
        setLocationRelativeTo(null);

        rowsSpinner = new JSpinner(new SpinnerNumberModel(10, MazeConfig.MIN_SIZE, MazeConfig.MAX_SIZE, 1));
        colsSpinner = new JSpinner(new SpinnerNumberModel(10, MazeConfig.MIN_SIZE, MazeConfig.MAX_SIZE, 1));

        energySpinner = new JSpinner(new SpinnerNumberModel(
                MazeConfig.INITIAL_ENERGY,
                0.10,
                100000.00,
                0.10));
        JSpinner.NumberEditor energyEditor = new JSpinner.NumberEditor(energySpinner, "0.00");
        energySpinner.setEditor(energyEditor);
        energyEditor.getTextField().setHorizontalAlignment(JTextField.RIGHT);

        waterSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 10000, 1));
        trapsSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 10000, 1));
        simulationDelaySpinner = new JSpinner(new SpinnerNumberModel(
                MazeConfig.SIMULATION_STEP_DELAY_MS,
                MazeConfig.SIMULATION_STEP_DELAY_MIN_MS,
                MazeConfig.SIMULATION_STEP_DELAY_MAX_MS,
                1));
        simulationDelaySpinner.setToolTipText("Миллисекунды на один шаг; меньшее значение означает более высокую скорость.");

        alignIntegerSpinnerRight(rowsSpinner);
        alignIntegerSpinnerRight(colsSpinner);
        alignIntegerSpinnerRight(waterSpinner);
        alignIntegerSpinnerRight(trapsSpinner);
        alignIntegerSpinnerRight(simulationDelaySpinner);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(18, 18, 12, 18));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        addField(form, gbc, row++, "Строк:", rowsSpinner);
        addField(form, gbc, row++, "Столбцов:", colsSpinner);
        addField(form, gbc, row++, "Начальные очки:", energySpinner);
        addField(form, gbc, row++, "Количество воды:", waterSpinner);
        addField(form, gbc, row++, "Количество ловушек:", trapsSpinner);
        addField(form, gbc, row++, "Задержка шага мыши (мс):", simulationDelaySpinner);

        //JLabel hint = new JLabel("<html>DFS автоматически создаёт связный лабиринт. Количество внутренних стен определяется размером поля.<br>Меньшая задержка означает более быстрое движение мыши. На границе будет один выход с сыром.</html>");
        //hint.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        JPanel buttons = new JPanel();
        JButton confirmButton = new JButton("Подтвердить");
        JButton savedButton = new JButton("Выбрать сохранённый лабиринт");
        savedButton.addActionListener(e -> new SavedMazesFrame(this).setVisible(true));
        confirmButton.addActionListener(e -> openMazeFrame());
        buttons.add(confirmButton);
        buttons.add(savedButton);

        JPanel center = new JPanel(new BorderLayout());
        center.add(form, BorderLayout.CENTER);
        //center.add(hint, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private static void addField(
            JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private static void alignIntegerSpinnerRight(JSpinner spinner) {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setHorizontalAlignment(JTextField.RIGHT);
        }
    }

    private void openMazeFrame() {
        try {
            int rows = (int) rowsSpinner.getValue();
            int cols = (int) colsSpinner.getValue();
            double energy = ((Number) energySpinner.getValue()).doubleValue();
            int water = (int) waterSpinner.getValue();
            int traps = (int) trapsSpinner.getValue();
            int simulationDelay = (int) simulationDelaySpinner.getValue();

            if (energy <= 0) {
                throw new IllegalArgumentException("Начальные очки должны быть больше нуля.");
            }
            if (water + traps > rows * cols - 2) {
                throw new IllegalArgumentException(
                        "Для воды и ловушек недостаточно клеток: старт и сыр зарезервированы.");
            }

            MazeSession session = new MazeSession(
                    rows, cols, energy, water, traps, simulationDelay);
            MazeFrame frame = new MazeFrame(session);
            frame.setVisible(true);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                    this, ex.getMessage(), "Ошибка параметров", JOptionPane.ERROR_MESSAGE);
        }
    }
}
