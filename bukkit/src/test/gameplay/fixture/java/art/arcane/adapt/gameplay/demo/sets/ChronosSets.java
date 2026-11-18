package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

public final class ChronosSets implements DemoSetProvider {
    private static final int WORKSHOP_RADIUS = 8;
    private static final int FURNACE_SOUTH_Z = -2;
    private static final int CROP_NORTH_Z = 2;
    private static final int WORKSHOP_CROP_AGE = 0;
    private static final int FURNACE_STOCK = 64;
    private static final int GARDEN_HALF_WIDTH = 2;
    private static final int GARDEN_NEAR_Z = -2;
    private static final int GARDEN_FAR_Z = -3;
    private static final int GARDEN_CROP_AGE = 1;
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose ACTOR_RECALL = new DemoSet.Pose(0.5, 0, 0.5, 180f, 40f);
    private static final List<DemoSet.Sparring> HORDE = List.of(
            new DemoSet.Sparring("ZOMBIE", -2.5, 0, -10.5),
            new DemoSet.Sparring("ZOMBIE", 0.5, 0, -12.0),
            new DemoSet.Sparring("ZOMBIE", 3.5, 0, -10.5)
    );
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("chronos-workshop", ACTOR_NORTH, new DemoSet.Pose(11.5, 7.0, 9.5, 129.3f, 24.6f), false, List.of(),
                    ChronosSets::buildWorkshop),
            new SimpleSet("chronos-garden", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.5, 2.0, 123.7f, 24.0f), false, List.of(),
                    ChronosSets::buildGarden),
            new SimpleSet("chronos-recall", ACTOR_RECALL, new DemoSet.Pose(9.5, 2.6, -2.0, 90f, 8f), false, List.of(),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("chronos-horde", ACTOR_NORTH, new DemoSet.Pose(12.5, 4.5, -4.5, 90f, 12f), false, HORDE,
                    SimpleSet.OPEN_PLATE)
    );

    @Override
    public String skill() {
        return "chronos";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildWorkshop(World world, int x, int y, int z) {
        for (int dx = -WORKSHOP_RADIUS; dx <= WORKSHOP_RADIUS; dx++) {
            for (int dz = -WORKSHOP_RADIUS; dz <= FURNACE_SOUTH_Z; dz++) {
                litFurnace(world.getBlockAt(x + dx, y, z + dz));
            }
            for (int dz = CROP_NORTH_Z; dz <= WORKSHOP_RADIUS; dz++) {
                crop(world, x + dx, y, z + dz, WORKSHOP_CROP_AGE);
            }
        }
    }

    private static void buildGarden(World world, int x, int y, int z) {
        for (int dx = -GARDEN_HALF_WIDTH; dx <= GARDEN_HALF_WIDTH; dx++) {
            for (int dz = GARDEN_FAR_Z; dz <= GARDEN_NEAR_Z; dz++) {
                crop(world, x + dx, y, z + dz, GARDEN_CROP_AGE);
            }
        }
    }

    private static void litFurnace(Block block) {
        Directional data = (Directional) Material.FURNACE.createBlockData();
        data.setFacing(BlockFace.SOUTH);
        block.setBlockData(data, false);
        FurnaceInventory inventory = ((Furnace) block.getState()).getInventory();
        inventory.setSmelting(new ItemStack(Material.RAW_IRON, FURNACE_STOCK));
        inventory.setFuel(new ItemStack(Material.COAL, FURNACE_STOCK));
    }

    private static void crop(World world, int x, int y, int z, int age) {
        Farmland soil = (Farmland) Material.FARMLAND.createBlockData();
        soil.setMoisture(soil.getMaximumMoisture());
        world.getBlockAt(x, y - 1, z).setBlockData(soil, false);
        Ageable wheat = (Ageable) Material.WHEAT.createBlockData();
        wheat.setAge(Math.min(age, wheat.getMaximumAge()));
        world.getBlockAt(x, y, z).setBlockData(wheat, false);
    }
}
