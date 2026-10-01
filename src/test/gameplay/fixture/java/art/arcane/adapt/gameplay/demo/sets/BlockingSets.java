package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class BlockingSets implements DemoSetProvider {
    private static final int CACTUS_Z = -5;
    private static final int CACTUS_HEIGHT = 2;
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("blocking-volley", ACTOR_NORTH, new DemoSet.Pose(10.5, 3.0, -3.5, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("SKELETON", 0.5, 0, -7.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("blocking-rearguard", ACTOR_NORTH, new DemoSet.Pose(10.5, 3.0, 4.5, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("SKELETON", 0.5, 0, 8.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("blocking-bash", ACTOR_NORTH, new DemoSet.Pose(10.5, 3.4, -1.5, 90f, 10f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -2.5), new DemoSet.Sparring("ZOMBIE", -1.5, 0, -3.5),
                            new DemoSet.Sparring("ZOMBIE", 2.5, 0, -3.5)),
                    SimpleSet.OPEN_PLATE),
            new OpponentSet("blocking-wall", new DemoSet.Pose(1.5, 0, -0.5, 180f, 0f), new DemoSet.Pose(0.5, 0, 1.5, 180f, 0f),
                    new DemoSet.Pose(10.5, 3.0, -2.5, 90f, 8f), List.of(new DemoSet.Sparring("SKELETON", 0.5, 0, -6.5)),
                    SimpleSet.OPEN_PLATE),
            new OpponentSet("blocking-cactus", ACTOR_NORTH, new DemoSet.Pose(0.5, 0, -2.0, 180f, 0f),
                    new DemoSet.Pose(8.5, 2.8, -2.0, 90f, 8f), List.of(), BlockingSets::buildCactus)
    );

    @Override
    public String skill() {
        return "blocking";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildCactus(World world, int x, int y, int z) {
        world.getBlockAt(x, y - 1, z + CACTUS_Z).setType(Material.SAND, false);
        DemoGeometry.fill(world, Material.CACTUS, x, y, z + CACTUS_Z, x, y + CACTUS_HEIGHT - 1, z + CACTUS_Z);
    }

    private record OpponentSet(String name, DemoSet.Pose actorStart, DemoSet.Pose opponentStart, DemoSet.Pose camera,
            List<DemoSet.Sparring> sparring, SimpleSet.Builder builder) implements DemoSet {
        @Override
        public void build(World world, int originX, int originY, int originZ) {
            builder.build(world, originX, originY, originZ);
        }
    }
}
