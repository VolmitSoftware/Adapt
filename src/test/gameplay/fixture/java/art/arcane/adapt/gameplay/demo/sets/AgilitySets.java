package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

public final class AgilitySets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_ARENA = new DemoSet.Pose(6.5, 2.4, 0.5, 90f, 6f);
    private static final DemoSet.Pose CAMERA_SPARRING = new DemoSet.Pose(10.5, 3.0, -1.0, 90f, 10f);
    private static final DemoSet.Pose CAMERA_RUNWAY = new DemoSet.Pose(3.0, 1.2, 4.5, -33.7f, 12.5f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("arena", ACTOR_NORTH, CAMERA_ARENA, false, List.of(), SimpleSet.OPEN_PLATE),
            new SimpleSet("runway", ACTOR_NORTH, CAMERA_RUNWAY, true, List.of(), SimpleSet.OPEN_PLATE),
            new SimpleSet("corridor", ACTOR_NORTH, new DemoSet.Pose(1.0, 6.0, -17.5, 0f, -10f), false, List.of(),
                    AgilitySets::buildCorridor),
            new SimpleSet("ladder", ACTOR_NORTH, new DemoSet.Pose(0.5, 4.5, 11.5, 180f, -8f), false, List.of(),
                    AgilitySets::buildLadder),
            new SimpleSet("ledge", new DemoSet.Pose(0.5, 8, 0.5, 180f, 0f), new DemoSet.Pose(13.5, 4.5, -1.0, 90f, 0f), false,
                    List.of(), (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE_BRICKS, x - 2, y, z, x + 2, y + 7, z + 2)),
            new SimpleSet("fence", ACTOR_NORTH, new DemoSet.Pose(10.5, 2.6, -3.0, 90f, 8f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fenceRow(world, Material.OAK_FENCE, x - 3, x + 3, y, z - 3)),
            new SimpleSet("pressure", ACTOR_NORTH, new DemoSet.Pose(14.0, 7.0, -14.0, 90f, 22f), false, List.of(),
                    AgilitySets::buildPressure),
            new SimpleSet("sparring", ACTOR_NORTH, CAMERA_SPARRING, false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -2.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("archery", ACTOR_NORTH, new DemoSet.Pose(14.5, 3.4, -5.5, 90f, 6f), false,
                    List.of(new DemoSet.Sparring("SKELETON", 0.5, 0, -12.5)), SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "agility";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildLadder(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x, y, z - 1, x, y + 20, z - 1);
        for (int dy = 0; dy <= 20; dy++) {
            DemoGeometry.ladder(world, x, y + dy, z, BlockFace.SOUTH);
        }
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z, x - 1, y + 3, z);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x + 1, y, z, x + 1, y + 3, z);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y + 3, z + 1, x + 1, y + 3, z + 1);
    }

    private static void buildCorridor(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z - 4, x - 1, y + 19, z + 4);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x + 2, y, z - 4, x + 2, y + 19, z + 4);
    }

    private static void buildPressure(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.REDSTONE_LAMP, x - 1, y, z - 26, x + 1, y, z - 2);
        DemoGeometry.fill(world, Material.STONE_PRESSURE_PLATE, x - 1, y + 1, z - 26, x + 1, y + 1, z - 2);
        DemoGeometry.fill(world, Material.SMOOTH_STONE_SLAB, x - 1, y, z - 1, x + 1, y, z - 1);
        DemoGeometry.fill(world, Material.SMOOTH_STONE_SLAB, x - 1, y, z - 27, x + 1, y, z - 27);
    }
}
