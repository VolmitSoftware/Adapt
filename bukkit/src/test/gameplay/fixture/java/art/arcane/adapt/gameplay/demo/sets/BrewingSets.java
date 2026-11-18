package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class BrewingSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_STAND = new DemoSet.Pose(3.5, 2.2, -1.5, 63.4f, 21.9f);
    private static final int STAND_NORTH = 1;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("brewing-stand", ACTOR_NORTH, CAMERA_STAND, false, List.of(), BrewingSets::buildStand),
            new SimpleSet("brewing-heated", ACTOR_NORTH, CAMERA_STAND, false, List.of(), BrewingSets::buildHeatedStand)
    );

    @Override
    public String skill() {
        return "brewing";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildStand(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z - STAND_NORTH).setType(Material.BREWING_STAND, false);
    }

    private static void buildHeatedStand(World world, int x, int y, int z) {
        world.getBlockAt(x, y - 1, z - STAND_NORTH).setType(Material.LAVA, false);
        buildStand(world, x, y, z);
    }
}
