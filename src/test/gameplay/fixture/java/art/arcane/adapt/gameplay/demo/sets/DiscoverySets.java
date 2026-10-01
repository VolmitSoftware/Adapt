package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BrushableBlock;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.ItemStack;

public final class DiscoverySets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_RUNWAY = new DemoSet.Pose(3.0, 1.2, 4.5, -33.7f, 12.5f);
    private static final int DIG_WEST = -2;
    private static final int DIG_EAST = 2;
    private static final int DIG_NORTH = -3;
    private static final int DIG_SOUTH = -1;
    private static final int DIG_ROW = -2;
    private static final int DIG_FIRST_X = -1;
    private static final List<Material> DIG_FINDS = List.of(Material.ARCHER_POTTERY_SHERD, Material.PRIZE_POTTERY_SHERD,
            Material.ARMS_UP_POTTERY_SHERD);
    private static final List<Cache> CACHES = List.of(new Cache(Material.CHEST, -4, -9), new Cache(Material.CHEST, 3, -13),
            new Cache(Material.CHEST, 6, -7), new Cache(Material.SPAWNER, -3, -15));
    private static final int VAULT_CENTER_Z = -10;
    private static final int VAULT_FLOOR_HALF = 5;
    private static final int VAULT_FLOOR_DEPTH = 3;
    private static final int VAULT_WALL_INNER = 2;
    private static final int VAULT_WALL_OUTER = 3;
    private static final int VAULT_WALL_HEIGHT = 4;
    private static final int FRONTIER_HALF_WIDTH = 15;
    private static final int FRONTIER_NORTH = -60;
    private static final int FRONTIER_SOUTH = -12;
    private static final int FRONTIER_BELOW = 4;
    private static final int FRONTIER_ABOVE = 8;
    private static final int BIOME_CELL = 4;
    private static final int CHUNK_SHIFT = 4;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("discovery-dig", ACTOR_NORTH, new DemoSet.Pose(5.5, 2.8, -3.5, 64.3f, 21.7f), false, List.of(),
                    DiscoverySets::buildDig),
            new SimpleSet("discovery-cache", ACTOR_NORTH, new DemoSet.Pose(4.5, 4.0, 5.5, 165.5f, 12.3f), false, List.of(),
                    DiscoverySets::buildCache),
            new SimpleSet("discovery-pasture", ACTOR_NORTH, new DemoSet.Pose(7.0, 3.2, 2.0, 113.3f, 20.2f), false,
                    List.of(new DemoSet.Sparring("COW", -1.0, 0, -1.8), new DemoSet.Sparring("PIG", 2.0, 0, -1.8)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("discovery-market", ACTOR_NORTH, new DemoSet.Pose(6.5, 2.4, -0.5, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("VILLAGER", 0.5, 0, -1.5)), SimpleSet.OPEN_PLATE),
            new Vault(),
            new Frontier()
    );

    @Override
    public String skill() {
        return "discovery";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildDig(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.SAND, x + DIG_WEST, y - 1, z + DIG_NORTH, x + DIG_EAST, y - 1, z + DIG_SOUTH);
        for (int index = 0; index < DIG_FINDS.size(); index++) {
            Block block = world.getBlockAt(x + DIG_FIRST_X + index, y - 1, z + DIG_ROW);
            block.setType(Material.SUSPICIOUS_SAND, false);
            BrushableBlock brushable = (BrushableBlock) block.getState();
            brushable.setItem(new ItemStack(DIG_FINDS.get(index)));
            brushable.update(true, false);
        }
    }

    private static void buildCache(World world, int x, int y, int z) {
        for (Cache cache : CACHES) {
            BlockData data = cache.block().createBlockData();
            if (data instanceof Directional directional) {
                directional.setFacing(BlockFace.SOUTH);
            }
            world.getBlockAt(x + cache.dx(), y, z + cache.dz()).setBlockData(data, false);
        }
    }

    private static int cellStart(int coordinate) {
        return Math.floorDiv(coordinate, BIOME_CELL) * BIOME_CELL;
    }

    private record Cache(Material block, int dx, int dz) {
    }

    private record BiomeCell(int x, int y, int z, Biome biome) {
    }

    private static final class Vault implements DemoSet {
        private static final DemoSet.Pose CAMERA = new DemoSet.Pose(1.0, 4.5, 3.5, 177.8f, 15.1f);

        @Override
        public String name() {
            return "discovery-vault";
        }

        @Override
        public void build(World world, int x, int y, int z) {
            int center = z + VAULT_CENTER_Z;
            int top = y + VAULT_WALL_HEIGHT - 1;
            DemoGeometry.fill(world, Material.OBSIDIAN, x - VAULT_FLOOR_HALF, y - VAULT_FLOOR_DEPTH, center - VAULT_FLOOR_HALF,
                    x + VAULT_FLOOR_HALF, y - 1, center + VAULT_FLOOR_HALF);
            DemoGeometry.fill(world, Material.OBSIDIAN, x - VAULT_WALL_OUTER, y, center - VAULT_WALL_OUTER,
                    x - VAULT_WALL_INNER, top, center + VAULT_WALL_OUTER);
            DemoGeometry.fill(world, Material.OBSIDIAN, x + VAULT_WALL_INNER, y, center - VAULT_WALL_OUTER,
                    x + VAULT_WALL_OUTER, top, center + VAULT_WALL_OUTER);
            DemoGeometry.fill(world, Material.OBSIDIAN, x - VAULT_WALL_OUTER, y, center - VAULT_WALL_OUTER,
                    x + VAULT_WALL_OUTER, top, center - VAULT_WALL_INNER);
        }

        @Override
        public void clear(World world, int x, int y, int z) {
            int center = z + VAULT_CENTER_Z;
            DemoGeometry.fill(world, Material.DIRT, x - VAULT_FLOOR_HALF, y - VAULT_FLOOR_DEPTH, center - VAULT_FLOOR_HALF,
                    x + VAULT_FLOOR_HALF, y - 2, center + VAULT_FLOOR_HALF);
        }

        @Override
        public DemoSet.Pose actorStart() {
            return ACTOR_NORTH;
        }

        @Override
        public DemoSet.Pose camera() {
            return CAMERA;
        }

        @Override
        public List<DemoSet.Sparring> sparring() {
            return List.of();
        }
    }

    private static final class Frontier implements DemoSet {
        private final List<BiomeCell> original = new ArrayList<>();

        @Override
        public String name() {
            return "discovery-frontier";
        }

        @Override
        public void build(World world, int x, int y, int z) {
            restore(world);
            for (int cellX = cellStart(x - FRONTIER_HALF_WIDTH); cellX <= x + FRONTIER_HALF_WIDTH; cellX += BIOME_CELL) {
                for (int cellY = cellStart(y - FRONTIER_BELOW); cellY <= y + FRONTIER_ABOVE; cellY += BIOME_CELL) {
                    for (int cellZ = cellStart(z + FRONTIER_NORTH); cellZ <= z + FRONTIER_SOUTH; cellZ += BIOME_CELL) {
                        original.add(new BiomeCell(cellX, cellY, cellZ, world.getBiome(cellX, cellY, cellZ)));
                        world.setBiome(cellX, cellY, cellZ, Biome.BADLANDS);
                    }
                }
            }
            refresh(world, x, z);
        }

        @Override
        public void clear(World world, int x, int y, int z) {
            restore(world);
            refresh(world, x, z);
        }

        @Override
        public DemoSet.Pose actorStart() {
            return ACTOR_NORTH;
        }

        @Override
        public DemoSet.Pose camera() {
            return CAMERA_RUNWAY;
        }

        @Override
        public boolean followCamera() {
            return true;
        }

        @Override
        public List<DemoSet.Sparring> sparring() {
            return List.of();
        }

        private void restore(World world) {
            for (BiomeCell cell : original) {
                world.setBiome(cell.x(), cell.y(), cell.z(), cell.biome());
            }
            original.clear();
        }

        private static void refresh(World world, int x, int z) {
            int westChunk = (x - FRONTIER_HALF_WIDTH - BIOME_CELL) >> CHUNK_SHIFT;
            int eastChunk = (x + FRONTIER_HALF_WIDTH + BIOME_CELL) >> CHUNK_SHIFT;
            int northChunk = (z + FRONTIER_NORTH - BIOME_CELL) >> CHUNK_SHIFT;
            int southChunk = (z + FRONTIER_SOUTH + BIOME_CELL) >> CHUNK_SHIFT;
            for (int chunkX = westChunk; chunkX <= eastChunk; chunkX++) {
                for (int chunkZ = northChunk; chunkZ <= southChunk; chunkZ++) {
                    world.refreshChunk(chunkX, chunkZ);
                }
            }
        }
    }
}
