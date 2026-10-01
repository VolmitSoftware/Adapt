package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class RangedSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_LANE = new DemoSet.Pose(13.5, 3.2, -6.0, 90f, 6f);
    private static final int RANGE_TARGET_Z = -40;
    private static final int FETCH_WALL_Z = -10;
    private static final int CORRIDOR_WEST_X = -3;
    private static final int CORRIDOR_EAST_X = 4;
    private static final int CORRIDOR_NORTH_Z = -30;
    private static final int CORRIDOR_SOUTH_Z = -1;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("ranged-range", ACTOR_NORTH, new DemoSet.Pose(2.5, 2.8, 4.5, 177.4f, 4f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.TARGET, x - 1, y, z + RANGE_TARGET_Z, x + 1, y + 2, z + RANGE_TARGET_Z)),
            new SimpleSet("ranged-lane", ACTOR_NORTH, CAMERA_LANE, false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -12.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("ranged-column", ACTOR_NORTH, CAMERA_LANE, false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -6.5), new DemoSet.Sparring("ZOMBIE", 0.5, 0, -9.5),
                            new DemoSet.Sparring("ZOMBIE", 0.5, 0, -12.5)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("ranged-charge", ACTOR_NORTH, new DemoSet.Pose(15.5, 3.6, -7.5, 90f, 7f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -16.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("ranged-fetch", ACTOR_NORTH, new DemoSet.Pose(8.5, 2.8, -4.5, 90f, 10f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.TARGET, x - 1, y, z + FETCH_WALL_Z, x + 1, y + 3, z + FETCH_WALL_Z)),
            new SimpleSet("ranged-corridor", ACTOR_NORTH, new DemoSet.Pose(0.5, 8.0, 8.5, 180f, 25f), false, List.of(),
                    RangedSets::buildCorridor)
    );

    @Override
    public String skill() {
        return "ranged";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildCorridor(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x + CORRIDOR_WEST_X, y, z + CORRIDOR_NORTH_Z, x + CORRIDOR_WEST_X, y + 3, z + CORRIDOR_SOUTH_Z);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x + CORRIDOR_EAST_X, y, z + CORRIDOR_NORTH_Z, x + CORRIDOR_EAST_X, y + 3, z + CORRIDOR_SOUTH_Z);
    }
}
