package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.player.PlayerRespawnAccess;
import art.arcane.volmlib.util.localization.MessageArgs;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimMovementListener implements Listener {
    private final ClaimsService claims;
    private final ClaimSafeLocation safeLocations;
    private final ClaimRespawnLocation respawnLocations;
    private final Set<UUID> relocating = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Location> respawns = new ConcurrentHashMap<>();

    public ClaimMovementListener(ClaimsService claims) {
        this.claims = Objects.requireNonNull(claims);
        safeLocations = new ClaimSafeLocation(claims);
        respawnLocations = new ClaimRespawnLocation(claims, safeLocations);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent) {
            return;
        }
        ClaimTravelService travel = claims.travel();
        if (travel.pending(event.getPlayer())) {
            if (travel.accepts(event)) {
                return;
            }
            boolean committing = travel.committing(event.getPlayer());
            travel.cancel(event.getPlayer());
            if (committing) {
                event.setCancelled(true);
                denied(event.getPlayer());
                return;
            }
        }
        Location destination = event.getTo();
        if (destination != null && !claims.canTeleport(event.getPlayer(), destination)) {
            event.setCancelled(true);
            denied(event.getPlayer());
            travel.redirect(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!claims.enabled() || claims.bypass(event.getPlayer()) || event.getTo() == null) {
            return;
        }
        if (!J.isFoliaThreading() && event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            event.setCancelled(true);
            claims.travel().redirectPortal(event);
        } else if (!claims.canTeleport(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            denied(event.getPlayer());
            claims.travel().redirect(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!claims.enabled()) {
            return;
        }
        Deque<Entity> passengers = new ArrayDeque<>(event.getEntity().getPassengers());
        while (!passengers.isEmpty()) {
            Entity passenger = passengers.removeFirst();
            if (passenger instanceof Player player && !claims.bypass(player)) {
                event.setCancelled(true);
                return;
            }
            passengers.addAll(passenger.getPassengers());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        Location destination = event.getTo();
        if (destination == null || !claims.enabled()) {
            return;
        }
        Deque<Entity> passengers = new ArrayDeque<>(event.getEntity().getPassengers());
        while (!passengers.isEmpty()) {
            Entity passenger = passengers.removeFirst();
            if (passenger instanceof Player player && !claims.canTeleport(player, destination)) {
                event.setCancelled(true);
                return;
            }
            passengers.addAll(passenger.getPassengers());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || !claims.enabled() || claims.bypass(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        Location destination = event.getTo();
        if (destination == null) {
            return;
        }
        if (relocating(player)) {
            event.setCancelled(true);
            return;
        }
        boolean flying = player.isFlying() || player.isGliding();
        boolean airborne = flying || !player.isOnGround();
        if (airborne && crossesClaim(claims.manager().snapshot(), event.getFrom(), destination)) {
            event.setCancelled(true);
            denied(player);
            return;
        }
        if (flying && claimed(destination)) {
            event.setCancelled(true);
            relocate(player, event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && claims.enabled() && !claims.bypass(event.getPlayer())
                && claimed(event.getPlayer().getLocation())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player
                && claims.enabled() && !claims.bypass(player) && claimed(player.getLocation())) {
            event.setCancelled(true);
            denied(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!claims.enabled() || event.getVehicle().isOnGround()
                || !crossesClaim(claims.manager().snapshot(), event.getFrom(), event.getTo())) {
            return;
        }
        Deque<Entity> passengers = new ArrayDeque<>(event.getVehicle().getPassengers());
        while (!passengers.isEmpty()) {
            Entity passenger = passengers.removeFirst();
            if (passenger instanceof Player player && !claims.bypass(player)) {
                event.getVehicle().setVelocity(new Vector());
                Location origin = event.getFrom().clone();
                J.runEntity(player, () -> relocateFromVehicle(player, origin));
            }
            passengers.addAll(passenger.getPassengers());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        claims.manager().setOnline(player.getUniqueId(), true, System.currentTimeMillis());
        validatePlayer(player);
    }

    public void validatePlayer(Player player) {
        if (claims.enabled() && !claims.bypass(player)
                && (outsider(player, player.getLocation())
                || (claimed(player.getLocation()) && (player.isFlying() || player.isGliding())))) {
            relocate(player, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        claims.manager().setOnline(event.getPlayer().getUniqueId(), false, System.currentTimeMillis());
        respawns.remove(event.getPlayer().getUniqueId());
        relocating.remove(event.getPlayer().getUniqueId());
        claims.travel().cancel(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        respawns.remove(event.getEntity().getUniqueId());
        relocating.remove(event.getEntity().getUniqueId());
        claims.travel().cancel(event.getEntity());
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        claims.resetTravel();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location intended = event.getRespawnLocation().clone();
        respawns.remove(player.getUniqueId());
        relocating.remove(player.getUniqueId());
        if (!claims.enabled() || claims.canTeleport(player, intended)) {
            return;
        }
        if (J.isFoliaThreading()) {
            relocating.add(player.getUniqueId());
            if (!J.runEntity(player, () -> {
                relocating.remove(player.getUniqueId());
                relocate(player, intended);
            }, 1)) {
                relocating.remove(player.getUniqueId());
            }
            return;
        }
        Optional<Location> fallback = respawnLocations.find(player, intended);
        if (fallback.isEmpty()) {
            NativeAdapters.require(PlayerRespawnAccess.class).abortRespawn(player,
                    AdaptLanguage.text(player.getUniqueId(), ClaimsMessages.RELOCATION_FAILED, MessageArgs.empty()));
            return;
        }
        event.setRespawnLocation(fallback.get());
        respawns.put(player.getUniqueId(), intended);
        relocating.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        Location intended = respawns.get(player.getUniqueId());
        if (intended == null) {
            return;
        }
        try {
            claims.travel().transfer(new ClaimTravelService.Request(player, intended,
                    PlayerTeleportEvent.TeleportCause.PLUGIN, ClaimArrivalProfile.NONE)).whenComplete((moved, failure) -> {
                if (failure != null) {
                    Adapt.error(failure);
                }
                finishRespawn(player.getUniqueId(), intended);
            });
        } catch (RuntimeException | LinkageError failure) {
            Adapt.error(failure);
            finishRespawn(player.getUniqueId(), intended);
        }
    }

    public boolean relocating(Player player) {
        return relocating.contains(player.getUniqueId()) || claims.travel().pending(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRelocationDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && relocating(player)
                && (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRelocationAttack(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker instanceof Player player && relocating(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (relocating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (relocating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && relocating(player)) {
            event.setCancelled(true);
        }
    }

    static boolean crossesClaim(ClaimSnapshot snapshot, Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || from.getWorld() != to.getWorld()) {
            return to.getWorld() != null && snapshot.cityAt(ClaimsService.chunk(to)).isPresent();
        }
        ClaimChunk origin = ClaimsService.chunk(from);
        ClaimChunk destination = ClaimsService.chunk(to);
        int x = origin.x();
        int z = origin.z();
        int steps = Math.abs(destination.x() - x) + Math.abs(destination.z() - z);
        if (steps > 4096) {
            return true;
        }
        UUID world = from.getWorld().getUID();
        ClaimCity previous = snapshot.cityAt(origin).orElse(null);
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        int directionX = Double.compare(deltaX, 0);
        int directionZ = Double.compare(deltaZ, 0);
        double incrementX = directionX == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(deltaX);
        double incrementZ = directionZ == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(deltaZ);
        double boundaryX = directionX == 0 ? Double.POSITIVE_INFINITY
                : ((directionX > 0 ? (x + 1) * 16.0 : x * 16.0) - from.getX()) / deltaX;
        double boundaryZ = directionZ == 0 ? Double.POSITIVE_INFINITY
                : ((directionZ > 0 ? (z + 1) * 16.0 : z * 16.0) - from.getZ()) / deltaZ;
        while (x != destination.x() || z != destination.z()) {
            if (boundaryX < boundaryZ) {
                x += directionX;
                boundaryX += incrementX;
            } else if (boundaryZ < boundaryX) {
                z += directionZ;
                boundaryZ += incrementZ;
            } else {
                x += directionX;
                z += directionZ;
                boundaryX += incrementX;
                boundaryZ += incrementZ;
            }
            ClaimCity crossed = snapshot.cityAt(new ClaimChunk(world, x, z)).orElse(null);
            if (crossed != null && (previous == null || !previous.id().equals(crossed.id()))) {
                return true;
            }
            previous = crossed;
        }
        return false;
    }

    private boolean outsider(Player player, Location location) {
        ClaimCity city = claims.manager().snapshot().cityAt(ClaimsService.chunk(location)).orElse(null);
        return city != null && !city.founder().equals(player.getUniqueId()) && !city.members().contains(player.getUniqueId());
    }

    private void finishRespawn(UUID playerId, Location intended) {
        respawns.computeIfPresent(playerId, (id, current) -> {
            if (current != intended) {
                return current;
            }
            relocating.remove(playerId);
            return null;
        });
    }

    private boolean claimed(Location location) {
        return claims.manager().snapshot().cityAt(ClaimsService.chunk(location)).isPresent();
    }

    private void relocate(Player player, Location origin) {
        if (!relocating.add(player.getUniqueId())) {
            return;
        }
        player.setVelocity(new Vector());
        safeLocations.findOutside(player, origin.clone()).whenComplete((destination, failure) -> {
            if (failure != null) {
                Adapt.error(failure);
            }
            Optional<Location> target = failure == null ? destination : Optional.empty();
            J.runEntity(player, () -> finishRelocation(player, target));
        });
    }

    private void relocateFromVehicle(Player player, Location origin) {
        if (!claims.enabled() || claims.bypass(player)) {
            return;
        }
        player.leaveVehicle();
        relocate(player, origin);
    }

    private void finishRelocation(Player player, Optional<Location> destination) {
        if (!player.isOnline() || !claims.enabled()) {
            relocating.remove(player.getUniqueId());
            return;
        }
        if (destination.isEmpty() || claimed(destination.get())) {
            player.kickPlayer(AdaptLanguage.text(player.getUniqueId(), ClaimsMessages.RELOCATION_FAILED, MessageArgs.empty()));
            return;
        }
        player.teleportAsync(destination.get(), PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((moved, failure) -> {
            if (failure != null) {
                Adapt.error(failure);
            }
            J.runEntity(player, () -> {
                if (failure == null && Boolean.TRUE.equals(moved)) {
                    player.setFallDistance(0);
                    relocating.remove(player.getUniqueId());
                } else {
                    player.kickPlayer(AdaptLanguage.text(player.getUniqueId(), ClaimsMessages.RELOCATION_FAILED, MessageArgs.empty()));
                }
            });
        });
    }

    private void denied(Player player) {
        player.sendActionBar(AdaptLanguage.text(player.getUniqueId(), ClaimsMessages.ENTRY_DENIED, MessageArgs.empty()));
    }
}
