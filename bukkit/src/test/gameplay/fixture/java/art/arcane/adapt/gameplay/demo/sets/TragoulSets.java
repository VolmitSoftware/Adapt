package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class TragoulSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_PACK = new DemoSet.Pose(7.5, 5.0, 4.5, 138.8f, 20.6f);
    private static final DemoSet.Pose CAMERA_EAST = new DemoSet.Pose(10.5, 3.5, -3.5, 90f, 12f);
    private static final int SENSE_WALL_Z = -5;
    private static final int SENSE_WALL_WEST_X = -2;
    private static final int SENSE_WALL_EAST_X = 3;
    private static final int SENSE_WALL_HEIGHT = 3;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("tragoul-globe", ACTOR_NORTH, CAMERA_PACK, false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -2.0), new DemoSet.Sparring("ZOMBIE", -2.5, 0, -4.5),
                            new DemoSet.Sparring("ZOMBIE", 3.5, 0, -4.5), new DemoSet.Sparring("ZOMBIE", -1.5, 0, -7.0),
                            new DemoSet.Sparring("ZOMBIE", 2.5, 0, -7.0)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("tragoul-nova", ACTOR_NORTH, CAMERA_PACK, false,
                    List.of(new DemoSet.Sparring("HUSK", 0.5, 0, -2.0), new DemoSet.Sparring("ZOMBIE", -2.0, 0, -4.5),
                            new DemoSet.Sparring("ZOMBIE", 3.0, 0, -4.5), new DemoSet.Sparring("ZOMBIE", 0.5, 0, -6.0)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("tragoul-lance", ACTOR_NORTH, CAMERA_EAST, false,
                    List.of(new DemoSet.Sparring("HUSK", 0.5, 0, -2.0), new DemoSet.Sparring("ZOMBIE", 3.5, 0, -7.5)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("tragoul-plague", ACTOR_NORTH, CAMERA_PACK, false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -2.5), new DemoSet.Sparring("COW", -2.5, 0, -5.5),
                            new DemoSet.Sparring("COW", 3.5, 0, -5.5)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("tragoul-sense", ACTOR_NORTH, new DemoSet.Pose(9.5, 3.5, -3.5, 90f, 12f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -2.5), new DemoSet.Sparring("COW", 1.5, 0, -7.5),
                            new DemoSet.Sparring("COW", -1.0, 0, -7.5)),
                    TragoulSets::buildSenseWall),
            new SimpleSet("tragoul-harvest", ACTOR_NORTH, new DemoSet.Pose(11.5, 4.0, -5.5, 90f, 14f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -2.5), new DemoSet.Sparring("COW", 0.5, 0, -5.5),
                            new DemoSet.Sparring("COW", 0.5, 0, -8.5), new DemoSet.Sparring("COW", 0.5, 0, -11.5)),
                    SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "tragoul";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildSenseWall(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x + SENSE_WALL_WEST_X, y, z + SENSE_WALL_Z,
                x + SENSE_WALL_EAST_X, y + SENSE_WALL_HEIGHT - 1, z + SENSE_WALL_Z);
    }
}
