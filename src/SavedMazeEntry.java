import java.nio.file.Path;

public record SavedMazeEntry(String name, int rows, int cols, long episodes, Path file) {
    @Override
    public String toString() {
        return name + "   (" + rows + "×" + cols + ", эпох: " + episodes + ")";
    }
}
