package art.arcane.adapt.claims;

import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ClaimSafeLocation {
    private static final int MAX_CANDIDATES = 128;
    private static final int SEARCH_RADIUS = 64;
    private static final int VERTICAL_SEARCH_RANGE = 16;
    private static final Set<Material> UNSAFE_FLOORS = Set.of(Material.CACTUS, Material.MAGMA_BLOCK,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.FIRE,
            Material.SOUL_FIRE, Material.LAVA, Material.WATER, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.END_PORTAL, Material.NETHER_PORTAL);

    private final ClaimsService claims;

    public ClaimSafeLocation(ClaimsService claims) {
        this.claims = Objects.requireNonNull(claims);
    }

    public CompletableFuture<Optional<Location>> findOutside(Player player, Location origin) {
        double width = player.getWidth();
        return findOutside(origin, candidate -> sameLayer(origin, candidate, width * 3.0) && safeOwned(width, candidate));
    }

    CompletableFuture<Optional<Location>> findOutside(Location origin, Predicate<Location> permitted) {
        return findOutside(origin, ClaimArrivalProfile.NONE, permitted);
    }

    CompletableFuture<Optional<Location>> findOutside(Location origin, ClaimArrivalProfile profile, Predicate<Location> permitted) {
        World world = Objects.requireNonNull(origin.getWorld());
        origin.checkFinite();
        Location requested = origin.clone();
        CompletableFuture<Optional<Location>> result = new CompletableFuture<>();
        int minY = Math.max(world.getMinHeight() + 1, requested.getBlockY() - VERTICAL_SEARCH_RANGE);
        int maxY = Math.min(world.getMaxHeight() - 2, requested.getBlockY() + VERTICAL_SEARCH_RANGE);
        if (profile.horizontal()) {
            minY = requested.getBlockY();
            maxY = requested.getBlockY();
        } else if (world.hasCeiling() && world.getLogicalHeight() > 0) {
            int ceiling = world.getMinHeight() + world.getLogicalHeight();
            if (requested.getY() < ceiling) {
                maxY = Math.min(maxY, ceiling - 2);
            } else {
                minY = Math.max(minY, ceiling);
            }
        }
        inspect(new Search(requested, candidates(requested), minY, maxY, profile, permitted, result), 0);
        return result.orTimeout(15, TimeUnit.SECONDS);
    }

    public boolean safeOwned(Player player, Location location) {
        return safeOwned(player.getWidth(), location);
    }

    public boolean safeLandingOwned(Player player, Location location) {
        return safeLandingOwned(player.getWidth(), location);
    }

    private boolean safeOwned(double width, Location location) {
        if (!safeLandingOwned(width, location)) {
            return false;
        }
        BoundingBox body = standingBody(width, location);
        for (int x = Location.locToBlock(body.getMinX()) >> 4; x <= Location.locToBlock(Math.nextDown(body.getMaxX())) >> 4; x++) {
            for (int z = Location.locToBlock(body.getMinZ()) >> 4; z <= Location.locToBlock(Math.nextDown(body.getMaxZ())) >> 4; z++) {
                if (claims.manager().snapshot().cityAt(new ClaimChunk(location.getWorld().getUID(), x, z)).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean safeLandingOwned(double width, Location location) {
        BoundingBox body = standingBody(width, location);
        double radius = width / 2.0;
        World world = location.getWorld();
        if (world == null || !J.isOwnedByCurrentRegion(location)
                || J.isFoliaThreading() && !J.isOwnedByCurrentRegion(location, radius, radius)
                || !world.getWorldBorder().isInside(location)
                || location.getBlockY() <= world.getMinHeight()
                || body.getMaxY() > world.getMaxHeight()) {
            return false;
        }
        for (int x = Location.locToBlock(body.getMinX()) >> 4; x <= Location.locToBlock(Math.nextDown(body.getMaxX())) >> 4; x++) {
            for (int z = Location.locToBlock(body.getMinZ()) >> 4; z <= Location.locToBlock(Math.nextDown(body.getMaxZ())) >> 4; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        if (!world.getWorldBorder().isInside(new Location(world, body.getMinX(), location.getY(), body.getMinZ()))
                || !world.getWorldBorder().isInside(new Location(world, Math.nextDown(body.getMaxX()), location.getY(), Math.nextDown(body.getMaxZ())))) {
            return false;
        }
        int top = Location.locToBlock(Math.nextDown(body.getMaxY()));
        BoundingBox support = new BoundingBox(body.getMinX(), body.getMinY() - 0.05, body.getMinZ(),
                body.getMaxX(), body.getMinY(), body.getMaxZ());
        for (int x = Location.locToBlock(body.getMinX()); x <= Location.locToBlock(Math.nextDown(body.getMaxX())); x++) {
            for (int z = Location.locToBlock(body.getMinZ()); z <= Location.locToBlock(Math.nextDown(body.getMaxZ())); z++) {
                int floorY = location.getBlockY() - 1;
                Block floor = world.getBlockAt(x, floorY, z);
                if (!safeFloor(floor.getType()) || floor.getCollisionShape().overlaps(body.clone().shift(-x, -floorY, -z))
                        || !floor.getCollisionShape().overlaps(support.clone().shift(-x, -floorY, -z))) {
                    return false;
                }
                for (int y = location.getBlockY(); y <= top; y++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static BoundingBox standingBody(Player player, Location destination) {
        return standingBody(player.getWidth(), destination);
    }

    static boolean sameLayer(Location origin, Location destination, double height) {
        World world = origin.getWorld();
        if (!world.hasCeiling() || world.getLogicalHeight() <= 0) {
            return true;
        }
        int ceiling = world.getMinHeight() + world.getLogicalHeight();
        return origin.getY() < ceiling ? destination.getY() + height <= ceiling : destination.getY() >= ceiling;
    }

    private static BoundingBox standingBody(double width, Location destination) {
        double radius = width / 2.0;
        return new BoundingBox(destination.getX() - radius, destination.getY(), destination.getZ() - radius,
                destination.getX() + radius, destination.getY() + width * 3.0, destination.getZ() + radius);
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
        candidates.sort(Comparator.comparingDouble(chunk -> distanceSquared(chunk, origin)));
        return candidates;
    }

    private double distanceSquared(ClaimChunk chunk, Location origin) {
        double x = Math.clamp(origin.getX(), (chunk.x() << 4) + 2.5, (chunk.x() << 4) + 14.5);
        double z = Math.clamp(origin.getZ(), (chunk.z() << 4) + 2.5, (chunk.z() << 4) + 14.5);
        double dx = x - origin.getX();
        double dz = z - origin.getZ();
        return dx * dx + dz * dz;
    }

    private void addCandidate(List<ClaimChunk> candidates, ClaimChunk chunk) {
        if (candidates.size() < MAX_CANDIDATES && claims.manager().snapshot().cityAt(chunk).isEmpty()) {
            candidates.add(chunk);
        }
    }

    private void inspect(Search search, int index) {
        if (search.result().isDone()) {
            return;
        }
        if (index >= search.candidates().size() || search.minY() > search.maxY() || !claims.enabled()) {
            search.result().complete(Optional.empty());
            return;
        }
        World world = search.origin().getWorld();
        ClaimChunk chunk = search.candidates().get(index);
        Location center = new Location(world, (chunk.x() << 4) + 8.5, world.getMinHeight(), (chunk.z() << 4) + 8.5);
        world.getChunkAtAsync(chunk.x(), chunk.z(), true).whenComplete((loaded, failure) -> {
            if (search.result().isDone()) {
                return;
            }
            if (failure != null) {
                search.result().completeExceptionally(failure);
                return;
            }
            if (!J.runAt(center, () -> inspectOwned(search, index))) {
                search.result().complete(Optional.empty());
            }
        });
    }

    private void inspectOwned(Search search, int index) {
        if (search.result().isDone()) {
            return;
        }
        if (!claims.enabled()) {
            search.result().complete(Optional.empty());
            return;
        }
        for (Column column : columns(search, index)) {
            if (search.result().isDone()) {
                return;
            }
            Optional<Location> destination = inspectColumn(search, column);
            if (destination.isPresent()) {
                search.result().complete(destination);
                return;
            }
        }
        inspect(search, index + 1);
    }

    private List<Column> columns(Search search, int index) {
        ClaimChunk chunk = search.candidates().get(index);
        int minX = chunk.x() << 4;
        int minZ = chunk.z() << 4;
        int insetMaximum = search.profile().horizontal() ? 13 : 14;
        List<Column> columns = new ArrayList<>(17);
        Column closest = new Column(Math.clamp(search.origin().getBlockX(), minX + 2, minX + insetMaximum),
                Math.clamp(search.origin().getBlockZ(), minZ + 2, minZ + insetMaximum));
        columns.add(closest);
        for (int x = minX + 2; x <= minX + insetMaximum; x += 4) {
            for (int z = minZ + 2; z <= minZ + insetMaximum; z += 4) {
                if (x != closest.x() || z != closest.z()) {
                    columns.add(new Column(x, z));
                }
            }
        }
        columns.sort(Comparator.comparingDouble(column -> column.distanceSquared(search.origin())));
        return columns;
    }

    private Optional<Location> inspectColumn(Search search, Column column) {
        int requestedY = search.origin().getBlockY();
        int range = search.profile().horizontal() ? 0 : VERTICAL_SEARCH_RANGE;
        for (int distance = 0; distance <= range; distance++) {
            Location below = candidate(search, column, requestedY - distance);
            if (below != null && search.permitted().test(below)) {
                return Optional.of(below);
            }
            if (distance > 0) {
                Location above = candidate(search, column, requestedY + distance);
                if (above != null && search.permitted().test(above)) {
                    return Optional.of(above);
                }
            }
        }
        return Optional.empty();
    }

    private Location candidate(Search search, Column column, int y) {
        if (y < search.minY() || y > search.maxY()) {
            return null;
        }
        Location origin = search.origin();
        if (search.profile().horizontal()) {
            return new Location(origin.getWorld(), column.x() + origin.getX() - origin.getBlockX(), origin.getY(),
                    column.z() + origin.getZ() - origin.getBlockZ(), origin.getYaw(), origin.getPitch());
        }
        return new Location(origin.getWorld(), column.x() + 0.5, y, column.z() + 0.5,
                origin.getYaw(), origin.getPitch());
    }

    private record Search(Location origin, List<ClaimChunk> candidates, int minY, int maxY, ClaimArrivalProfile profile,
                          Predicate<Location> permitted,
                          CompletableFuture<Optional<Location>> result) {
    }

    private record Column(int x, int z) {
        private double distanceSquared(Location origin) {
            double dx = x + 0.5 - origin.getX();
            double dz = z + 0.5 - origin.getZ();
            return dx * dx + dz * dz;
        }
    }
}
