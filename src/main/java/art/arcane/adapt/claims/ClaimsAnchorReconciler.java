package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimPlot;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ClaimsAnchorReconciler implements Listener, AutoCloseable {
    private final ClaimsService claims;
    private final Map<ClaimBlock, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public ClaimsAnchorReconciler(ClaimsService claims) {
        this.claims = Objects.requireNonNull(claims);
    }

    @Override
    public void close() {
        closed = true;
        for (CompletableFuture<Void> validation : pending.values()) {
            validation.cancel(false);
        }
        pending.clear();
    }

    public void validateWorld(World world) {
        if (closed || !claims.enabled()) {
            return;
        }
        ClaimSnapshot snapshot = claims.manager().snapshot();
        for (ClaimCity city : snapshot.cities().values()) {
            if (city.anchor().worldId().equals(world.getUID())) {
                validate(world, new CityAnchor(city));
            }
        }
        for (ClaimPlot plot : snapshot.plots().values()) {
            if (plot.anchor().worldId().equals(world.getUID())) {
                validate(world, new PlotAnchor(plot));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        validateWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (closed || !claims.enabled()) {
            return;
        }
        Chunk chunk = event.getChunk();
        ClaimChunk position = new ClaimChunk(event.getWorld().getUID(), chunk.getX(), chunk.getZ());
        ClaimSnapshot snapshot = claims.manager().snapshot();
        ClaimCity city = snapshot.cityAt(position).orElse(null);
        if (city != null && city.anchor().chunk().equals(position)) {
            validate(event.getWorld(), new CityAnchor(city));
        }
        ClaimPlot plot = snapshot.plotAt(position).orElse(null);
        if (plot != null && plot.anchor().chunk().equals(position)) {
            validate(event.getWorld(), new PlotAnchor(plot));
        }
    }

    private void validate(World world, Anchor anchor) {
        if (closed || !claims.enabled() || !anchor.current(claims.manager())) {
            return;
        }
        ClaimBlock position = anchor.position();
        CompletableFuture<Void> validation = new CompletableFuture<>();
        if (pending.putIfAbsent(position, validation) != null) {
            return;
        }
        validation.orTimeout(30, TimeUnit.SECONDS).whenComplete((ignored, failure) -> {
            pending.remove(position, validation);
            if (failure != null && !validation.isCancelled()) {
                Adapt.error(new IllegalStateException("Cannot validate Claims anchor at " + position + "; ownership retained", failure));
            }
        });
        Location location = new Location(world, position.x(), position.y(), position.z());
        try {
            world.getChunkAtAsync(position.chunk().x(), position.chunk().z(), false).whenComplete((loaded, failure) -> {
                if (validation.isDone()) {
                    return;
                }
                try {
                    if (failure != null) {
                        validation.completeExceptionally(failure);
                    } else if (loaded == null) {
                        validation.completeExceptionally(new IllegalStateException("Anchor chunk is unavailable"));
                    } else if (!J.runAt(location, () -> inspectOwned(anchor, location, validation))) {
                        validation.completeExceptionally(new IllegalStateException("Anchor region scheduler rejected validation"));
                    }
                } catch (RuntimeException dispatchFailure) {
                    validation.completeExceptionally(dispatchFailure);
                }
            });
        } catch (RuntimeException failure) {
            validation.completeExceptionally(failure);
        }
    }

    private void inspectOwned(Anchor anchor, Location location, CompletableFuture<Void> validation) {
        if (validation.isDone()) {
            return;
        }
        if (closed || !claims.enabled() || !anchor.current(claims.manager())) {
            validation.complete(null);
            return;
        }
        try {
            if (!J.isOwnedByCurrentRegion(location)) {
                throw new IllegalStateException("Anchor validation is outside its owning region");
            }
            World world = Objects.requireNonNull(location.getWorld());
            ClaimBlock position = anchor.position();
            if (!world.isChunkLoaded(position.chunk().x(), position.chunk().z())) {
                throw new IllegalStateException("Anchor chunk unloaded before validation");
            }
            if (position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight()) {
                throw new IllegalStateException("Anchor is outside world build height");
            }
            if (world.getBlockAt(position.x(), position.y(), position.z()).getType() != Material.LODESTONE) {
                anchor.remove(claims.manager());
            }
            validation.complete(null);
        } catch (RuntimeException failure) {
            validation.completeExceptionally(failure);
        }
    }

    private sealed interface Anchor permits CityAnchor, PlotAnchor {
        ClaimBlock position();

        boolean current(ClaimManager manager);

        void remove(ClaimManager manager);
    }

    private record CityAnchor(ClaimCity expected) implements Anchor {
        @Override
        public ClaimBlock position() {
            return expected.anchor();
        }

        @Override
        public boolean current(ClaimManager manager) {
            ClaimCity current = manager.snapshot().cities().get(expected.id());
            return current != null && current.anchor().equals(expected.anchor());
        }

        @Override
        public void remove(ClaimManager manager) {
            manager.destroyAnchorIfUnchanged(expected);
        }
    }

    private record PlotAnchor(ClaimPlot expected) implements Anchor {
        @Override
        public ClaimBlock position() {
            return expected.anchor();
        }

        @Override
        public boolean current(ClaimManager manager) {
            return manager.snapshot().plots().get(expected.owner()) == expected;
        }

        @Override
        public void remove(ClaimManager manager) {
            manager.destroyAnchorIfUnchanged(expected);
        }
    }
}
