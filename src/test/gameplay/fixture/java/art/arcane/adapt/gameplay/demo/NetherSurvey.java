package art.arcane.adapt.gameplay.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

final class NetherSurvey implements PlateSurvey {
    static final int HEADROOM = 40;

    private static final int MIN_SUPPORTED_PERCENT = 80;
    private static final int MIN_OPEN_PERCENT = 80;
    private static final int LAVA_MARGIN = 8;
    private static final long LOAD_TIMEOUT_SECONDS = 60L;
    private static final Set<Material> FLOORS = Set.of(Material.NETHERRACK, Material.SOUL_SOIL);
    private static final Set<String> BIOMES = Set.of("minecraft:nether_wastes", "minecraft:soul_sand_valley");

    private final World world;
    private final Executor mainThread;
    private final int bottom;
    private final int top;

    NetherSurvey(World world, Executor mainThread) {
        this.world = world;
        this.mainThread = mainThread;
        this.bottom = bottom(world);
        this.top = top(world);
    }

    static OptionalInt floor(World world, int x, int z) {
        return floor(y -> world.getBlockAt(x, y, z).getType(), bottom(world), top(world));
    }

    @Override
    public String goal() {
        return "open netherrack or soul soil floor with " + HEADROOM + " blocks of headroom";
    }

    @Override
    public PlateSearch.Verdict judge(PlateSearch.Candidate candidate, BooleanSupplier cancelled) throws InterruptedException {
        int x = candidate.x();
        int z = candidate.z();
        Map<Long, ChunkSnapshot> origin = snapshots(x, x, z, z);
        if (origin == null) {
            return new PlateSearch.Verdict("unavailable", PlateSearch.Rejection.UNAVAILABLE);
        }
        IntFunction<Material> originColumn = column(origin, x, z);
        OptionalInt originFloor = floor(originColumn, bottom, top);
        String biome = biome(origin, x, originFloor.orElse(bottom), z);
        if (!BIOMES.contains(biome)) {
            return new PlateSearch.Verdict(biome, PlateSearch.Rejection.BIOME);
        }
        if (originFloor.isEmpty()) {
            return new PlateSearch.Verdict(biome, PlateSearch.Rejection.HEADROOM);
        }
        int floor = originFloor.getAsInt();
        if (!FLOORS.contains(originColumn.apply(floor))) {
            return new PlateSearch.Verdict(biome, PlateSearch.Rejection.SURFACE);
        }
        if (cancelled.getAsBoolean()) {
            return new PlateSearch.Verdict(biome, PlateSearch.Rejection.UNAVAILABLE);
        }
        int reach = LAVA_MARGIN + 1;
        Map<Long, ChunkSnapshot> area = snapshots(x - DemoStudio.PLATE_HALF_WIDTH - reach, x + DemoStudio.PLATE_HALF_WIDTH + reach,
                z - DemoStudio.PLATE_NORTH - reach, z + DemoStudio.PLATE_SOUTH + reach);
        if (area == null) {
            return new PlateSearch.Verdict(biome, PlateSearch.Rejection.UNAVAILABLE);
        }
        return new PlateSearch.Verdict(biome, footprintRejection(area, x, z, floor));
    }

    private static int bottom(World world) {
        return world.getSeaLevel();
    }

    private static int top(World world) {
        return world.getMinHeight() + world.getLogicalHeight();
    }

    private static OptionalInt floor(IntFunction<Material> column, int bottom, int top) {
        int found = Integer.MIN_VALUE;
        int clear = 0;
        for (int y = top - 1; y >= bottom; y--) {
            Material material = column.apply(y);
            if (material.isAir()) {
                clear++;
                continue;
            }
            if (clear >= HEADROOM && material.isSolid()) {
                found = y;
            }
            clear = 0;
        }
        return found == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(found);
    }

    private PlateSearch.Rejection footprintRejection(Map<Long, ChunkSnapshot> area, int x, int z, int floor) {
        int cleared = floor + DemoStudio.CLEAR_HEIGHT + 1;
        int samples = 0;
        int supported = 0;
        int cells = 0;
        int open = 0;
        for (int dx : PlateSearch.X_OFFSETS) {
            for (int dz : PlateSearch.Z_OFFSETS) {
                if (!BIOMES.contains(biome(area, x + dx, floor, z + dz))) {
                    return PlateSearch.Rejection.BIOME;
                }
                IntFunction<Material> sample = column(area, x + dx, z + dz);
                samples++;
                if (solidBetween(sample, floor - DemoStudio.PLATE_DEPTH, floor)) {
                    supported++;
                }
                for (int y = floor + 1; y <= cleared; y++) {
                    cells++;
                    if (sample.apply(y).isAir()) {
                        open++;
                    }
                }
            }
        }
        if (supported * 100 < MIN_SUPPORTED_PERCENT * samples) {
            return PlateSearch.Rejection.SLOPE;
        }
        if (open * 100 < MIN_OPEN_PERCENT * cells) {
            return PlateSearch.Rejection.HEADROOM;
        }
        return lavaReaches(area, x, z, floor) ? PlateSearch.Rejection.LAVA : null;
    }

