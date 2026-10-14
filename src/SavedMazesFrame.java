import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SavedMazesFrame extends JFrame {
    private final Window parent;
    private final MazeJsonStore store = new MazeJsonStore(MazeConfig.SAVE_DIRECTORY);
    private final DefaultListModel<SavedMazeEntry> listModel = new DefaultListModel<>();
    private final JList<SavedMazeEntry> list = new JList<>(listModel);
    private final JButton loadButton = new JButton("Загрузить");
    private final JButton deleteButton = new JButton("Удалить");

    public SavedMazesFrame(Window parent) {
        super("Пройденные лабиринты");
        this.parent = parent;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(620, 420);
        setLocationRelativeTo(parent);

        list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> updateButtons());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Закрыть");
        buttons.add(loadButton);
        buttons.add(deleteButton);
        buttons.add(closeButton);

        add(new JScrollPane(list), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadSelected());
        deleteButton.addActionListener(e -> deleteSelected());
        closeButton.addActionListener(e -> dispose());

        reload();
    }

    private void reload() {
        listModel.clear();
        try {
            List<SavedMazeModel> models = store.loadAll();
            List<Path> paths = store.listFiles();
            // Соединяем успешные загрузки с реальными файлами по имени файла.
            for (Path path : paths) {
                try {
                    SavedMazeModel model = store.load(path);
                    listModel.addElement(new SavedMazeEntry(
                            model.name(),
                            model.maze().getRows(),
                            model.maze().getCols(),
                            model.completedEpisodes(),
                            path));
                } catch (Exception ignored) {
                    // Не показываем повреждённые файлы как валидные сохранения.
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка чтения сохранений", JOptionPane.ERROR_MESSAGE);
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean selected = list.getSelectedIndex() >= 0;
        loadButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
    }

    private void loadSelected() {
        SavedMazeEntry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        try {
            SavedMazeModel model = store.load(entry.file());
            new MazeFrame(model).setVisible(true);
            dispose();
            if (parent != null) {
                parent.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка загрузки", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        SavedMazeEntry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Удалить сохранённый лабиринт «" + entry.name() + "»?",
                "Подтверждение удаления",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            store.delete(entry.file());
            reload();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка удаления", JOptionPane.ERROR_MESSAGE);
        }
    }
}
