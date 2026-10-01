package art.arcane.adapt.gameplay.demo;

import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.api.terrain.IrisTerrainService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

final class IrisSurvey implements PlateSurvey {
    private static final int MAX_HEIGHT_SPAN = 4;
    private static final long READ_RETRY_MILLIS = 20L;
    private static final long READ_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10L);
    private static final List<String> GRASSLAND_WORDS = List.of("plains", "meadow", "grass", "savanna");

    private final World world;
    private final IrisTerrainService terrain;

    private IrisSurvey(World world, IrisTerrainService terrain) {
        this.world = world;
        this.terrain = terrain;
    }

    static IrisSurvey create(World world) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Iris")) {
            throw new IllegalStateException("Iris is not enabled");
        }
        IrisTerrainService terrain = Bukkit.getServicesManager().load(IrisTerrainService.class);
        if (terrain == null || !terrain.isIrisWorld(world)) {
            throw new IllegalStateException("Iris terrain service unavailable for " + world.getName());
        }
        return new IrisSurvey(world, terrain);
    }

    @Override
    public String goal() {
        return "flat grassland";
    }

    @Override
    public PlateSearch.Verdict judge(PlateSearch.Candidate candidate, BooleanSupplier cancelled) throws InterruptedException {
        Column origin = column(candidate.x(), candidate.z(), cancelled);
        if (origin == null) {
            return new PlateSearch.Verdict("unavailable", PlateSearch.Rejection.UNAVAILABLE);
        }
        if (!grassland(origin.biome())) {
            return new PlateSearch.Verdict(origin.biome(), PlateSearch.Rejection.BIOME);
        }
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx : PlateSearch.X_OFFSETS) {
            for (int dz : PlateSearch.Z_OFFSETS) {
                Column sample = column(candidate.x() + dx, candidate.z() + dz, cancelled);
                if (sample == null) {
                    return new PlateSearch.Verdict(origin.biome(), PlateSearch.Rejection.UNAVAILABLE);
                }
                if (sample.kind() != IrisSurfaceKind.LAND) {
                    return new PlateSearch.Verdict(origin.biome(), PlateSearch.Rejection.WATER);
                }
                if (!grassland(sample.biome())) {
                    return new PlateSearch.Verdict(origin.biome(), PlateSearch.Rejection.BIOME);
                }
                lowest = Math.min(lowest, sample.height());
                highest = Math.max(highest, sample.height());
                if (highest - lowest > MAX_HEIGHT_SPAN) {
                    return new PlateSearch.Verdict(origin.biome(), PlateSearch.Rejection.SLOPE);
                }
            }
        }
        return new PlateSearch.Verdict(origin.biome(), null);
    }

    @Override
    public boolean confirm(PlateSearch.Candidate candidate) {
        for (int dx : PlateSearch.X_OFFSETS) {
            for (int dz : PlateSearch.Z_OFFSETS) {
                Block ground = world.getHighestBlockAt(candidate.x() + dx, candidate.z() + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (ground.getType() != Material.GRASS_BLOCK) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean grassland(String biome) {
        String key = biome.toLowerCase(Locale.ROOT);
        for (String word : GRASSLAND_WORDS) {
            if (key.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private Column column(int x, int z, BooleanSupplier cancelled) throws InterruptedException {
        long deadline = System.nanoTime() + READ_TIMEOUT_NANOS;
        while (true) {
            Optional<String> biome = terrain.surfaceBiomeKey(world, x, z);
            IrisSurfaceKind kind = terrain.surfaceKind(world, x, z);
            OptionalInt height = terrain.surfaceHeight(world, x, z);
            if (biome.isPresent() && kind != IrisSurfaceKind.UNKNOWN && height.isPresent()) {
                return new Column(biome.get(), kind, height.getAsInt());
            }
            if (cancelled.getAsBoolean() || System.nanoTime() > deadline) {
                return null;
            }
            Thread.sleep(READ_RETRY_MILLIS);
        }
    }

    private record Column(String biome, IrisSurfaceKind kind, int height) {
    }
}
