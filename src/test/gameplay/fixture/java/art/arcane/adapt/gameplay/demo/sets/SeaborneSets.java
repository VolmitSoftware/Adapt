package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;

public final class SeaborneSets implements DemoSetProvider {
    private static final int POOL_HALF_WIDTH = 3;
    private static final int POOL_LENGTH = 30;
    private static final int POOL_DEPTH = 3;
    private static final int SHAFT_HALF_WIDTH = 2;
    private static final int SHAFT_NORTH = -7;
    private static final int SHAFT_SOUTH = -2;
    private static final int SHAFT_FLOOR_BELOW_SEA = 8;
    private static final int SHAFT_MIN_DEPTH = 8;
    private static final int SHAFT_MAX_DEPTH = 24;
    private static final int SHAFT_LANTERN_SPACING = 4;
    private static final DemoSet.Pose ACTOR_DECK = new DemoSet.Pose(0.5, POOL_DEPTH, 0.5, 180f, 0f);
    private static final DemoSet.Pose ACTOR_SUBMERGED = new DemoSet.Pose(0.5, 0, -1.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_DECK = new DemoSet.Pose(10.5, 4.2, -5.0, 90f, 14f);
    private static final DemoSet.Pose CAMERA_LAPS = new DemoSet.Pose(15.5, 4.5, -13.0, 90f, 12f);
    private static final DemoSet.Pose CAMERA_DUEL = new DemoSet.Pose(8.5, 3.2, -2.5, 90f, 12f);
    private static final DemoSet.Pose CAMERA_SHOAL = new DemoSet.Pose(10.5, 3.6, -5.0, 90f, 12f);
    private static final DemoSet.Pose CAMERA_WRECK = new DemoSet.Pose(8.5, 3.2, -3.0, 90f, 12f);
    private static final DemoSet.Pose CAMERA_DIG = new DemoSet.Pose(9.5, 3.8, -2.0, 90f, 12f);
    private static final DemoSet.Pose ACTOR_RIM = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_SHAFT = new DemoSet.Pose(1.6, 2.8, -1.0, -122f, 56f);
    private static final List<DemoSet.Sparring> SHOAL = List.of(
            new DemoSet.Sparring("COD", -1.5, 1, -6.5),
            new DemoSet.Sparring("TROPICAL_FISH", 1.5, 1, -7.5),
            new DemoSet.Sparring("TROPICAL_FISH", -2.0, 1.2, -8.5),
            new DemoSet.Sparring("SALMON", -0.5, 1.5, -9.5),
            new DemoSet.Sparring("COD", 2.0, 0.5, -10.5)
    );
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("seaborne-pool", ACTOR_DECK, CAMERA_DECK, false, List.of(), SeaborneSets::buildPool),
            new SimpleSet("seaborne-swim", ACTOR_SUBMERGED, CAMERA_LAPS, false, List.of(), SeaborneSets::buildPool),
            new SimpleSet("seaborne-duel", ACTOR_SUBMERGED, CAMERA_DUEL, false,
                    List.of(new DemoSet.Sparring("DROWNED", 0.5, 0, -3.5)), SeaborneSets::buildPool),
            new SimpleSet("seaborne-shoal", ACTOR_SUBMERGED, CAMERA_SHOAL, false, SHOAL, SeaborneSets::buildPool),
            new SimpleSet("seaborne-wreck", ACTOR_SUBMERGED, CAMERA_WRECK, false, List.of(), SeaborneSets::buildWreck),
            new SimpleSet("seaborne-dig", ACTOR_DECK, CAMERA_DIG, false, List.of(), SeaborneSets::buildDig),
            new FloodedShaft()
    );

