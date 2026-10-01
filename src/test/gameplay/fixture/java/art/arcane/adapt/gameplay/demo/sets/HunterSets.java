package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;

public final class HunterSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final List<DemoSet.Sparring> TROPHY_ARC = List.of(
            new DemoSet.Sparring("ZOMBIE", -1.415, 0, -1.107),
            new DemoSet.Sparring("ZOMBIE", -0.75, 0, -1.665),
            new DemoSet.Sparring("ZOMBIE", 0.066, 0, -1.962),
            new DemoSet.Sparring("ZOMBIE", 0.934, 0, -1.962),
            new DemoSet.Sparring("ZOMBIE", 1.75, 0, -1.665),
            new DemoSet.Sparring("ZOMBIE", 2.415, 0, -1.107)
    );
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("hunter-duel", ACTOR_NORTH, new DemoSet.Pose(7.5, 2.6, -0.5, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -1.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("hunter-retreat", new DemoSet.Pose(0.5, 0, -24.5, 180f, 0f), new DemoSet.Pose(14.5, 3.2, -17.5, 90f, 6f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -27.0)), SimpleSet.OPEN_PLATE),
            new SimpleSet("hunter-snare", ACTOR_NORTH, new DemoSet.Pose(9.5, 3.4, -3.5, 90f, 10f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -9.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("hunter-wound", ACTOR_NORTH, new DemoSet.Pose(7.5, 7.0, 7.5, 145f, 28f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -2.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("hunter-golem", ACTOR_NORTH, new DemoSet.Pose(9.5, 3.4, -1.0, 90f, 6f), false,
                    List.of(new DemoSet.Sparring("IRON_GOLEM", 0.5, 0, -2.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("hunter-range", ACTOR_NORTH, new DemoSet.Pose(11.5, 3.0, -4.5, 90f, 6f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -9.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("hunter-trophy", ACTOR_NORTH, new DemoSet.Pose(4.5, 5.0, 5.5, 150f, 27f), false, TROPHY_ARC,
                    SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "hunter";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }
}
