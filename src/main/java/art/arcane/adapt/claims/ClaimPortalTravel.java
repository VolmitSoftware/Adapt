package art.arcane.adapt.claims;

import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.portal.NetherPortalAccess;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ClaimPortalTravel {
    private static final int MAX_EXISTING_PORTALS = 128;
    private static final int MAX_CREATION_CANDIDATES = 32;
    private static final int MAX_CREATION_CHUNK_DISTANCE = 64;

    private final ClaimsService claims;
    private final NetherPortalAccess portals;

    public ClaimPortalTravel(ClaimsService claims) {
        this(claims, NativeAdapters.find(NetherPortalAccess.class).orElse(null));
    }

    ClaimPortalTravel(ClaimsService claims, NetherPortalAccess portals) {
        this.claims = Objects.requireNonNull(claims);
        this.portals = portals;
    }

    public CompletableFuture<Optional<Location>> resolve(Request request) {
        Objects.requireNonNull(request);
        if (portals == null || J.isFoliaThreading()) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "Claim-aware Nether portal resolution requires a supported Paper or Purpur server"));
        }
        Search search = new Search(request);
        search.result.orTimeout(15, TimeUnit.SECONDS);
        if (!active(search)) {
            return search.result;
        }
        try {
            portals.loadSearch(request.requested(), request.searchRadius()).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    search.result.completeExceptionally(failure);
                } else {
                    schedule(search, request.requested(), () -> inspectExisting(search,
                            portals.find(request.requested(), request.searchRadius()), 0));
                }
            });
        } catch (RuntimeException | LinkageError failure) {
            search.result.completeExceptionally(failure);
        }
        return search.result;
    }

    public boolean safeDestination(Player player, Location destination) {
        World world = destination.getWorld();
        if (portals == null || world == null || J.isFoliaThreading()
                || !claims.canTeleport(player, destination) || !portals.clearDestination(player, destination)) {
            return false;
        }
        BoundingBox body = ClaimSafeLocation.standingBody(player, destination);
        for (int x = Location.locToBlock(body.getMinX()); x <= Location.locToBlock(Math.nextDown(body.getMaxX())); x++) {
            for (int z = Location.locToBlock(body.getMinZ()); z <= Location.locToBlock(Math.nextDown(body.getMaxZ())); z++) {
                Location column = new Location(world, x + 0.5, destination.getY(), z + 0.5);
                if (!claims.canTeleport(player, column) || !world.getWorldBorder().isInside(column)
                        || !safeColumn(destination, body, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Vector movement(Player player) {
        if (portals == null || J.isFoliaThreading()) {
            throw new UnsupportedOperationException("Native portal movement requires a supported Paper or Purpur server");
        }
        return portals.movement(player);
    }

    private boolean active(Search search) {
        if (search.result.isDone()) {
            return false;
        }
        if (!claims.enabled() || !search.request.player().isOnline() || !search.request.active().getAsBoolean()) {
            search.result.complete(Optional.empty());
            return false;
        }
        return true;
    }

    private void inspectExisting(Search search, List<Location> candidates, int start) {
        if (!active(search)) {
            return;
        }
        int index = start;
        while (index < candidates.size() && (visited(search, candidates.get(index))
                || !claims.canTeleport(search.request.player(), candidates.get(index)))) {
            index++;
        }
        if (index >= candidates.size() || search.inspected >= MAX_EXISTING_PORTALS) {
            if (search.request.canCreate()) {
                create(search, creationCandidates(search.request), 0);
            } else {
                search.result.complete(Optional.empty());
            }
            return;
        }
        Location candidate = candidates.get(index);
        int next = index + 1;
        search.inspected++;
        load(search, candidate, 24, retained -> {
            Optional<NetherPortalAccess.Rectangle> rectangle = portals.inspect(candidate);
            if (rectangle.isPresent()) {
                search.visited.add(rectangle.get());
                Optional<Location> destination = destination(search.request, rectangle.get());
                if (destination.isPresent()) {
                    retained.copyTo(search.request.chunks());
                    search.result.complete(destination);
                    return;
                }
            }
            inspectExisting(search, candidates, next);
        });
    }

    private boolean visited(Search search, Location location) {
        for (NetherPortalAccess.Rectangle rectangle : search.visited) {
            int widthX = rectangle.axis() == Axis.X ? rectangle.width() : 1;
            int widthZ = rectangle.axis() == Axis.Z ? rectangle.width() : 1;
            if (location.getWorld().equals(rectangle.world())
                    && location.getBlockX() >= rectangle.x() && location.getBlockX() < rectangle.x() + widthX
                    && location.getBlockZ() >= rectangle.z() && location.getBlockZ() < rectangle.z() + widthZ
                    && location.getBlockY() >= rectangle.y() && location.getBlockY() < rectangle.y() + rectangle.height()) {
                return true;
            }
        }
        return false;
    }

    private Optional<Location> destination(Request request, NetherPortalAccess.Rectangle rectangle) {
        if (!permittedRectangle(request.player(), rectangle)) {
            return Optional.empty();
        }
        World world = rectangle.world();
        if (world.hasCeiling() && world.getLogicalHeight() > 0) {
            int ceiling = world.getMinHeight() + world.getLogicalHeight();
            if (request.requested().getY() < ceiling && rectangle.y() + rectangle.height() >= ceiling) {
                return Optional.empty();
            }
        }
        Location destination = portals.destination(request.player(), request.origin(), rectangle);
        if (!destination.getWorld().equals(request.requested().getWorld())
                || destination.getY() <= world.getMinHeight()
                || ClaimSafeLocation.standingBody(request.player(), destination).getMaxY() >= world.getMaxHeight()) {
            return Optional.empty();
        }
        if (safeDestination(request.player(), destination)) {
            return Optional.of(destination);
        }
        Location bottom = destination.clone();
        bottom.setY(rectangle.y());
        return safeDestination(request.player(), bottom) ? Optional.of(bottom) : Optional.empty();
    }

    private boolean safeColumn(Location destination, BoundingBox body, int x, int z) {
        World world = destination.getWorld();
        int feetY = destination.getBlockY();
        Material feet = world.getBlockAt(x, feetY, z).getType();
        int headY = Location.locToBlock(Math.nextDown(body.getMaxY()));
        for (int y = feetY; y <= headY; y++) {
            if (!passablePortal(world.getBlockAt(x, y, z).getType())) {
                return false;
            }
        }
        double maximumDrop = feet == Material.NETHER_PORTAL ? 3 : 0;
        for (int y = feetY - 1; y >= world.getMinHeight() && destination.getY() - (y + 1) <= maximumDrop; y--) {
            Material floor = world.getBlockAt(x, y, z).getType();
            if (ClaimSafeLocation.safeFloor(floor)) {
                return true;
            }
            if (!passablePortal(floor)) {
                return false;
            }
        }
        return false;
    }

    private boolean passablePortal(Material material) {
        return material == Material.NETHER_PORTAL || material.isAir();
    }

    private boolean permittedRectangle(Player player, NetherPortalAccess.Rectangle rectangle) {
        for (int across = -1; across <= rectangle.width(); across++) {
            int x = rectangle.x() + (rectangle.axis() == Axis.X ? across : 0);
            int z = rectangle.z() + (rectangle.axis() == Axis.Z ? across : 0);
            Location frame = new Location(rectangle.world(), x + 0.5, rectangle.y(), z + 0.5);
            if (!rectangle.world().getWorldBorder().isInside(frame) || !claims.canTeleport(player, frame)) {
                return false;
            }
        }
        return true;
    }

    private void create(Search search, List<Location> candidates, int index) {
        if (!active(search)) {
            return;
        }
        if (index >= candidates.size()) {
            search.result.complete(Optional.empty());
            return;
        }
        Location candidate = candidates.get(index);
        load(search, candidate, 18, retained -> {
            if (!canPrepare(search.request.player(), candidate)) {
                create(search, candidates, index + 1);
                return;
            }
            Optional<NetherPortalAccess.Rectangle> rectangle = portals.create(new NetherPortalAccess.Creation(
                    search.request.player(), search.request.origin(), candidate, 0));
            if (rectangle.isPresent()) {
                Optional<Location> destination = destination(search.request, rectangle.get());
                if (destination.isPresent()) {
                    retained.copyTo(search.request.chunks());
                    search.result.complete(destination);
                    return;
                }
            }
            create(search, candidates, index + 1);
        });
    }

    private List<Location> creationCandidates(Request request) {
        Location requested = request.requested();
        List<Location> candidates = new ArrayList<>(MAX_CREATION_CANDIDATES);
        if (canPrepare(request.player(), requested)) {
            candidates.add(requested);
        }
        ClaimChunk center = ClaimsService.chunk(requested);
        for (int radius = 0; radius <= MAX_CREATION_CHUNK_DISTANCE && candidates.size() < MAX_CREATION_CANDIDATES; radius++) {
            for (int x = -radius; x <= radius && candidates.size() < MAX_CREATION_CANDIDATES; x++) {
                addCandidate(request, candidates, new ClaimChunk(center.worldId(), center.x() + x, center.z() - radius));
                if (radius > 0) {
                    addCandidate(request, candidates, new ClaimChunk(center.worldId(), center.x() + x, center.z() + radius));
                }
            }
            for (int z = -radius + 1; z < radius && candidates.size() < MAX_CREATION_CANDIDATES; z++) {
                addCandidate(request, candidates, new ClaimChunk(center.worldId(), center.x() - radius, center.z() + z));
                addCandidate(request, candidates, new ClaimChunk(center.worldId(), center.x() + radius, center.z() + z));
            }
        }
        candidates.sort(Comparator.comparingDouble(candidate -> horizontalDistanceSquared(candidate, requested)));
        return candidates;
    }

    private void addCandidate(Request request, List<Location> candidates, ClaimChunk chunk) {
        if (candidates.size() >= MAX_CREATION_CANDIDATES || claims.manager().snapshot().cityAt(chunk).isPresent()) {
            return;
        }
        Location requested = request.requested();
        Location candidate = new Location(requested.getWorld(),
                Math.clamp(requested.getBlockX(), (chunk.x() << 4) + 3, (chunk.x() << 4) + 12), requested.getY(),
                Math.clamp(requested.getBlockZ(), (chunk.z() << 4) + 3, (chunk.z() << 4) + 12),
                requested.getYaw(), requested.getPitch());
        if (!candidates.contains(candidate) && canPrepare(request.player(), candidate)) {
            candidates.add(candidate);
        }
    }

    private boolean canPrepare(Player player, Location candidate) {
        World world = candidate.getWorld();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Location block = candidate.clone().add(x, 0, z);
                if (!world.getWorldBorder().isInside(block) || !claims.canTeleport(player, block)
                        || !claims.canPrepareArrival(player, block)) {
                    return false;
                }
            }
        }
        return true;
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private void load(Search search, Location center, int radius, Consumer<ClaimChunkRetention> action) {
        ClaimChunkRetention retained = new ClaimChunkRetention();
        search.result.whenComplete((destination, failure) -> retained.close());
        List<CompletableFuture<Void>> loading = new ArrayList<>();
        World world = center.getWorld();
        for (int x = (center.getBlockX() - radius) >> 4; x <= (center.getBlockX() + radius) >> 4; x++) {
            for (int z = (center.getBlockZ() - radius) >> 4; z <= (center.getBlockZ() + radius) >> 4; z++) {
                loading.add(retained.load(world, x, z));
            }
        }
        CompletableFuture.allOf(loading.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            if (failure != null) {
                search.result.completeExceptionally(failure);
            } else {
                schedule(search, center, () -> {
                    try {
                        action.accept(retained);
                    } finally {
                        retained.close();
                    }
                });
            }
        });
    }

    private void schedule(Search search, Location location, Runnable action) {
        if (search.result.isDone()) {
            return;
        }
        if (!J.runAt(location, () -> {
            try {
                if (active(search)) {
                    action.run();
                }
            } catch (RuntimeException | LinkageError failure) {
                search.result.completeExceptionally(failure);
            }
        })) {
            search.result.complete(Optional.empty());
        }
    }

    public record Request(Player player, Location origin, Location requested, int searchRadius,
                          boolean canCreate, BooleanSupplier active, ClaimChunkRetention chunks) {
        public Request {
            Objects.requireNonNull(player, "player");
            origin = Objects.requireNonNull(origin, "origin").clone();
            requested = Objects.requireNonNull(requested, "requested").clone();
            Objects.requireNonNull(origin.getWorld(), "origin world");
            Objects.requireNonNull(requested.getWorld(), "destination world");
            Objects.requireNonNull(active, "active");
            Objects.requireNonNull(chunks, "chunks");
            origin.checkFinite();
            requested.checkFinite();
            if (searchRadius < 0 || searchRadius > 1024) {
                throw new IllegalArgumentException("Portal search radius must be between 0 and 1024");
            }
        }

        @Override
        public Location origin() {
            return origin.clone();
        }

        @Override
        public Location requested() {
            return requested.clone();
        }
    }

    private static final class Search {
        private final Request request;
        private final CompletableFuture<Optional<Location>> result = new CompletableFuture<>();
        private final List<NetherPortalAccess.Rectangle> visited = new ArrayList<>();
        private int inspected;

        private Search(Request request) {
            this.request = request;
        }
    }
}
