package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;

public final class HerbalismSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final int MATURE = 7;
    private static final int YOUNG = 2;
    private static final List<Material> MEADOW_NEAR = List.of(Material.DANDELION, Material.POPPY, Material.CORNFLOWER,
            Material.AZURE_BLUET, Material.OXEYE_DAISY);
    private static final List<Material> MEADOW_FAR = List.of(Material.ALLIUM, Material.RED_TULIP, Material.BLUE_ORCHID,
            Material.ORANGE_TULIP, Material.LILY_OF_THE_VALLEY);
    private static final List<Bloom> BLOOMS = List.of(new Bloom(Material.DANDELION, -2, -5), new Bloom(Material.POPPY, 2, -6),
            new Bloom(Material.CORNFLOWER, -3, -2), new Bloom(Material.AZURE_BLUET, 3, -3), new Bloom(Material.OXEYE_DAISY, -1, -7),
            new Bloom(Material.ALLIUM, 1, -1), new Bloom(Material.RED_TULIP, 4, -5), new Bloom(Material.BLUE_ORCHID, -4, -4),
            new Bloom(Material.ORANGE_TULIP, -2, 0), new Bloom(Material.LILY_OF_THE_VALLEY, 3, 0),
            new Bloom(Material.WHITE_TULIP, 0, -8), new Bloom(Material.PINK_TULIP, -5, -1));
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("herbalism-wheat", ACTOR_NORTH, new DemoSet.Pose(8.5, 4.0, 0.5, 109.4f, 21.2f), false, List.of(),
                    HerbalismSets::buildWheat),
            new SimpleSet("herbalism-farmland", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.5, 1.5, 121.6f, 24.6f), false, List.of(),
                    (world, x, y, z) -> farmland(world, x - 2, y - 1, z - 5, x + 2, z - 1)),
            new SimpleSet("herbalism-seedlings", ACTOR_NORTH, new DemoSet.Pose(6.5, 5.0, 4.5, 142.6f, 25.0f), false, List.of(),
                    (world, x, y, z) -> field(world, x - 6, y, z - 10, x + 6, z - 1)),
            new SimpleSet("herbalism-apiary", ACTOR_NORTH, new DemoSet.Pose(6.5, 4.0, 4.5, 135.0f, 18.1f), false,
                    List.of(new DemoSet.Sparring("BEE", -8.5, 3, -9.5), new DemoSet.Sparring("BEE", 8.5, 3, -10.5),
                            new DemoSet.Sparring("BEE", -4.5, 4, -12.5), new DemoSet.Sparring("BEE", 5.5, 4, -6.5)),
                    (world, x, y, z) -> field(world, x - 12, y, z - 14, x + 12, z - 1)),
            new SimpleSet("herbalism-compost", ACTOR_NORTH, new DemoSet.Pose(6.5, 4.0, 2.5, 127.6f, 23.1f), false, List.of(),
                    HerbalismSets::buildCompost),
            new SimpleSet("herbalism-mycelium", ACTOR_NORTH, new DemoSet.Pose(7.5, 5.0, 4.5, 133.0f, 26.0f), false, List.of(),
                    HerbalismSets::buildMycelium),
            new SimpleSet("herbalism-meadow", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.0, 0.5, 107.1f, 20.2f), false, List.of(),
                    HerbalismSets::buildMeadow),
            new SimpleSet("herbalism-ledge", new DemoSet.Pose(0.5, 8, 0.5, 180f, 0f), new DemoSet.Pose(13.5, 4.5, -1.0, 90f, 0f),
                    false, List.of(), HerbalismSets::buildLedge),
            new SimpleSet("herbalism-workbench", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.4, -1.5, 72.0f, 18.4f), false, List.of(),
                    (world, x, y, z) -> world.getBlockAt(x, y, z - 1).setType(Material.CRAFTING_TABLE, false))
    );

    @Override
    public String skill() {
        return "herbalism";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildWheat(World world, int x, int y, int z) {
        farmland(world, x - 3, y - 1, z - 6, x + 3, z - 2);
        crops(world, MATURE, x - 3, y, z - 6, x + 3, z - 2);
    }

    private static void buildCompost(World world, int x, int y, int z) {
        farmland(world, x - 5, y - 1, z - 8, x + 5, z - 1);
        for (int row = 1; row <= 8; row++) {
            crops(world, row % 2 == 0 ? MATURE : YOUNG, x - 5, y, z - row, x + 5, z - row);
        }
        DemoGeometry.fill(world, Material.GRASS_BLOCK, x - 1, y - 1, z - 4, x + 1, y - 1, z - 1);
        DemoGeometry.fill(world, Material.AIR, x - 1, y, z - 4, x + 1, y, z - 1);
        world.getBlockAt(x, y, z - 3).setType(Material.COMPOSTER, false);
    }

    private static void buildMycelium(World world, int x, int y, int z) {
        world.getBlockAt(x, y - 1, z - 3).setType(Material.MYCELIUM, false);
        for (Bloom bloom : BLOOMS) {
            world.getBlockAt(x + bloom.dx(), y, z + bloom.dz()).setType(bloom.flower(), false);
        }
    }

    private static void buildMeadow(World world, int x, int y, int z) {
        for (int index = 0; index < MEADOW_NEAR.size(); index++) {
            world.getBlockAt(x - 2 + index, y, z - 2).setType(MEADOW_NEAR.get(index), false);
            world.getBlockAt(x - 2 + index, y, z - 3).setType(MEADOW_FAR.get(index), false);
        }
    }

    private static void buildLedge(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 2, y, z, x + 2, y + 7, z + 2);
        farmland(world, x - 2, y - 1, z - 4, x + 2, z - 1);
    }

    private static void field(World world, int x1, int y, int z1, int x2, int z2) {
        farmland(world, x1, y - 1, z1, x2, z2);
        crops(world, MATURE, x1, y, z1, x2, z2);
    }

    private static void farmland(World world, int x1, int y, int z1, int x2, int z2) {
        Farmland data = (Farmland) Material.FARMLAND.createBlockData();
        data.setMoisture(data.getMaximumMoisture());
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                world.getBlockAt(x, y, z).setBlockData(data, false);
            }
        }
    }

    private static void crops(World world, int age, int x1, int y, int z1, int x2, int z2) {
        Ageable data = (Ageable) Material.WHEAT.createBlockData();
        data.setAge(Math.min(age, data.getMaximumAge()));
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                world.getBlockAt(x, y, z).setBlockData(data, false);
            }
        }
    }

    private record Bloom(Material flower, int dx, int dz) {
    }
}
