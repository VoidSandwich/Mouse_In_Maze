import java.util.EnumSet;

public final class MazeCell {
    private final EnumSet<Direction> walls = EnumSet.allOf(Direction.class);
    private boolean visited;
    private CellType type = CellType.EMPTY;

    public boolean hasWall(Direction direction) {
        return walls.contains(direction);
    }

    public void addWall(Direction direction) {
        walls.add(direction);
    }

    public void removeWall(Direction direction) {
        walls.remove(direction);
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public CellType getType() {
        return type;
    }

    public void setType(CellType type) {
        this.type = type;
    }

    public MazeCell copy() {
        MazeCell copy = new MazeCell();
        copy.walls.clear();
        copy.walls.addAll(walls);
        copy.visited = visited;
        copy.type = type;
        return copy;
    }
}
