package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class NetherSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose ACTOR_EAST = new DemoSet.Pose(0.5, 0, 0.5, -90f, 0f);
    private static final DemoSet.Pose CAMERA_SIDE = new DemoSet.Pose(6.5, 2.4, 0.5, 90f, 6f);
    private static final DemoSet.Pose CAMERA_FOLLOW = new DemoSet.Pose(3.0, 1.2, 4.5, -33.7f, 12.5f);
    private static final DemoSet.Pose CAMERA_HEARTH = new DemoSet.Pose(5.5, 2.6, 2.5, 128.7f, 14f);
    private static final DemoSet.Pose CAMERA_BRAWL = new DemoSet.Pose(5.5, 2.8, 3.5, 144f, 14.8f);
    private static final DemoSet.Pose CAMERA_BLAST = new DemoSet.Pose(9.5, 3.0, 0.5, 99.5f, 12.4f);
    private static final DemoSet.Pose CAMERA_BARTER = new DemoSet.Pose(8.5, 4.0, -0.5, 90f, 20.6f);
    private static final DemoSet.Pose CAMERA_RANGE = new DemoSet.Pose(9.5, 3.5, -6.5, 90f, 12.5f);
    private static final DemoSet.Pose CAMERA_STRIDER = new DemoSet.Pose(7.5, 3.5, 1.5, 116.6f, 15.6f);
    private static final DemoSet.Pose CAMERA_WITHER = new DemoSet.Pose(6.5, 2.8, 5.5, 140.7f, 12.7f);
    private static final DemoSet.Pose CAMERA_HARVEST = new DemoSet.Pose(5.5, 2.8, 2.5, 125f, 16.4f);
    private static final DemoSet.Pose CAMERA_FEAST = new DemoSet.Pose(1.5, 2.6, -7.0, 8.1f, 9.6f);
    private static final int PATH_NORTH = 30;
    private static final int POOL_NORTH = 16;
    private static final int TNT_NORTH = 3;
    private static final int BLAST_PAD = 7;
    private static final int RANGE_NORTH = 14;
    private static final int RANGE_HEIGHT = 4;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("nether-soul", ACTOR_NORTH, CAMERA_FOLLOW, true, List.of(), NetherSets::buildSoulPath),
            new SimpleSet("nether-soulfire", ACTOR_NORTH, CAMERA_SIDE, false, List.of(),
                    (world, x, y, z) -> buildHearth(world, x, y, z, Material.SOUL_SOIL, Material.SOUL_FIRE)),
            new SimpleSet("nether-fire", ACTOR_NORTH, CAMERA_SIDE, false, List.of(),
                    (world, x, y, z) -> buildHearth(world, x, y, z, Material.NETHERRACK, Material.FIRE)),
            new SimpleSet("nether-lava", ACTOR_NORTH, CAMERA_FOLLOW, true, List.of(), NetherSets::buildLavaPool),
            new SimpleSet("nether-quarry", ACTOR_NORTH, CAMERA_SIDE, false, List.of(), NetherSets::buildQuarry),
            new SimpleSet("nether-magma", ACTOR_NORTH, CAMERA_HEARTH, false, List.of(), NetherSets::buildMagmaPad),
            new SimpleSet("nether-brawl", ACTOR_NORTH, CAMERA_BRAWL, false, List.of(new DemoSet.Sparring("PIGLIN", 2.5, 0, -2.0)),
                    (world, x, y, z) -> buildHearth(world, x, y, z, Material.NETHERRACK, Material.FIRE)),
            new SimpleSet("nether-blast", ACTOR_NORTH, CAMERA_BLAST, false, List.of(), NetherSets::buildBlastPad),
            new SimpleSet("nether-barter", ACTOR_NORTH, CAMERA_BARTER, false,
                    List.of(new DemoSet.Sparring("PIGLIN", -1.0, 0, -1.5), new DemoSet.Sparring("PIGLIN", 0.5, 0, -2.0),
                            new DemoSet.Sparring("PIGLIN", 2.0, 0, -1.5)),
                    NetherSets::buildBarterFloor),
            new SimpleSet("nether-skull", ACTOR_NORTH, CAMERA_RANGE, false, List.of(), NetherSets::buildSkullWall),
            new SimpleSet("nether-strider", ACTOR_NORTH, CAMERA_STRIDER, false, List.of(new DemoSet.Sparring("STRIDER", 0.5, 0, -2.5)),
                    NetherSets::buildLavaPool),
            new SimpleSet("nether-wither", ACTOR_EAST, CAMERA_WITHER, false, List.of(), NetherSets::buildWitherBed),
            new SimpleSet("nether-harvest", ACTOR_NORTH, CAMERA_HARVEST, false, List.of(new DemoSet.Sparring("WITHER_SKELETON", 0.5, 0, -2.0)),
                    NetherSets::buildFortressFloor),
            new SimpleSet("nether-feast", ACTOR_NORTH, CAMERA_FEAST, false, List.of(), NetherSets::buildFungusBeds)
    );

    @Override
    public String skill() {
        return "nether";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildSoulPath(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.SOUL_SOIL, x - 2, y - 1, z - PATH_NORTH, x + 2, y - 1, z + 1);
        DemoGeometry.fill(world, Material.POLISHED_BLACKSTONE_BRICKS, x - 3, y - 1, z - PATH_NORTH, x - 3, y - 1, z + 1);
        DemoGeometry.fill(world, Material.POLISHED_BLACKSTONE_BRICKS, x + 3, y - 1, z - PATH_NORTH, x + 3, y - 1, z + 1);
        DemoGeometry.fill(world, Material.BASALT, x - 3, y, z - PATH_NORTH, x - 3, y + 2, z - PATH_NORTH);
        DemoGeometry.fill(world, Material.BASALT, x + 3, y, z - PATH_NORTH, x + 3, y + 2, z - PATH_NORTH);
    }

    private static void buildHearth(World world, int x, int y, int z, Material base, Material flame) {
        DemoGeometry.fill(world, Material.POLISHED_BLACKSTONE, x - 1, y - 1, z - 3, x + 1, y - 1, z - 1);
        DemoGeometry.fill(world, base, x, y - 1, z - 2, x, y - 1, z - 2);
        DemoGeometry.fill(world, flame, x, y, z - 2, x, y, z - 2);
    }

    private static void buildLavaPool(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.BLACKSTONE, x - 3, y - 1, z - POOL_NORTH - 1, x + 3, y - 1, z);
        DemoGeometry.fill(world, Material.LAVA, x - 2, y - 1, z - POOL_NORTH, x + 2, y - 1, z - 1);
        DemoGeometry.fill(world, Material.BASALT, x - 3, y, z - POOL_NORTH - 1, x - 3, y + 1, z - POOL_NORTH - 1);
        DemoGeometry.fill(world, Material.BASALT, x + 3, y, z - POOL_NORTH - 1, x + 3, y + 1, z - POOL_NORTH - 1);
    }

    private static void buildQuarry(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.BLACKSTONE, x - 2, y, z - 1, x + 2, y + 1, z - 1);
        DemoGeometry.fill(world, Material.BASALT, x - 2, y, z - 2, x + 2, y + 1, z - 2);
        DemoGeometry.fill(world, Material.NETHERRACK, x - 2, y, z - 3, x + 2, y + 1, z - 3);
    }

    private static void buildMagmaPad(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.POLISHED_BLACKSTONE, x - 2, y - 1, z - 4, x + 2, y - 1, z);
        DemoGeometry.fill(world, Material.MAGMA_BLOCK, x - 1, y - 1, z - 3, x + 1, y - 1, z - 1);
    }

    private static void buildBlastPad(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.OBSIDIAN, x - BLAST_PAD, y - 1, z - TNT_NORTH - BLAST_PAD, x + BLAST_PAD, y - 1, z - TNT_NORTH + BLAST_PAD);
        DemoGeometry.fill(world, Material.TNT, x, y, z - TNT_NORTH, x, y, z - TNT_NORTH);
    }

    private static void buildBarterFloor(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.POLISHED_BLACKSTONE_BRICKS, x - 2, y - 1, z - 3, x + 3, y - 1, z - 1);
        DemoGeometry.fill(world, Material.GILDED_BLACKSTONE, x - 2, y - 1, z - 3, x - 2, y - 1, z - 3);
        DemoGeometry.fill(world, Material.GILDED_BLACKSTONE, x + 3, y - 1, z - 3, x + 3, y - 1, z - 3);
    }

    private static void buildSkullWall(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.POLISHED_BLACKSTONE_BRICKS, x - 3, y, z - RANGE_NORTH, x + 3, y + RANGE_HEIGHT, z - RANGE_NORTH);
        DemoGeometry.fill(world, Material.BASALT, x - 4, y, z - RANGE_NORTH, x - 4, y + RANGE_HEIGHT, z - RANGE_NORTH);
        DemoGeometry.fill(world, Material.BASALT, x + 4, y, z - RANGE_NORTH, x + 4, y + RANGE_HEIGHT, z - RANGE_NORTH);
    }

    private static void buildWitherBed(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.POLISHED_BLACKSTONE, x + 1, y - 1, z - 2, x + 5, y - 1, z + 2);
        DemoGeometry.fill(world, Material.SOUL_SOIL, x + 2, y - 1, z - 1, x + 4, y - 1, z + 1);
        DemoGeometry.fill(world, Material.WITHER_ROSE, x + 2, y, z - 1, x + 4, y, z + 1);
    }

    private static void buildFortressFloor(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.NETHER_BRICKS, x - 2, y - 1, z - 4, x + 2, y - 1, z - 1);
        DemoGeometry.fill(world, Material.NETHER_BRICK_FENCE, x - 2, y, z - 4, x - 2, y + 1, z - 4);
        DemoGeometry.fill(world, Material.NETHER_BRICK_FENCE, x + 2, y, z - 4, x + 2, y + 1, z - 4);
    }

    private static void buildFungusBeds(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.CRIMSON_NYLIUM, x - 4, y - 1, z - 4, x - 2, y - 1, z - 2);
        DemoGeometry.fill(world, Material.WARPED_NYLIUM, x + 2, y - 1, z - 4, x + 4, y - 1, z - 2);
        world.getBlockAt(x - 3, y, z - 3).setType(Material.CRIMSON_FUNGUS, false);
        world.getBlockAt(x - 4, y, z - 2).setType(Material.CRIMSON_ROOTS, false);
        world.getBlockAt(x - 2, y, z - 4).setType(Material.CRIMSON_ROOTS, false);
        world.getBlockAt(x - 4, y, z - 4).setType(Material.CRIMSON_ROOTS, false);
        world.getBlockAt(x + 3, y, z - 3).setType(Material.WARPED_FUNGUS, false);
        world.getBlockAt(x + 4, y, z - 2).setType(Material.WARPED_ROOTS, false);
        world.getBlockAt(x + 2, y, z - 4).setType(Material.WARPED_ROOTS, false);
        world.getBlockAt(x + 4, y, z - 4).setType(Material.WARPED_ROOTS, false);
    }
}
