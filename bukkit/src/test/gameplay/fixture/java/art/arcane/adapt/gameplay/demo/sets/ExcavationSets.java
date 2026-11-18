package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class ExcavationSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_MOUND = new DemoSet.Pose(6.5, 3.5, 5.5, 143.1f, 13.0f);
    private static final List<int[]> OUTCROP_ORES = List.of(new int[]{-3, 1, -5}, new int[]{2, 0, -6}, new int[]{0, 1, -7},
            new int[]{4, 1, -4}, new int[]{-1, 0, -4});
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("excavation-shaft", new DemoSet.Pose(0.5, 8, 0.5, 180f, 0f), new DemoSet.Pose(12.5, 4.8, 0.5, 90f, 0f), false,
                    List.of(), ExcavationSets::buildShaft),
            new SimpleSet("excavation-bank", ACTOR_NORTH, new DemoSet.Pose(5.5, 3.2, 4.5, 137.7f, 12.9f), false, List.of(),
                    ExcavationSets::buildBank),
            new SimpleSet("excavation-mound", ACTOR_NORTH, CAMERA_MOUND, false, List.of(), ExcavationSets::buildMound),
            new SimpleSet("excavation-seismic", ACTOR_NORTH, CAMERA_MOUND, false, List.of(), ExcavationSets::buildSeismic),
            new SimpleSet("excavation-dig-site", ACTOR_NORTH, new DemoSet.Pose(5.5, 5.0, 4.5, 144.5f, 34.9f), false, List.of(),
                    ExcavationSets::buildDigSite),
            new SimpleSet("excavation-mudflat", new DemoSet.Pose(0.5, -1, 0.5, 180f, 0f), new DemoSet.Pose(5.5, 2.4, 4.5, 140.2f, 13.7f),
                    false, List.of(), ExcavationSets::buildMudflat),
            new SimpleSet("excavation-horde", ACTOR_NORTH, new DemoSet.Pose(9.5, 4.0, 6.5, 138.0f, 13.4f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", -2.5, 0, -3.5), new DemoSet.Sparring("ZOMBIE", 0.5, 0, -4.5),
                            new DemoSet.Sparring("ZOMBIE", 3.5, 0, -3.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("excavation-outcrop", ACTOR_NORTH, new DemoSet.Pose(7.5, 3.5, 4.5, 143.6f, 9.6f), false, List.of(),
                    ExcavationSets::buildOutcrop),
            new SimpleSet("excavation-workbench", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.6, 3.5, 135.0f, 17.0f), false, List.of(),
                    ExcavationSets::buildWorkbench)
    );

    @Override
    public String skill() {
        return "excavation";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildShaft(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE, x - 1, y, z - 1, x + 1, y, z + 1);
        DemoGeometry.fill(world, Material.DIRT, x - 1, y + 1, z - 1, x + 1, y + 6, z + 1);
        DemoGeometry.fill(world, Material.GRASS_BLOCK, x - 1, y + 7, z - 1, x + 1, y + 7, z + 1);
        DemoGeometry.fill(world, Material.GLASS, x + 1, y, z - 1, x + 1, y + 7, z + 1);
        world.getBlockAt(x, y, z).setType(Material.LAVA, false);
    }

    private static void buildBank(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.CLAY, x - 2, y, z - 7, x + 2, y + 2, z - 1);
        DemoGeometry.fill(world, Material.GRASS_BLOCK, x - 2, y + 3, z - 7, x + 2, y + 3, z - 1);
    }

    private static void buildMound(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.DIRT, x - 3, y, z - 7, x + 3, y + 1, z - 2);
        DemoGeometry.fill(world, Material.PODZOL, x - 3, y + 2, z - 7, x + 3, y + 2, z - 2);
    }

    private static void buildSeismic(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.DIRT, x - 2, y, z - 6, x + 2, y + 1, z - 2);
        DemoGeometry.fill(world, Material.GRASS_BLOCK, x - 2, y + 2, z - 6, x + 2, y + 2, z - 2);
        world.getBlockAt(x, y, z - 5).setType(Material.GOLD_ORE, false);
    }

    private static void buildDigSite(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.SANDSTONE, x - 3, y - 4, z - 5, x + 3, y - 4, z - 1);
        DemoGeometry.fill(world, Material.SAND, x - 3, y - 3, z - 5, x + 3, y - 1, z - 1);
    }

    private static void buildMudflat(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.DIRT, x - 1, y - 2, z - 1, x + 1, y - 2, z + 1);
        DemoGeometry.fill(world, Material.WATER, x - 1, y - 1, z - 1, x + 1, y - 1, z + 1);
        DemoGeometry.fill(world, Material.MUD, x - 2, y, z - 5, x + 2, y + 2, z - 2);
    }

    private static void buildOutcrop(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE, x - 5, y, z - 8, x + 5, y + 1, z - 3);
        DemoGeometry.fill(world, Material.DIRT, x - 5, y + 2, z - 8, x + 5, y + 2, z - 3);
        DemoGeometry.fill(world, Material.GRASS_BLOCK, x - 5, y + 3, z - 8, x + 5, y + 3, z - 3);
        for (int[] ore : OUTCROP_ORES) {
            world.getBlockAt(x + ore[0], y + ore[1], z + ore[2]).setType(Material.DIAMOND_ORE, false);
        }
    }

    private static void buildWorkbench(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z - 1).setType(Material.CRAFTING_TABLE, false);
        DemoGeometry.fill(world, Material.STONE, x - 2, y, z - 2, x + 2, y, z - 2);
        DemoGeometry.fill(world, Material.STONE, x - 3, y, z - 3, x, y, z - 3);
        world.getBlockAt(x - 2, y + 1, z - 2).setType(Material.STONE, false);
        world.getBlockAt(x - 1, y + 1, z - 2).setType(Material.OAK_LOG, false);
        world.getBlockAt(x, y + 1, z - 2).setType(Material.DIRT, false);
        world.getBlockAt(x + 1, y + 1, z - 2).setType(Material.COBWEB, false);
        world.getBlockAt(x + 2, y, z - 2).setType(Material.GRASS_BLOCK, false);
        world.getBlockAt(x - 3, y + 1, z - 3).setType(Material.IRON_ORE, false);
        world.getBlockAt(x - 1, y + 1, z - 3).setType(Material.OAK_PLANKS, false);
        world.getBlockAt(x, y + 1, z - 3).setType(Material.SAND, false);
    }
}
