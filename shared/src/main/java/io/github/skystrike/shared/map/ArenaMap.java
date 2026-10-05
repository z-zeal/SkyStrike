package io.github.skystrike.shared.map;

import java.util.ArrayList;
import java.util.List;

/** The canonical static geometry and spawn points for the first arena. */
public final class ArenaMap {
    public static final float WIDTH = 3000;
    public static final float HEIGHT = 2000;

    private final List<Rect> solids;
    private final List<float[]> spawns;

    private ArenaMap(List<Rect> solids) {
        this.solids = List.copyOf(solids);
        this.spawns = List.of(new float[] {240, 180}, new float[] {2760, 180});
    }

    public List<Rect> solids() {
        return solids;
    }

    public List<float[]> spawns() {
        return spawns;
    }

    public static ArenaMap standard() {
        List<Rect> rectangles = new ArrayList<>();
        rectangles.add(new Rect(0, 0, 3000, 40));
        rectangles.add(new Rect(0, 0, 40, 2000));
        rectangles.add(new Rect(2960, 0, 40, 2000));
        rectangles.add(new Rect(0, 1960, 3000, 40));

        addSymmetric(rectangles, new Rect(500, 180, 300, 40));
        addSymmetric(rectangles, new Rect(900, 430, 220, 40));
        addSymmetric(rectangles, new Rect(1180, 760, 640, 40));
        addSymmetric(rectangles, new Rect(480, 980, 180, 220));
        addSymmetric(rectangles, new Rect(760, 1180, 260, 180));
        addSymmetric(rectangles, new Rect(1080, 1500, 840, 40));
        addSymmetric(rectangles, new Rect(1320, 760, 360, 260));
        addSymmetric(rectangles, new Rect(180, 520, 120, 180));
        addSymmetric(rectangles, new Rect(240, 820, 140, 120));

        return new ArenaMap(rectangles);
    }

    private static void addSymmetric(List<Rect> rectangles, Rect rectangle) {
        rectangles.add(rectangle);
        rectangles.add(rectangle.mirror(WIDTH / 2));
    }
}
