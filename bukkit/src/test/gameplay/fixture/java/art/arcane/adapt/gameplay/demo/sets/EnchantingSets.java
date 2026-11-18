package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;

public final class EnchantingSets implements DemoSetProvider {
    private static final int STATION_Z = -1;
    private static final int LIBRARY_HALF_WIDTH = 2;
    private static final int LIBRARY_NORTH_Z = -3;
    private static final int LIBRARY_SOUTH_Z = 1;
    private static final int LIBRARY_BACK_HEIGHT = 1;
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_STATION = new DemoSet.Pose(3.5, 2.5, 2.0, 124f, 24f);
    private static final DemoSet.Pose CAMERA_LIBRARY = new DemoSet.Pose(4.5, 3.2, 2.5, 127f, 25f);
    private static final DemoSet.Pose CAMERA_SIPHON = new DemoSet.Pose(5.5, 3.0, 3.5, 129f, 17f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("enchanting-table", ACTOR_NORTH, CAMERA_STATION, false, List.of(), EnchantingSets::buildTable),
            new SimpleSet("enchanting-library", ACTOR_NORTH, CAMERA_LIBRARY, false, List.of(), EnchantingSets::buildLibrary),
            new SimpleSet("enchanting-anvil", ACTOR_NORTH, CAMERA_STATION, false, List.of(), EnchantingSets::buildAnvil),
            new SimpleSet("enchanting-chest", ACTOR_NORTH, CAMERA_STATION, false, List.of(), EnchantingSets::buildChest),
            new SimpleSet("enchanting-siphon", ACTOR_NORTH, CAMERA_SIPHON, false, List.of(), SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "enchanting";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildTable(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z + STATION_Z).setType(Material.ENCHANTING_TABLE, false);
    }

    private static void buildLibrary(World world, int x, int y, int z) {
        buildTable(world, x, y, z);
        DemoGeometry.fill(world, Material.BOOKSHELF, x - LIBRARY_HALF_WIDTH, y, z + LIBRARY_NORTH_Z,
                x - LIBRARY_HALF_WIDTH, y, z + LIBRARY_SOUTH_Z);
        DemoGeometry.fill(world, Material.BOOKSHELF, x + LIBRARY_HALF_WIDTH, y, z + LIBRARY_NORTH_Z,
                x + LIBRARY_HALF_WIDTH, y, z + LIBRARY_SOUTH_Z);
        DemoGeometry.fill(world, Material.BOOKSHELF, x - LIBRARY_HALF_WIDTH, y, z + LIBRARY_NORTH_Z,
                x + LIBRARY_HALF_WIDTH, y + LIBRARY_BACK_HEIGHT, z + LIBRARY_NORTH_Z);
    }

    private static void buildAnvil(World world, int x, int y, int z) {
        Directional anvil = (Directional) Material.ANVIL.createBlockData();
        anvil.setFacing(BlockFace.EAST);
        world.getBlockAt(x, y, z + STATION_Z).setBlockData(anvil, false);
    }

    private static void buildChest(World world, int x, int y, int z) {
        Chest chest = (Chest) Material.CHEST.createBlockData();
        chest.setFacing(BlockFace.SOUTH);
        world.getBlockAt(x, y, z + STATION_Z).setBlockData(chest, false);
    }
}
