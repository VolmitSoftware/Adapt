package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ClaimTravelService implements AutoCloseable {
    private static final int TIMEOUT_TICKS = 200;
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final int PORTAL_COOLDOWN_TICKS = 10;
    private static final double SOURCE_POSITION_TOLERANCE_SQUARED = 0.125 * 0.125;

    private final ClaimsService claims;
    private final ClaimSafeLocation locations;
    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequences = new AtomicLong();
    private ClaimPortalTravel portals;
    private volatile boolean closed;

    public ClaimTravelService(ClaimsService claims, ClaimSafeLocation locations) {
        this.claims = Objects.requireNonNull(claims);
        this.locations = Objects.requireNonNull(locations);
    }

    @Override
    public void close() {
        closed = true;
        reset();
    }

    public void reset() {
        for (Pending transfer : pending.values()) {
            finish(transfer, false);
        }
    }

    public CompletableFuture<Optional<Prepared>> prepare(Request request) {
        return prepare(request, null);
    }

    public CompletableFuture<Boolean> transfer(Request request) {
        return prepare(request).thenCompose(approved -> approved.map(this::commit)
                .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    public CompletableFuture<Boolean> redirect(PlayerTeleportEvent event) {
        return transfer(new Request(event.getPlayer(), event.getTo(), event.getCause(), ClaimArrivalProfile.NONE));
    }

    public CompletableFuture<Boolean> redirectPortal(PlayerPortalEvent event) {
        Request request = new Request(event.getPlayer(), event.getTo(), event.getCause(), ClaimArrivalProfile.NONE);
        PortalOptions options = new PortalOptions(event.getFrom().clone(), event.getSearchRadius(), event.getCanCreatePortal());
        return prepare(request, options).thenCompose(approved -> approved.map(this::commit)
                .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    private CompletableFuture<Optional<Prepared>> prepare(Request request, PortalOptions options) {
        Player player = request.player();
        if (closed || !claims.enabled() || J.isFoliaThreading() || !available(player)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Pending previous = pending.get(player.getUniqueId());
        if (previous != null && previous.request.equals(request) && Objects.equals(previous.portal, options)
                && active(previous)) {
            return previous.approval;
        }
        cancel(player);
        Pending transfer = new Pending(request, sequences.incrementAndGet(), options);
        pending.put(player.getUniqueId(), transfer);
        if (!J.runEntity(player, () -> finish(transfer, false), TIMEOUT_TICKS)) {
            finish(transfer, false);
            return transfer.approval;
        }
        try {
            if (options == null) {
                resolve(transfer);
            } else {
                transfer.portalMotion = portals().movement(player);
                resolvePortal(transfer);
            }
        } catch (RuntimeException | LinkageError failure) {
            failed(transfer, failure);
        }
        return transfer.approval;
    }

    public CompletableFuture<Boolean> commit(Prepared prepared) {
        Pending transfer = pending.get(prepared.playerId());
        if (!matches(transfer, prepared) || !active(transfer)) {
            if (matches(transfer, prepared)) {
                finish(transfer, false);
            }
            return CompletableFuture.completedFuture(false);
        }
        if (transfer.committing) {
            return transfer.completion;
        }
        transfer.committing = true;
        if (!J.runAt(prepared.destination(), () -> commitOwned(transfer))) {
            finish(transfer, false);
        }
        return transfer.completion;
    }

    public boolean valid(Prepared prepared) {
        Pending transfer = pending.get(prepared.playerId());
        return matches(transfer, prepared) && active(transfer) && (transfer.portal == null
                ? permitted(transfer, prepared.destination()) : portals().safeDestination(transfer.request.player(), prepared.destination()));
    }

    public boolean pending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public boolean committing(Player player) {
        Pending transfer = pending.get(player.getUniqueId());
        return transfer != null && transfer.committing;
    }

    public void cancel(Player player) {
        Pending transfer = pending.get(player.getUniqueId());
        if (transfer != null) {
            finish(transfer, false);
        }
    }

    public void cancel(Request request) {
        Pending transfer = pending.get(request.player().getUniqueId());
        if (transfer != null && transfer.request == request) {
            finish(transfer, false);
        }
    }

    public boolean accepts(PlayerTeleportEvent event) {
        Pending transfer = pending.get(event.getPlayer().getUniqueId());
        return transfer != null && transfer.committing && active(transfer)
                && samePosition(transfer.prepared.destination(), event.getTo())
                && movementChunks(transfer).loaded()
                && ready(transfer);
    }

    private void resolvePortal(Pending transfer) {
        PortalOptions options = transfer.portal;
        CompletableFuture<Optional<Location>> search = portals().resolve(new ClaimPortalTravel.Request(
                transfer.request.player(), options.origin(), transfer.request.requested(), options.searchRadius(),
                options.canCreate(), () -> active(transfer), transfer.chunks));
        transfer.resolution = search;
        search.whenComplete((destination, failure) -> {
                    if (failure != null) {
                        failed(transfer, failure);
                    } else if (!J.runEntity(transfer.request.player(), () -> approve(transfer, destination))) {
                        finish(transfer, false);
                    }
                });
    }

    private void resolve(Pending transfer) {
        Location requested = transfer.request.requested();
        CompletableFuture<Void> loading = transfer.request.profile().load(transfer.request.player(), requested, transfer.chunks);
        transfer.resolution = loading;
        loading.whenComplete((chunk, failure) -> {
                    if (failure != null) {
                        failed(transfer, failure);
                    } else if (!J.runAt(requested, () -> resolveOwned(transfer))) {
                        finish(transfer, false);
                    }
                });
    }

    private void resolveOwned(Pending transfer) {
        if (!active(transfer)) {
            finish(transfer, false);
            return;
        }
        Location requested = transfer.request.requested();
        if (permitted(transfer, requested)) {
            approve(transfer, Optional.of(requested));
            return;
        }
        CompletableFuture<Optional<Location>> search = locations.findOutside(requested, transfer.request.profile(),
                candidate -> active(transfer) && permitted(transfer, candidate));
        transfer.resolution = search;
        search.whenComplete((destination, failure) -> {
                    if (failure != null) {
                        failed(transfer, failure);
                    } else if (!J.runEntity(transfer.request.player(), () -> loadApproval(transfer, destination))) {
                        finish(transfer, false);
                    }
                });
    }

    private void loadApproval(Pending transfer, Optional<Location> destination) {
        if (!active(transfer) || destination.isEmpty()) {
            finish(transfer, false);
            return;
        }
        Location approved = destination.get();
        CompletableFuture<Void> loading = transfer.request.profile().load(transfer.request.player(), approved, transfer.chunks);
        transfer.resolution = loading;
        loading.whenComplete((ignored, failure) -> {
            if (failure != null) {
                failed(transfer, failure);
            } else if (!J.runAt(approved, () -> approve(transfer,
                    permitted(transfer, approved) ? destination : Optional.empty()))) {
                finish(transfer, false);
            }
        });
    }

    private void approve(Pending transfer, Optional<Location> destination) {
        if (!active(transfer) || destination.isEmpty()) {
            finish(transfer, false);
            return;
        }
        transfer.prepared = new Prepared(transfer.request.player().getUniqueId(), transfer.token, destination.get());
        transfer.approval.complete(Optional.of(transfer.prepared));
    }

    private void commitOwned(Pending transfer) {
        if (!active(transfer)) {
            finish(transfer, false);
            return;
        }
        try {
            CompletableFuture<Void> loading = movementChunks(transfer).load(transfer.chunks);
            transfer.resolution = loading;
            loading.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    failed(transfer, failure);
                } else if (!J.runEntity(transfer.request.player(), () -> move(transfer))) {
                    finish(transfer, false);
                }
            });
        } catch (RuntimeException | LinkageError failure) {
            failed(transfer, failure);
        }
    }

    private void move(Pending transfer) {
        if (!active(transfer) || !movementChunks(transfer).loaded() || !ready(transfer)) {
            finish(transfer, false);
            return;
        }
        Player player = transfer.request.player();
        try {
            arrived(transfer, player.teleport(transfer.prepared.destination(), transfer.request.cause()));
        } catch (RuntimeException | LinkageError failure) {
            failed(transfer, failure);
        }
    }

    private MovementChunks movementChunks(Pending transfer) {
        Location destination = transfer.prepared.destination();
        double radius = transfer.request.player().getWidth() / 2.0;
        return new MovementChunks(destination.getWorld(),
                (Location.locToBlock(destination.getX() - radius - 1.0E-7) - 3) >> 4,
                (Location.locToBlock(destination.getZ() - radius - 1.0E-7) - 3) >> 4,
                (Location.locToBlock(destination.getX() + radius + 1.0E-7) + 3) >> 4,
                (Location.locToBlock(destination.getZ() + radius + 1.0E-7) + 3) >> 4);
    }

    private void arrived(Pending transfer, boolean moved) {
        Player player = transfer.request.player();
        boolean success = pending.get(player.getUniqueId()) == transfer && !closed && claims.enabled()
                && System.nanoTime() < transfer.deadline && available(player) && moved
                && samePosition(player.getLocation(), transfer.prepared.destination())
                && claims.canTeleport(player, player.getLocation());
        try {
            if (success) {
                player.setFallDistance(0);
                if (transfer.portal != null) {
                    double rotation = Math.toRadians(transfer.portal.origin().getYaw() - transfer.prepared.destination().getYaw());
                    player.setVelocity(transfer.portalMotion.clone().rotateAroundY(rotation));
                    player.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
                    player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 1, 1);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            Adapt.error(failure);
        } finally {
            finish(transfer, success);
        }
    }

    private boolean permitted(Pending transfer, Location location) {
        return sameLayer(transfer, location)
                && transfer.request.profile().permitted(claims, locations, transfer.request.player(), location);
    }

    private boolean sameLayer(Pending transfer, Location location) {
        return transfer.request.profile() != ClaimArrivalProfile.NONE
                || ClaimSafeLocation.sameLayer(transfer.request.requested(), location, transfer.request.player().getWidth() * 3.0);
    }

    private boolean ready(Pending transfer) {
        if (transfer.prepared == null) {
            return false;
        }
        return transfer.portal == null
                ? sameLayer(transfer, transfer.prepared.destination())
                    && transfer.request.profile().ready(claims, locations, transfer.request.player(), transfer.prepared.destination())
                : portals().safeDestination(transfer.request.player(), transfer.prepared.destination());
    }

    private ClaimPortalTravel portals() {
        if (portals == null) {
            portals = new ClaimPortalTravel(claims);
        }
        return portals;
    }

    private boolean active(Pending transfer) {
        Player player = transfer.request.player();
        if (closed || !claims.enabled() || pending.get(player.getUniqueId()) != transfer
                || System.nanoTime() >= transfer.deadline || !available(player)) {
            return false;
        }
        Location current = player.getLocation();
        return transfer.source.getWorld() == current.getWorld()
                && transfer.source.distanceSquared(current) <= SOURCE_POSITION_TOLERANCE_SQUARED;
    }

    private boolean available(Player player) {
        return player.isOnline() && !player.isDead() && !player.isInsideVehicle() && player.getPassengers().isEmpty();
    }

    private boolean matches(Pending transfer, Prepared prepared) {
        return transfer != null && transfer.prepared != null && transfer.prepared.equals(prepared);
    }

    private void failed(Pending transfer, Throwable failure) {
        if (pending.get(transfer.request.player().getUniqueId()) != transfer) {
            return;
        }
        Adapt.error(failure);
        finish(transfer, false);
    }

    private void finish(Pending transfer, boolean success) {
        pending.remove(transfer.request.player().getUniqueId(), transfer);
        CompletableFuture<?> resolution = transfer.resolution;
        if (resolution != null && !resolution.isDone()) {
            resolution.cancel(false);
        }
        transfer.chunks.close();
        transfer.approval.complete(Optional.empty());
        transfer.completion.complete(success);
    }

    private static boolean samePosition(Location first, Location second) {
        return first != null && second != null && first.getWorld() == second.getWorld()
                && first.distanceSquared(second) < 0.000001;
    }

    public record Request(Player player, Location requested, PlayerTeleportEvent.TeleportCause cause,
                          ClaimArrivalProfile profile) {
        public Request {
            Objects.requireNonNull(player);
            Objects.requireNonNull(requested.getWorld());
            Objects.requireNonNull(cause);
            Objects.requireNonNull(profile);
            requested.checkFinite();
            requested = requested.clone();
        }

        @Override
        public Location requested() {
            return requested.clone();
        }
    }

    public record Prepared(UUID playerId, long token, Location destination) {
        public Prepared {
            Objects.requireNonNull(playerId);
            destination = destination.clone();
        }

        @Override
        public Location destination() {
            return destination.clone();
        }
    }

    private record MovementChunks(World world, int minX, int minZ, int maxX, int maxZ) {
        private CompletableFuture<Void> load(ClaimChunkRetention retention) {
            List<CompletableFuture<Void>> loading = new ArrayList<>(4);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    loading.add(retention.load(world, x, z));
                }
            }
            return CompletableFuture.allOf(loading.toArray(new CompletableFuture<?>[0]));
        }

        private boolean loaded() {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.isChunkLoaded(x, z)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static final class Pending {
        private final Request request;
        private final Location source;
        private final long token;
        private final PortalOptions portal;
        private final long deadline = System.nanoTime() + TIMEOUT_NANOS;
        private final CompletableFuture<Optional<Prepared>> approval = new CompletableFuture<>();
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();
        private final ClaimChunkRetention chunks = new ClaimChunkRetention();
        private volatile Prepared prepared;
        private volatile boolean committing;
        private volatile CompletableFuture<?> resolution;
        private Vector portalMotion;

        private Pending(Request request, long token, PortalOptions portal) {
            this.request = request;
            this.source = request.player().getLocation().clone();
            this.token = token;
            this.portal = portal;
        }
    }

    private record PortalOptions(Location origin, int searchRadius, boolean canCreate) {
    }
}
