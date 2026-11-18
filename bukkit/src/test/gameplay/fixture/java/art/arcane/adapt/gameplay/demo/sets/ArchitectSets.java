package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class ArchitectSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final int LEDGE_HEIGHT = 4;
    private static final int LEDGE_HALF_WIDTH = 2;
    private static final int LEDGE_DEPTH = 3;
    private static final int WINDOW_Z = -2;
    private static final int WINDOW_HALF_WIDTH = 2;
    private static final int WINDOW_TOP = 2;
    private static final int LOG_Z = -2;
    private static final int PLANK_WALL_Z = -3;
    private static final int PLANK_WALL_TOP = 2;
    private static final int ELEVATOR_PIT_Z = -1;
    private static final int ELEVATOR_WALL_Z = -2;
    private static final int ELEVATOR_WALL_TOP = 3;
    private static final int SIGNAL_Z = -3;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("architect-yard", ACTOR_NORTH, new DemoSet.Pose(5.5, 3.0, 3.0, 128.7f, 21.3f), false, List.of(),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("architect-lawn", ACTOR_NORTH, new DemoSet.Pose(6.5, 5.5, 3.5, 135.0f, 33.0f), false, List.of(),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("architect-cutter", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.4, -3.5, 45.0f, 13.9f), false, List.of(),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("architect-window", ACTOR_NORTH, new DemoSet.Pose(5.5, 2.8, 2.5, 125.0f, 16.4f), false, List.of(),
                    ArchitectSets::buildWindow),
            new SimpleSet("architect-logs", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.6, 2.5, 135.0f, 15.8f), false, List.of(),
                    ArchitectSets::buildLogs),
            new SimpleSet("architect-planks", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.5, 3.0, 126.9f, 17.1f), false, List.of(),
                    ArchitectSets::buildPlankWall),
            new SimpleSet("architect-ledge", new DemoSet.Pose(0.5, LEDGE_HEIGHT, 0.5, 180f, 0f), new DemoSet.Pose(9.5, 5.5, -1.0, 90.0f, 9.5f),
                    false, List.of(), ArchitectSets::buildLedge),
            new SimpleSet("architect-elevator", ACTOR_NORTH, new DemoSet.Pose(6.5, 4.0, 3.0, 123.7f, 15.5f), false, List.of(),
                    ArchitectSets::buildElevatorShaft),
            new SimpleSet("architect-signal", ACTOR_NORTH, new DemoSet.Pose(8.5, 3.0, 0.5, 93.6f, 12.7f), false, List.of(),
                    ArchitectSets::buildSignal)
    );

    @Override
    public String skill() {
        return "architect";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildWindow(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - WINDOW_HALF_WIDTH, y, z + WINDOW_Z, x + WINDOW_HALF_WIDTH, y + WINDOW_TOP,
                z + WINDOW_Z);
        DemoGeometry.fill(world, Material.GLASS, x - 1, y, z + WINDOW_Z, x + 1, y + WINDOW_TOP - 1, z + WINDOW_Z);
    }

    private static void buildLogs(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.OAK_LOG, x, y, z + LOG_Z, x, y + 1, z + LOG_Z);
    }

    private static void buildPlankWall(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.OAK_PLANKS, x - 1, y, z + PLANK_WALL_Z, x + 1, y + PLANK_WALL_TOP, z + PLANK_WALL_Z);
    }

    private static void buildLedge(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - LEDGE_HALF_WIDTH, y, z, x + LEDGE_HALF_WIDTH, y + LEDGE_HEIGHT - 1, z + LEDGE_DEPTH);
    }

    private static void buildElevatorShaft(World world, int x, int y, int z) {
        world.getBlockAt(x, y - 1, z + ELEVATOR_PIT_Z).setType(Material.AIR, false);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z + ELEVATOR_WALL_Z, x + 1, y + ELEVATOR_WALL_TOP, z + ELEVATOR_WALL_Z);
    }

    private static void buildSignal(World world, int x, int y, int z) {
        world.getBlockAt(x, y + 1, z + SIGNAL_Z).setType(Material.STONE_BRICKS, false);
        world.getBlockAt(x, y, z + SIGNAL_Z).setType(Material.REDSTONE_LAMP, false);
        world.getBlockAt(x, y + 2, z + SIGNAL_Z).setType(Material.REDSTONE_LAMP, false);
        world.getBlockAt(x - 1, y + 1, z + SIGNAL_Z).setType(Material.REDSTONE_LAMP, false);
        world.getBlockAt(x + 1, y + 1, z + SIGNAL_Z).setType(Material.REDSTONE_LAMP, false);
        world.getBlockAt(x, y + 1, z + SIGNAL_Z - 1).setType(Material.REDSTONE_LAMP, false);
    }
}
