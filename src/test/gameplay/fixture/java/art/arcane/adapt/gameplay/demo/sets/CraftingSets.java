package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Campfire;

public final class CraftingSets implements DemoSetProvider {
    private static final int STATION_NORTH = 1;
    private static final int PAD_HALF_WIDTH = 2;
    private static final int PAD_NEAR_NORTH = 1;
    private static final int PAD_FAR_NORTH = 3;
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_BENCH = new DemoSet.Pose(3.5, 2.7, -2.5, 50.2f, 22.3f);
    private static final DemoSet.Pose CAMERA_CAMPFIRE = new DemoSet.Pose(3.5, 2.7, -2.5, 50.2f, 23.5f);
    private static final DemoSet.Pose CAMERA_SALVAGE = new DemoSet.Pose(3.5, 2.0, 0.0, 108.4f, 25.4f);
    private static final DemoSet.Pose CAMERA_OPEN = new DemoSet.Pose(3.0, 2.4, -3.0, 42.8f, 12.3f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("crafting-bench", ACTOR_NORTH, CAMERA_BENCH, false, List.of(), CraftingSets::buildBench),
            new SimpleSet("crafting-campfire", ACTOR_NORTH, CAMERA_CAMPFIRE, false, List.of(), CraftingSets::buildCampfire),
            new SimpleSet("crafting-salvage", ACTOR_NORTH, CAMERA_SALVAGE, false, List.of(), CraftingSets::buildSalvage),
            new SimpleSet("crafting-open", ACTOR_NORTH, CAMERA_OPEN, false, List.of(), SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "crafting";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildBench(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z - STATION_NORTH).setType(Material.CRAFTING_TABLE, false);
    }

    private static void buildCampfire(World world, int x, int y, int z) {
        Campfire campfire = (Campfire) Material.CAMPFIRE.createBlockData();
        campfire.setLit(true);
        campfire.setSignalFire(false);
        world.getBlockAt(x, y, z - STATION_NORTH).setBlockData(campfire, false);
    }

    private static void buildSalvage(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.SMOOTH_STONE, x - PAD_HALF_WIDTH, y - 1, z - PAD_FAR_NORTH,
                x + PAD_HALF_WIDTH, y - 1, z - PAD_NEAR_NORTH);
    }
}
