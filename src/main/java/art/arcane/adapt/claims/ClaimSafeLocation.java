package art.arcane.adapt.claims;

import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ClaimSafeLocation {
    private static final int MAX_CANDIDATES = 128;
    private static final int SEARCH_RADIUS = 64;
    private static final Set<Material> UNSAFE_FLOORS = Set.of(Material.CACTUS, Material.MAGMA_BLOCK,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.FIRE,
            Material.SOUL_FIRE, Material.LAVA, Material.WATER, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.END_PORTAL, Material.NETHER_PORTAL);

    private final ClaimsService claims;

    public ClaimSafeLocation(ClaimsService claims) {
        this.claims = Objects.requireNonNull(claims);
    }

    public CompletableFuture<Optional<Location>> findOutside(Location origin) {
        World world = Objects.requireNonNull(origin.getWorld());
        List<ClaimChunk> candidates = candidates(origin);
        CompletableFuture<Optional<Location>> result = new CompletableFuture<>();
        inspect(world, candidates, 0, result);
        return result.orTimeout(15, TimeUnit.SECONDS);
    }

    public boolean safeOwned(Location location) {
        World world = location.getWorld();
        if (world == null || !J.isOwnedByCurrentRegion(location)
                || claims.manager().snapshot().cityAt(ClaimsService.chunk(location)).isPresent()
                || !world.getWorldBorder().isInside(location)
                || location.getBlockY() <= world.getMinHeight()
                || location.getBlockY() + 1 >= world.getMaxHeight()) {
            return false;
        }
        Block feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Block head = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        Block floor = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
        return safeFloor(floor.getType()) && feet.getType().isAir() && head.getType().isAir();
    }

    static boolean safeFloor(Material material) {
        return !UNSAFE_FLOORS.contains(material) && material.isSolid();
    }

    private List<ClaimChunk> candidates(Location origin) {
        List<ClaimChunk> candidates = new ArrayList<>(MAX_CANDIDATES);
        ClaimChunk center = ClaimsService.chunk(origin);
        for (int radius = 0; radius <= SEARCH_RADIUS && candidates.size() < MAX_CANDIDATES; radius++) {
            for (int x = -radius; x <= radius && candidates.size() < MAX_CANDIDATES; x++) {
                addCandidate(candidates, new ClaimChunk(center.worldId(), center.x() + x, center.z() - radius));
                if (radius > 0) {
                    addCandidate(candidates, new ClaimChunk(center.worldId(), center.x() + x, center.z() + radius));
                }
            }
            for (int z = -radius + 1; z < radius && candidates.size() < MAX_CANDIDATES; z++) {
                addCandidate(candidates, new ClaimChunk(center.worldId(), center.x() - radius, center.z() + z));
                addCandidate(candidates, new ClaimChunk(center.worldId(), center.x() + radius, center.z() + z));
            }
        }
        return candidates;
    }

    private void addCandidate(List<ClaimChunk> candidates, ClaimChunk chunk) {
        if (candidates.size() < MAX_CANDIDATES && claims.manager().snapshot().cityAt(chunk).isEmpty()) {
            candidates.add(chunk);
        }
    }

    private void inspect(World world, List<ClaimChunk> candidates, int index,
                         CompletableFuture<Optional<Location>> result) {
        if (result.isDone()) {
            return;
        }
        if (index >= candidates.size() || !claims.enabled()) {
            result.complete(Optional.empty());
            return;
        }
        ClaimChunk chunk = candidates.get(index);
        Location center = new Location(world, (chunk.x() << 4) + 8.5, world.getMinHeight(), (chunk.z() << 4) + 8.5);
        world.getChunkAtAsync(chunk.x(), chunk.z(), true).whenComplete((loaded, failure) -> {
            if (result.isDone()) {
                return;
            }
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            if (!J.runAt(center, () -> inspectOwned(world, candidates, index, result))) {
                result.complete(Optional.empty());
            }
        });
    }

    private void inspectOwned(World world, List<ClaimChunk> candidates, int index,
                              CompletableFuture<Optional<Location>> result) {
        if (result.isDone()) {
            return;
        }
        if (!claims.enabled()) {
            result.complete(Optional.empty());
            return;
        }
        ClaimChunk chunk = candidates.get(index);
        int minX = chunk.x() << 4;
        int minZ = chunk.z() << 4;
        for (int x = minX + 2; x < minX + 15; x += 4) {
            for (int z = minZ + 2; z < minZ + 15; z += 4) {
                if (result.isDone()) {
                    return;
                }
                int y = world.getHighestBlockYAt(x, z) + 1;
                Location destination = new Location(world, x + 0.5, y, z + 0.5);
                if (safeOwned(destination)) {
                    result.complete(Optional.of(destination));
                    return;
                }
            }
        }
        inspect(world, candidates, index + 1, result);
    }
}