    @Override
    public String skill() {
        return "seaborne";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildPool(World world, int x, int y, int z) {
        int north = z - POOL_LENGTH;
        int top = y + POOL_DEPTH - 1;
        DemoGeometry.fill(world, Material.GLASS, x - POOL_HALF_WIDTH - 1, y, north - 1, x + POOL_HALF_WIDTH + 1, top, north - 1);
        DemoGeometry.fill(world, Material.GLASS, x - POOL_HALF_WIDTH - 1, y, north, x - POOL_HALF_WIDTH - 1, top, z - 1);
        DemoGeometry.fill(world, Material.GLASS, x + POOL_HALF_WIDTH + 1, y, north, x + POOL_HALF_WIDTH + 1, top, z - 1);
        DemoGeometry.fill(world, Material.PRISMARINE_BRICKS, x - POOL_HALF_WIDTH - 1, y, z, x + POOL_HALF_WIDTH + 1, top, z + 2);
        DemoGeometry.fill(world, Material.SAND, x - POOL_HALF_WIDTH, y - 1, north, x + POOL_HALF_WIDTH, y - 1, z - 1);
        DemoGeometry.fill(world, Material.WATER, x - POOL_HALF_WIDTH, y, north, x + POOL_HALF_WIDTH, top, z - 1);
    }

    private static void buildDig(World world, int x, int y, int z) {
        buildPool(world, x, y, z);
        DemoGeometry.fill(world, Material.CLAY, x - 1, y, z - 5, x + 1, y + 1, z - 4);
    }

    private static void buildWreck(World world, int x, int y, int z) {
        buildPool(world, x, y, z);
        BlockData chest = Material.CHEST.createBlockData();
        ((Directional) chest).setFacing(BlockFace.SOUTH);
        ((Waterlogged) chest).setWaterlogged(true);
        world.getBlockAt(x, y, z - 5).setBlockData(chest, false);
    }

    private static final class FloodedShaft implements DemoSet {
        @Override
        public String name() {
            return "seaborne-shaft";
        }

        @Override
        public void build(World world, int x, int y, int z) {
            int floor = floorY(world, y);
            int west = x - SHAFT_HALF_WIDTH;
            int east = x + SHAFT_HALF_WIDTH;
            int north = z + SHAFT_NORTH;
            int south = z + SHAFT_SOUTH;
            DemoGeometry.fill(world, Material.PRISMARINE_BRICKS, west - 1, floor - 1, north - 1, east + 1, y - 1, south + 1);
            DemoGeometry.fill(world, Material.WATER, west, floor, north, east, y - 1, south);
            world.getBlockAt(x, floor - 1, (north + south) / 2).setType(Material.SEA_LANTERN, false);
            for (int lanternY = floor + 1; lanternY < y - 1; lanternY += SHAFT_LANTERN_SPACING) {
                world.getBlockAt(x, lanternY, north - 1).setType(Material.SEA_LANTERN, false);
                world.getBlockAt(x, lanternY, south + 1).setType(Material.SEA_LANTERN, false);
                world.getBlockAt(west - 1, lanternY, (north + south) / 2).setType(Material.SEA_LANTERN, false);
                world.getBlockAt(east + 1, lanternY, (north + south) / 2).setType(Material.SEA_LANTERN, false);
            }
        }

        @Override
        public void clear(World world, int x, int y, int z) {
            DemoGeometry.fill(world, Material.DIRT, x - SHAFT_HALF_WIDTH - 1, floorY(world, y) - 1, z + SHAFT_NORTH - 1,
                    x + SHAFT_HALF_WIDTH + 1, y - 2, z + SHAFT_SOUTH + 1);
        }

        @Override
        public DemoSet.Pose actorStart() {
            return ACTOR_RIM;
        }

        @Override
        public DemoSet.Pose camera() {
            return CAMERA_SHAFT;
        }

        @Override
        public boolean followCamera() {
            return true;
        }

        @Override
        public List<DemoSet.Sparring> sparring() {
            return List.of();
        }

        private static int floorY(World world, int originY) {
            int floor = Math.min(originY - SHAFT_MIN_DEPTH, world.getSeaLevel() - SHAFT_FLOOR_BELOW_SEA);
            if (originY - floor > SHAFT_MAX_DEPTH) {
                throw new IllegalStateException("Sea level " + world.getSeaLevel() + " of " + world.getName() + " needs a shaft floor at y "
                        + floor + ", " + (originY - floor) + " blocks below the plate at y " + originY + "; the limit is " + SHAFT_MAX_DEPTH);
            }
            return floor;
        }
    }
}
