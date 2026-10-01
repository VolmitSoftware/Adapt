package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;

public final class AxesSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final int WOODPILE_Z = -3;
    private static final int TREE_Z = -4;
    private static final int TRUNK_HEIGHT = 5;
    private static final int CANOPY_BOTTOM = 3;
    private static final int CANOPY_TOP = 6;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("axes-woodpile", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.0, 4.5, 135f, 13.5f), false, List.of(),
                    AxesSets::buildWoodpile),
            new SimpleSet("axes-tree", ACTOR_NORTH, new DemoSet.Pose(10.5, 3.8, -1.5, 90f, 6f), false, List.of(),
                    AxesSets::buildTree),
            new SimpleSet("axes-cleave", ACTOR_NORTH, new DemoSet.Pose(7.5, 3.5, 5.5, 135f, 14f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -1.5), new DemoSet.Sparring("ZOMBIE", -1.2, 0, -2.2),
                            new DemoSet.Sparring("ZOMBIE", 2.2, 0, -2.2)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("axes-smash", ACTOR_NORTH, new DemoSet.Pose(9.5, 6.0, 7.5, 133.4f, 24f), false,
                    List.of(new DemoSet.Sparring("COW", -2.5, 0, -2.5), new DemoSet.Sparring("COW", -0.5, 0, -4.0),
                            new DemoSet.Sparring("COW", 1.5, 0, -4.0), new DemoSet.Sparring("COW", 3.5, 0, -2.5)),
                    SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "axes";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildWoodpile(World world, int x, int y, int z) {
        Orientable log = (Orientable) Material.OAK_LOG.createBlockData();
        log.setAxis(Axis.X);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                world.getBlockAt(x + dx, y + dy, z + WOODPILE_Z).setBlockData(log, false);
            }
        }
    }

    private static void buildTree(World world, int x, int y, int z) {
        Leaves leaves = (Leaves) Material.OAK_LEAVES.createBlockData();
        leaves.setPersistent(true);
        int trunkZ = z + TREE_Z;
        for (int dy = CANOPY_BOTTOM; dy <= CANOPY_TOP; dy++) {
            int radius = dy < TRUNK_HEIGHT ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (canopyCorner(dy, radius, dx, dz)) {
                        continue;
                    }
                    world.getBlockAt(x + dx, y + dy, trunkZ + dz).setBlockData(leaves, false);
                }
            }
        }
        DemoGeometry.fill(world, Material.OAK_LOG, x, y, trunkZ, x, y + TRUNK_HEIGHT - 1, trunkZ);
    }

    private static boolean canopyCorner(int dy, int radius, int dx, int dz) {
        return Math.abs(dx) == radius && Math.abs(dz) == radius && (radius == 2 || dy == CANOPY_TOP);
    }
}
