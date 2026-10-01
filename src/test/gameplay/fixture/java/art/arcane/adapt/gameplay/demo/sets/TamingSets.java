package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;

public final class TamingSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_CLOSE = new DemoSet.Pose(6.5, 2.4, -0.75, 90f, 10f);
    private static final DemoSet.Pose CAMERA_RUN = new DemoSet.Pose(9.5, 3.0, -2.5, 90f, 10f);
    private static final DemoSet.Pose CAMERA_FIELD = new DemoSet.Pose(13.5, 3.6, -6.5, 90f, 10f);
    private static final DemoSet.Pose CAMERA_COW = new DemoSet.Pose(8.0, 2.6, -0.75, 90f, 8f);
    private static final DemoSet.Pose CAMERA_PACK = new DemoSet.Pose(6.0, 3.0, 3.0, 128f, 16f);
    private static final DemoSet.Pose CAMERA_STABLE = new DemoSet.Pose(8.5, 3.4, -0.75, 90f, 12f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("taming-yard", ACTOR_NORTH, CAMERA_CLOSE, false, List.of(), SimpleSet.OPEN_PLATE),
            new SimpleSet("taming-run", ACTOR_NORTH, CAMERA_RUN, false, List.of(), SimpleSet.OPEN_PLATE),
            new SimpleSet("taming-field", ACTOR_NORTH, CAMERA_FIELD, false, List.of(), SimpleSet.OPEN_PLATE),
            new SimpleSet("taming-cow", ACTOR_NORTH, CAMERA_COW, false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -2.0)), SimpleSet.OPEN_PLATE),
            new SimpleSet("taming-wild", ACTOR_NORTH, CAMERA_CLOSE, false,
                    List.of(new DemoSet.Sparring("WOLF", 0.5, 0, -2.0)), SimpleSet.OPEN_PLATE),
            new SimpleSet("taming-pack", ACTOR_NORTH, CAMERA_PACK, false,
                    List.of(new DemoSet.Sparring("WOLF", -1.0, 0, -1.5), new DemoSet.Sparring("WOLF", 0.5, 0, -2.0),
                            new DemoSet.Sparring("WOLF", 2.0, 0, -1.5)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("taming-stable", ACTOR_NORTH, CAMERA_STABLE, false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -2.0)), SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "taming";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }
}
