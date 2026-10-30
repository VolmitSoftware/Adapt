package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.player.PlayerRespawnAccess;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimMovementListenerTest extends AdaptTestBase {
    private final UUID worldId = UUID.randomUUID();
    private final UUID founder = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private ClaimsService claims;
    private ClaimManager manager;
    private ClaimMovementListener listener;
    private ClaimSnapshot snapshot;
    private Player player;
    private World world;
    private ClaimTravelService travel;

    @BeforeEach
    void prepare() {
        claims = mock(ClaimsService.class);
        manager = mock(ClaimManager.class);
        player = mock(Player.class);
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(location(1608, 64, 1608));
        when(claims.enabled()).thenReturn(true);
        when(claims.manager()).thenReturn(manager);
        travel = mock(ClaimTravelService.class);
        when(claims.travel()).thenReturn(travel);
        ClaimBlock anchor = new ClaimBlock(worldId, 1608, 64, 1608);
        ClaimCity city = new ClaimCity(UUID.randomUUID(), "Oakwatch", founder, anchor, Set.of(member),
                ClaimSnapshot.footprint(anchor.chunk()), new ClaimRanks(true, 1, 1, 0, false), 0);
        snapshot = new ClaimSnapshot(Map.of(city.id(), city), Map.of());
        when(manager.snapshot()).thenReturn(snapshot);
        listener = new ClaimMovementListener(claims);
    }

    @Test
    void everyTeleportCauseUsesTheSameDestinationPolicy() {
        for (PlayerTeleportEvent.TeleportCause cause : PlayerTeleportEvent.TeleportCause.values()) {
            PlayerTeleportEvent event = new PlayerTeleportEvent(player, location(1607, 64, 1608),
                    location(1608, 64, 1608), cause);
            listener.onTeleport(event);
            assertThat(event.isCancelled()).as(cause.name()).isTrue();
        }
    }

    @Test
    void teleportingOutRemainsAllowed() {
        Location outside = location(0, 64, 0);
        when(claims.canTeleport(player, outside)).thenReturn(true);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, location(1608, 64, 1608), outside,
                PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.onTeleport(event);
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void forbiddenArrivalIsCancelledBeforeStartingSafeRedirection() {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, location(0, 64, 0),
                location(1608, 64, 1608), PlayerTeleportEvent.TeleportCause.COMMAND);
        listener.onTeleport(event);
        assertThat(event.isCancelled()).isTrue();
        verify(travel).redirect(event);
    }

    @Test
    void evenAnUnclaimedNetherSearchOriginNeedsSafePortalResolution() {
        Location requested = location(0, 64, 0);
        when(claims.canTeleport(player, requested)).thenReturn(true);
        PlayerPortalEvent event = new PlayerPortalEvent(player, location(800, 64, 800), requested,
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        listener.onPortal(event);
        assertThat(event.isCancelled()).isTrue();
        verify(travel).redirectPortal(event);
    }

    @Test
    void foliaPreservesAllowedNetherTravelWithoutStartingThePaperResolver() {
        Location requested = location(0, 64, 0);
        when(claims.canTeleport(player, requested)).thenReturn(true);
        PlayerPortalEvent event = new PlayerPortalEvent(player, location(800, 64, 800), requested,
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFoliaThreading).thenReturn(true);
            listener.onPortal(event);
            assertThat(event.isCancelled()).isFalse();
            verify(travel, never()).redirectPortal(any());
        }
    }

    @Test
    void foliaStillRejectsForbiddenNetherTargetsWithoutStartingThePaperResolver() {
        PlayerPortalEvent event = new PlayerPortalEvent(player, location(0, 64, 0),
                location(1608, 64, 1608), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFoliaThreading).thenReturn(true);
            listener.onPortal(event);
            assertThat(event.isCancelled()).isTrue();
            verify(travel, never()).redirectPortal(any());
        }
    }

    @Test
    void unrelatedTeleportInvalidatesAPreparedJourney() {
        when(travel.pending(player)).thenReturn(true);
        Location outside = location(0, 64, 0);
        when(claims.canTeleport(player, outside)).thenReturn(true);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, location(1608, 64, 1608), outside,
                PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.onTeleport(event);
        verify(travel).cancel(player);
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aRejectedFinalCommitCannotStartAnUnownedReplacementTeleport() {
        when(travel.pending(player)).thenReturn(true);
        when(travel.committing(player)).thenReturn(true);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, location(0, 64, 0),
                location(1608, 64, 1608), PlayerTeleportEvent.TeleportCause.COMMAND);
        listener.onTeleport(event);
        assertThat(event.isCancelled()).isTrue();
        verify(travel).cancel(player);
        verify(travel, never()).redirect(any());
    }

    @Test
    void playerPortalEntryUsesTheDestinationPolicy() {
        PlayerPortalEvent event = new PlayerPortalEvent(player, location(0, 64, 0),
                location(1608, 64, 1608), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        listener.onPortal(event);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void entityPortalCannotCarryANestedPlayerPassengerInside() {
        Entity vehicle = mock(Entity.class);
        Entity nested = mock(Entity.class);
        when(vehicle.getPassengers()).thenReturn(List.of(nested));
        when(nested.getPassengers()).thenReturn(List.of(player));
        EntityPortalEvent event = new EntityPortalEvent(vehicle, location(0, 64, 0), location(1608, 64, 1608));
        listener.onEntityPortal(event);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void anUnclaimedPortalSearchCannotCarryUnsupportedNestedRiders() {
        Entity vehicle = mock(Entity.class);
        Entity nested = mock(Entity.class);
        when(vehicle.getPassengers()).thenReturn(List.of(nested));
        when(nested.getPassengers()).thenReturn(List.of(player));
        Location outside = location(0, 64, 0);
        when(claims.canTeleport(player, outside)).thenReturn(true);
        EntityPortalEvent event = new EntityPortalEvent(vehicle, location(800, 64, 800), outside);
        listener.onEntityPortal(event);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void airborneVehicleSchedulesNestedPlayerRelocationOnThePlayerOwner() {
        Vehicle vehicle = mock(Vehicle.class);
        Entity nested = mock(Entity.class);
        when(vehicle.getPassengers()).thenReturn(List.of(nested));
        when(nested.getPassengers()).thenReturn(List.of(player));
        VehicleMoveEvent event = new VehicleMoveEvent(vehicle, location(1583.9, 80, 1608), location(1584.1, 80, 1608));
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            listener.onVehicleMove(event);
            scheduler.verify(() -> J.runEntity(eq(player), any(Runnable.class)));
        }
        verify(player, never()).leaveVehicle();
        verify(vehicle).setVelocity(any());
    }

    @Test
    void airborneBoundaryEntryIsRejectedBeforeCrossing() {
        PlayerMoveEvent event = new PlayerMoveEvent(player, location(1583.9, 80, 1608), location(1584.1, 80, 1608));
        listener.onMove(event);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void walkingThroughTheBoundaryRemainsAllowed() {
        when(player.isOnGround()).thenReturn(true);
        PlayerMoveEvent event = new PlayerMoveEvent(player, location(1583.9, 64, 1608), location(1584.1, 64, 1608));
        listener.onMove(event);
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void normalJumpsWithinCityLandRemainAllowed() {
        PlayerMoveEvent event = new PlayerMoveEvent(player, location(1599.9, 65, 1608), location(1600.1, 65, 1608));
        listener.onMove(event);
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void founderCannotGlideOrFlyInsideTheCity() {
        when(player.getUniqueId()).thenReturn(founder);
        PlayerToggleFlightEvent flight = new PlayerToggleFlightEvent(player, true);
        EntityToggleGlideEvent glide = new EntityToggleGlideEvent(player, true);
        listener.onFlight(flight);
        listener.onGlide(glide);
        assertThat(flight.isCancelled()).isTrue();
        assertThat(glide.isCancelled()).isTrue();
    }

    @Test
    void explicitBypassPermitsAirborneEntry() {
        when(claims.bypass(player)).thenReturn(true);
        PlayerMoveEvent event = new PlayerMoveEvent(player, location(1583.9, 80, 1608), location(1584.1, 80, 1608));
        listener.onMove(event);
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void fastMovementCannotSkipAcrossAnEntireCity() {
        assertThat(ClaimMovementListener.crossesClaim(snapshot, location(1500, 80, 1608), location(1700, 80, 1608))).isTrue();
        assertThat(ClaimMovementListener.crossesClaim(snapshot, location(1700, 80, 1608), location(1500, 80, 1608))).isTrue();
        assertThat(ClaimMovementListener.crossesClaim(snapshot, location(1500, 80, 1500), location(1700, 80, 1500))).isFalse();
    }

    @Test
    void tinyDiagonalIntersectionsCannotSkipAClaimedCorner() {
        assertThat(ClaimMovementListener.crossesClaim(snapshot,
                location(1583.999, 80, 1584.01), location(1584.01, 80, 1583.999))).isTrue();
    }

    @Test
    void negativeCoordinatesUseFloorChunkBoundaries() {
        ClaimBlock anchor = new ClaimBlock(worldId, -1608, 64, -1608);
        ClaimCity city = new ClaimCity(UUID.randomUUID(), "Northfield", founder, anchor, Set.of(),
                ClaimSnapshot.footprint(anchor.chunk()), new ClaimRanks(true, 1, 0, 0, false), 0);
        ClaimSnapshot negative = new ClaimSnapshot(Map.of(city.id(), city), Map.of());
        assertThat(ClaimMovementListener.crossesClaim(negative,
                location(-1632.01, 80, -1608), location(-1631.99, 80, -1608))).isTrue();
    }

    @Test
    void crossingAnUnclaimedGapAndReenteringTheSameCityIsEntry() {
        ClaimCity original = snapshot.cities().values().iterator().next();
        Set<ClaimChunk> chunks = new HashSet<>(original.chunks());
        chunks.add(new ClaimChunk(worldId, 103, 100));
        ClaimCity city = new ClaimCity(original.id(), original.name(), founder, original.anchor(), original.members(),
                chunks, original.ranks(), 0);
        ClaimSnapshot irregular = new ClaimSnapshot(Map.of(city.id(), city), Map.of());
        assertThat(ClaimMovementListener.crossesClaim(irregular, location(1624, 80, 1608), location(1656, 80, 1608))).isTrue();
    }

    @Test
    void membersReconnectWithoutRelocation() {
        when(player.getUniqueId()).thenReturn(member);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onJoin(event);
        assertThat(listener.relocating(player)).isFalse();
        verify(manager).setOnline(eq(member), eq(true), anyLong());
    }

    @Test
    void outsidersAreFrozenWhileAValidatedDestinationLoads() {
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(new CompletableFuture<Chunk>());
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onJoin(event);
        assertThat(listener.relocating(player)).isTrue();
        PlayerMoveEvent movement = new PlayerMoveEvent(player, location(1608, 64, 1608), location(1609, 64, 1608));
        listener.onMove(movement);
        assertThat(movement.isCancelled()).isTrue();
    }

    @Test
    void outsiderRespawnUsesSafeFallbackBeforeStartingIntendedWorldTravel() {
        Location intended = location(1608, 64, 1608);
        Location fallback = location(8.5, 64, 8.5);
        CompletableFuture<Boolean> arrival = new CompletableFuture<>();
        when(travel.transfer(any())).thenAnswer(call -> {
            ClaimTravelService.Request request = call.getArgument(0);
            assertThat(request.requested()).isEqualTo(intended);
            return arrival;
        });
        try (MockedConstruction<ClaimRespawnLocation> locations = mockConstruction(ClaimRespawnLocation.class,
                (resolver, context) -> when(resolver.find(player, intended)).thenReturn(Optional.of(fallback)))) {
            listener = new ClaimMovementListener(claims);
            PlayerRespawnEvent event = new PlayerRespawnEvent(player, intended, false, false, false,
                    PlayerRespawnEvent.RespawnReason.DEATH);
            listener.onRespawn(event);
            assertThat(event.getRespawnLocation()).isEqualTo(fallback);
            assertThat(listener.relocating(player)).isTrue();
            verify(travel, never()).transfer(any());
            PlayerPostRespawnEvent post = mock(PlayerPostRespawnEvent.class);
            when(post.getPlayer()).thenReturn(player);
            listener.onPostRespawn(post);
            assertThat(listener.relocating(player)).isTrue();
            arrival.complete(true);
            assertThat(listener.relocating(player)).isFalse();
        }
    }

    @Test
    void outsiderRespawnWithoutLoadedSafeFallbackDisconnectsBeforePlacement() {
        Location intended = location(1608, 64, 1608);
        PlayerRespawnAccess respawn = mock(PlayerRespawnAccess.class);
        try (MockedConstruction<ClaimRespawnLocation> locations = mockConstruction(ClaimRespawnLocation.class,
                (resolver, context) -> when(resolver.find(player, intended)).thenReturn(Optional.empty()));
             MockedStatic<NativeAdapters> natives = mockStatic(NativeAdapters.class)) {
            natives.when(() -> NativeAdapters.require(PlayerRespawnAccess.class)).thenReturn(respawn);
            listener = new ClaimMovementListener(claims);
            PlayerRespawnEvent event = new PlayerRespawnEvent(player, intended, false, false, false,
                    PlayerRespawnEvent.RespawnReason.DEATH);
            listener.onRespawn(event);
            verify(respawn).abortRespawn(eq(player), anyString());
            verify(travel, never()).transfer(any());
            assertThat(listener.relocating(player)).isFalse();
        }
    }

    @Test
    void ownTownRespawnStaysAtTheOriginalDestination() {
        Location intended = location(1608, 64, 1608);
        when(claims.canTeleport(player, intended)).thenReturn(true);
        try (MockedConstruction<ClaimRespawnLocation> locations = mockConstruction(ClaimRespawnLocation.class)) {
            listener = new ClaimMovementListener(claims);
            PlayerRespawnEvent event = new PlayerRespawnEvent(player, intended, true, false, false,
                    PlayerRespawnEvent.RespawnReason.DEATH);
            listener.onRespawn(event);
            assertThat(event.getRespawnLocation()).isEqualTo(intended);
            verify(locations.constructed().getFirst(), never()).find(any(), any());
            verify(player, never()).kickPlayer(any());
        }
    }

    @Test
    void quittingAfterRespawnPreparationClearsTheDeferredDestination() {
        Location intended = location(1608, 64, 1608);
        try (MockedConstruction<ClaimRespawnLocation> locations = mockConstruction(ClaimRespawnLocation.class,
                (resolver, context) -> when(resolver.find(player, intended)).thenReturn(Optional.of(location(8.5, 64, 8.5))))) {
            listener = new ClaimMovementListener(claims);
            listener.onRespawn(new PlayerRespawnEvent(player, intended, false, false, false,
                    PlayerRespawnEvent.RespawnReason.DEATH));
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(player);
            listener.onQuit(quit);
            PlayerPostRespawnEvent post = mock(PlayerPostRespawnEvent.class);
            when(post.getPlayer()).thenReturn(player);
            listener.onPostRespawn(post);
            verify(travel, never()).transfer(any());
            assertThat(listener.relocating(player)).isFalse();
        }
    }

    @Test
    void foliaRespawnsRetainTheExistingScheduledRelocationProtection() {
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRespawnLocation()).thenReturn(location(1608, 64, 1608));
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(J::isFoliaThreading).thenReturn(true);
            scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class), eq(1))).thenReturn(true);
            listener.onRespawn(event);
            assertThat(listener.relocating(player)).isTrue();
            scheduler.verify(() -> J.runEntity(eq(player), any(Runnable.class), eq(1)));
            verify(event, never()).setRespawnLocation(any());
        }
    }

    @Test
    void pendingRelocationBlocksMeleeAndProjectileAttacks() {
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(new CompletableFuture<Chunk>());
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        listener.onJoin(join);
        EntityDamageByEntityEvent melee = mock(EntityDamageByEntityEvent.class);
        when(melee.getDamager()).thenReturn(player);
        listener.onRelocationAttack(melee);
        verify(melee).setCancelled(true);
        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(player);
        EntityDamageByEntityEvent ranged = mock(EntityDamageByEntityEvent.class);
        when(ranged.getDamager()).thenReturn(projectile);
        listener.onRelocationAttack(ranged);
        verify(ranged).setCancelled(true);
    }

    @Test
    void physicallyPresentPlayersCanAttack() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(player);
        listener.onRelocationAttack(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    private Location location(double x, double y, double z) {
        return new Location(world, x, y, z);
    }
}