    private static boolean solidBetween(IntFunction<Material> column, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (column.apply(y).isSolid()) {
                return true;
            }
        }
        return false;
    }

    private boolean lavaReaches(Map<Long, ChunkSnapshot> area, int x, int z, int floor) {
        for (int dx = -DemoStudio.PLATE_HALF_WIDTH - LAVA_MARGIN; dx <= DemoStudio.PLATE_HALF_WIDTH + LAVA_MARGIN; dx++) {
            for (int dz = -DemoStudio.PLATE_NORTH - LAVA_MARGIN; dz <= DemoStudio.PLATE_SOUTH + LAVA_MARGIN; dz++) {
                IntFunction<Material> column = column(area, x + dx, z + dz);
                for (int y = floor + 1; y < top; y++) {
                    if (column.apply(y) == Material.LAVA && !plateCleared(dx, y, dz, floor) && lavaCanFlow(area, x, z, floor, dx, y, dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean lavaCanFlow(Map<Long, ChunkSnapshot> area, int x, int z, int floor, int dx, int y, int dz) {
        return open(area, x, z, floor, dx, y - 1, dz) || open(area, x, z, floor, dx + 1, y, dz) || open(area, x, z, floor, dx - 1, y, dz)
                || open(area, x, z, floor, dx, y, dz + 1) || open(area, x, z, floor, dx, y, dz - 1);
    }

    private static boolean open(Map<Long, ChunkSnapshot> area, int x, int z, int floor, int dx, int y, int dz) {
        if (plateCleared(dx, y, dz, floor)) {
            return true;
        }
        if (platedSolid(dx, y, dz, floor)) {
            return false;
        }
        Material material = column(area, x + dx, z + dz).apply(y);
        return !material.isSolid() && material != Material.LAVA;
    }

    private static boolean plateCleared(int dx, int y, int dz, int floor) {
        return inFootprint(dx, dz) && y > floor && y <= floor + DemoStudio.CLEAR_HEIGHT + 1;
    }

    private static boolean platedSolid(int dx, int y, int dz, int floor) {
        return inFootprint(dx, dz) && y > floor - DemoStudio.PLATE_DEPTH && y <= floor;
    }

    private static boolean inFootprint(int dx, int dz) {
        return Math.abs(dx) <= DemoStudio.PLATE_HALF_WIDTH && dz >= -DemoStudio.PLATE_NORTH && dz <= DemoStudio.PLATE_SOUTH;
    }

    private static IntFunction<Material> column(Map<Long, ChunkSnapshot> snapshots, int x, int z) {
        ChunkSnapshot snapshot = snapshots.get(key(x >> 4, z >> 4));
        int localX = x & 15;
        int localZ = z & 15;
        return y -> snapshot.getBlockType(localX, y, localZ);
    }

    private static String biome(Map<Long, ChunkSnapshot> snapshots, int x, int y, int z) {
        return snapshots.get(key(x >> 4, z >> 4)).getBiome(x & 15, y, z & 15).key().asString();
    }

    private Map<Long, ChunkSnapshot> snapshots(int minX, int maxX, int minZ, int maxZ) throws InterruptedException {
        CompletableFuture<Map<Long, ChunkSnapshot>> snapshots = CompletableFuture
                .supplyAsync(() -> loads(minX >> 4, maxX >> 4, minZ >> 4, maxZ >> 4), mainThread)
                .thenCompose(loads -> CompletableFuture.allOf(loads.toArray(new CompletableFuture<?>[0])).thenApply(ignored -> loads))
                .thenApplyAsync(NetherSurvey::capture, mainThread);
        try {
            return snapshots.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("Nether chunk load failed around " + minX + " " + minZ, failed.getCause());
        } catch (TimeoutException timedOut) {
            snapshots.cancel(false);
            return null;
        }
    }

    private List<CompletableFuture<Chunk>> loads(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        List<CompletableFuture<Chunk>> loads = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                loads.add(world.getChunkAtAsync(chunkX, chunkZ, true, false));
            }
        }
        return loads;
    }

    private static Map<Long, ChunkSnapshot> capture(List<CompletableFuture<Chunk>> loads) {
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>(loads.size() * 2);
        for (CompletableFuture<Chunk> load : loads) {
            Chunk chunk = load.join();
            snapshots.put(key(chunk.getX(), chunk.getZ()), chunk.getChunkSnapshot(false, true, false));
        }
        return snapshots;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
