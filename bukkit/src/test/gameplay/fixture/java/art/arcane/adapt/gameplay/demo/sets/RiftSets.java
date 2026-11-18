package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.ItemStack;

public final class RiftSets implements DemoSetProvider {
    private static final int ACCESS_DIAMONDS = 4;
    private static final int CONDUIT_DIAMONDS = 7;
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose ACTOR_EAST = new DemoSet.Pose(0.5, 0, 0.5, 270f, 0f);
    private static final DemoSet.Pose ACTOR_CLEARING = new DemoSet.Pose(0.5, 0, -4.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_RECALL = new DemoSet.Pose(12.5, 4.5, -4.0, 90f, 10f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("rift-pocket", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.0, 2.5, 135f, 14f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z - 3, x + 1, y + 1, z - 3)),
            new SimpleSet("rift-rebound", ACTOR_NORTH, new DemoSet.Pose(-13.5, 5.0, -13.0, 270f, 12f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE_BRICKS, x + 4, y, z - 12, x + 4, y + 4, z - 2)),
            new SimpleSet("rift-taglock", ACTOR_NORTH, new DemoSet.Pose(8.5, 4.0, 2.5, 135f, 16f), false,
                    List.of(new DemoSet.Sparring("COW", -0.5, 0, -1.5)),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE_BRICKS, x - 3, y, z - 13, x + 5, y + 2, z - 13)),
            new SimpleSet("rift-escape", ACTOR_CLEARING, new DemoSet.Pose(9.5, 9.0, 7.5, 135f, 30f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -6.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("rift-gate", ACTOR_NORTH, CAMERA_RECALL, false, List.of(), RiftSets::buildWorkbench),
            new SimpleSet("rift-access", ACTOR_NORTH, CAMERA_RECALL, false, List.of(), RiftSets::buildAccess),
            new SimpleSet("rift-conduit", ACTOR_EAST, new DemoSet.Pose(-4.5, 3.5, -6.5, 315f, 16f), false, List.of(),
                    RiftSets::buildConduit)
    );

    @Override
    public String skill() {
        return "rift";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildWorkbench(World world, int x, int y, int z) {
        world.getBlockAt(x - 2, y, z).setType(Material.CRAFTING_TABLE, false);
    }

    private static void buildAccess(World world, int x, int y, int z) {
        buildWorkbench(world, x, y, z);
        chest(world, x + 2, y, z - 1, ACCESS_DIAMONDS);
    }

    private static void buildConduit(World world, int x, int y, int z) {
        chest(world, x + 3, y, z - 1, CONDUIT_DIAMONDS);
        chest(world, x + 3, y, z + 1, 0);
    }

    private static void chest(World world, int x, int y, int z, int diamonds) {
        Chest data = (Chest) Material.CHEST.createBlockData();
        data.setFacing(BlockFace.WEST);
        Block block = world.getBlockAt(x, y, z);
        block.setBlockData(data, false);
        if (diamonds > 0) {
            ((Container) block.getState()).getInventory().addItem(new ItemStack(Material.DIAMOND, diamonds));
        }
    }
}
