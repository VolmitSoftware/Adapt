package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class UnarmedSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_DUMMY = new DemoSet.Pose(5.5, 2.4, -0.75, 90f, 12f);
    private static final List<DemoSet.Sparring> ZOMBIE_IN_REACH = List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -2.0));
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("unarmed-dummy", ACTOR_NORTH, CAMERA_DUMMY, false, ZOMBIE_IN_REACH, SimpleSet.OPEN_PLATE),
            new SimpleSet("unarmed-fists", ACTOR_NORTH, CAMERA_DUMMY, false, ZOMBIE_IN_REACH, UnarmedSets::buildSoftPile),
            new SimpleSet("unarmed-throw", ACTOR_NORTH, new DemoSet.Pose(9.5, 3.2, -6.0, 90f, 10f), false, ZOMBIE_IN_REACH,
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("unarmed-lane", ACTOR_NORTH, new DemoSet.Pose(9.5, 3.5, -8.0, 90f, 12f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -6.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("unarmed-trio", ACTOR_NORTH, new DemoSet.Pose(8.5, 4.5, 2.5, 135f, 16f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -3.5), new DemoSet.Sparring("ZOMBIE", -2.0, 0, -3.0),
                            new DemoSet.Sparring("ZOMBIE", 3.0, 0, -3.0)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("unarmed-calm", ACTOR_NORTH, new DemoSet.Pose(3.0, 1.9, -1.8, 47f, 18f), false, List.of(),
                    SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "unarmed";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildSoftPile(World world, int x, int y, int z) {
        world.getBlockAt(x - 2, y, z + 1).setType(Material.DIRT, false);
        world.getBlockAt(x - 2, y + 1, z + 1).setType(Material.SAND, false);
    }
}
