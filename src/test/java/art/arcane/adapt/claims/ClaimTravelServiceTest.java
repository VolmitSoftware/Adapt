package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

class ClaimTravelServiceTest extends AdaptTestBase {
    private ClaimsService claims;
    private ClaimSafeLocation locations;
    private ClaimTravelService travel;
    private Player player;
    private World world;
    private Location source;
    private Location destination;

    @BeforeAll
    static void initializeSounds() {
        RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
            registry.when(RegistryAccess::registryAccess).thenReturn(access);
            Registry<Sound> sounds = Registry.SOUNDS;
            doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
            assertThat(Sound.BLOCK_PORTAL_TRAVEL).isNotNull();
        }
    }

    @BeforeEach
    void prepare() {
        claims = mock(ClaimsService.class);
        locations = mock(ClaimSafeLocation.class);
        player = mock(Player.class);
        world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getWorldBorder()).thenReturn(border);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.addPluginChunkTicket(anyInt(), anyInt(), eq(plugin))).thenReturn(true);
        when(claims.enabled()).thenReturn(true);
        when(claims.canTeleport(eq(player), any(Location.class))).thenReturn(true);
        when(locations.safeLandingOwned(any(Player.class), any(Location.class))).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWidth()).thenReturn(0.6);
        source = new Location(world, 0.5, 64, 0.5);
        destination = new Location(world, 200.5, 64, 200.5, 90, 12);
        when(player.getLocation()).thenAnswer(call -> source.clone());
        travel = new ClaimTravelService(claims, locations);
    }

    @AfterEach
    void releasePendingTravel() {
        try (MockedStatic<J> scheduler = scheduler()) {
            travel.close();
        }
    }

    @Test
    void approvalRetainsTheDestinationUntilCancellation() {
        try (MockedStatic<J> scheduler = scheduler()) {
            travel.prepare(request(destination)).join().orElseThrow();
            verify(world).addPluginChunkTicket(12, 12, plugin);
            verify(world, never()).removePluginChunkTicket(12, 12, plugin);
            travel.cancel(player);
            verify(world).removePluginChunkTicket(12, 12, plugin);
        }
    }

    @Test
    void aFailedChunkLoadReleasesOtherAlreadyRetainedChunks() {
        CompletableFuture<Chunk> failed = new CompletableFuture<>();
        when(world.getChunkAtAsync(0, 0, true)).thenReturn(failed);
        try (MockedStatic<J> scheduler = scheduler()) {
            CompletableFuture<Optional<ClaimTravelService.Prepared>> prepared = travel.prepare(request(source));
            verify(world).addPluginChunkTicket(-1, -1, plugin);
            failed.completeExceptionally(new IllegalStateException("Cannot load requested chunk"));
            assertThat(prepared.join()).isEmpty();
            verify(world).removePluginChunkTicket(-1, -1, plugin);
            assertThat(travel.pending(player)).isFalse();
        }
    }

    @Test
    void scaledHeadCrossingTheLogicalCeilingIsRecheckedAtCommit() {
        when(world.hasCeiling()).thenReturn(true);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getLogicalHeight()).thenReturn(128);
        destination.setY(126);
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            when(player.getWidth()).thenReturn(0.6 * 1.32);
            assertThat(travel.commit(prepared).join()).isFalse();
            verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void approvalDoesNotMoveThePlayerUntilCommitted() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            assertThat(prepared.destination()).isEqualTo(destination);
            assertThat(travel.pending(player)).isTrue();
            verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
            when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND)).thenAnswer(call -> {
                source = destination.clone();
                return true;
            });
            assertThat(travel.commit(prepared).join()).isTrue();
            assertThat(travel.pending(player)).isFalse();
            assertThat(travel.commit(prepared).join()).isFalse();
        }
    }

    @Test
    void identicalTriggersShareOnePendingSearch() {
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(loading);
        try (MockedStatic<J> scheduler = scheduler()) {
            CompletableFuture<Optional<ClaimTravelService.Prepared>> first = travel.prepare(request(destination));
            assertThat(travel.prepare(request(destination))).isSameAs(first);
            loading.complete(mock(Chunk.class));
            assertThat(first.join()).isPresent();
        }
    }

    @Test
    void aNewJourneyInvalidatesTheOldApproval() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared first = travel.prepare(request(destination)).join().orElseThrow();
            ClaimTravelService.Prepared second = travel.prepare(request(destination.clone().add(20, 0, 0)))
                    .join().orElseThrow();
            assertThat(travel.valid(first)).isFalse();
            assertThat(travel.commit(first).join()).isFalse();
            assertThat(travel.valid(second)).isTrue();
        }
    }

    @Test
    void cancellationOfAnOlderOwnedRequestCannotCancelTheReplacement() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Request first = request(destination);
            travel.prepare(first).join().orElseThrow();
            ClaimTravelService.Prepared second = travel.prepare(request(destination.clone().add(30, 0, 0)))
                    .join().orElseThrow();
            travel.cancel(first);
            assertThat(travel.valid(second)).isTrue();
        }
    }

    @Test
    void aDeniedDestinationIsResolvedInTheRequestedWorldBeforeApproval() {
        Location outside = destination.clone().add(32, 0, 0);
        when(claims.canTeleport(player, destination)).thenReturn(false);
        when(locations.findOutside(eq(destination), eq(ClaimArrivalProfile.NONE), any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(outside)));
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            assertThat(prepared.destination()).isEqualTo(outside);
            verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void sourceMovementAndDeathInvalidatePreparedTravel() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared first = travel.prepare(request(destination)).join().orElseThrow();
            source.add(1, 0, 0);
            assertThat(travel.commit(first).join()).isFalse();
            ClaimTravelService.Prepared second = travel.prepare(request(destination)).join().orElseThrow();
            when(player.isDead()).thenReturn(true);
            assertThat(travel.commit(second).join()).isFalse();
        }
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void delayedPortalResolutionToleratesMovementBelowPapersMoveEventThreshold() {
        CompletableFuture<Optional<Location>> searching = new CompletableFuture<>();
        try (MockedStatic<J> scheduler = scheduler();
             MockedConstruction<ClaimPortalTravel> portals = portalResolver(searching)) {
            when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)).thenAnswer(call -> {
                source = destination.clone();
                return true;
            });
            CompletableFuture<Boolean> result = travel.redirectPortal(new PlayerPortalEvent(player, source.clone(), destination,
                    PlayerTeleportEvent.TeleportCause.NETHER_PORTAL));
            source.add(0, 0, 0.0196);
            searching.complete(Optional.of(destination));
            assertThat(result.orTimeout(5, TimeUnit.SECONDS).join()).isTrue();
            verify(player).teleport(destination, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        }
    }

    @Test
    void delayedPortalResolutionRejectsMovementBeyondTheSourceEnvelope() {
        CompletableFuture<Optional<Location>> searching = new CompletableFuture<>();
        try (MockedStatic<J> scheduler = scheduler();
             MockedConstruction<ClaimPortalTravel> portals = portalResolver(searching)) {
            CompletableFuture<Boolean> result = travel.redirectPortal(new PlayerPortalEvent(player, source.clone(), destination,
                    PlayerTeleportEvent.TeleportCause.NETHER_PORTAL));
            source.add(0, 0, 0.126);
            searching.complete(Optional.of(destination));
            assertThat(result.orTimeout(5, TimeUnit.SECONDS).join()).isFalse();
            verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void sourceDriftAllowanceDoesNotRelaxApprovedOrActualArrival() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND)).thenAnswer(call -> {
                Location altered = destination.clone().add(0, 0, 0.0196);
                assertThat(travel.accepts(new PlayerTeleportEvent(player, source.clone(), altered,
                        PlayerTeleportEvent.TeleportCause.COMMAND))).isFalse();
                source = altered;
                return true;
            });
            assertThat(travel.commit(prepared).join()).isFalse();
        }
    }

    @Test
    void claimsAndTerrainAreCheckedAgainBeforeCommit() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared first = travel.prepare(request(destination)).join().orElseThrow();
            when(claims.canTeleport(player, destination)).thenReturn(false);
            assertThat(travel.commit(first).join()).isFalse();
            when(claims.canTeleport(player, destination)).thenReturn(true);
            ClaimTravelService.Prepared second = travel.prepare(request(destination)).join().orElseThrow();
            when(locations.safeLandingOwned(player, destination)).thenReturn(false);
            assertThat(travel.commit(second).join()).isFalse();
        }
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void cancellationAndShutdownDiscardLateChunkResults() {
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(loading, new CompletableFuture<>());
        try (MockedStatic<J> scheduler = scheduler()) {
            CompletableFuture<Optional<ClaimTravelService.Prepared>> first = travel.prepare(request(destination));
            travel.cancel(player);
            assertThat(first.join()).isEmpty();
            CompletableFuture<Optional<ClaimTravelService.Prepared>> second = travel.prepare(request(destination));
            travel.close();
            loading.complete(mock(Chunk.class));
            assertThat(second.join()).isEmpty();
            assertThat(travel.pending(player)).isFalse();
        }
    }

    @Test
    void cancellingTravelStopsTheRemainingDestinationSearch() {
        CompletableFuture<Optional<Location>> searching = new CompletableFuture<>();
        when(claims.canTeleport(player, destination)).thenReturn(false);
        when(locations.findOutside(eq(destination), eq(ClaimArrivalProfile.NONE), any())).thenReturn(searching);
        try (MockedStatic<J> scheduler = scheduler()) {
            CompletableFuture<Optional<ClaimTravelService.Prepared>> result = travel.prepare(request(destination));
            travel.cancel(player);
            assertThat(result.join()).isEmpty();
            assertThat(searching.isCancelled()).isTrue();
        }
    }

    @Test
    void cancellationDuringFinalChunkLoadingCannotLeaveANativeTeleportQueued() {
        List<Runnable> nativeTeleports = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            CompletableFuture<Chunk> loading = new CompletableFuture<>();
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
            when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(loading);
            when(player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND)).thenAnswer(call -> {
                CompletableFuture<Boolean> moved = new CompletableFuture<>();
                nativeTeleports.add(() -> {
                    source = destination.clone();
                    moved.complete(true);
                });
                return moved;
            });
            Location original = source.clone();
            CompletableFuture<Boolean> result = travel.commit(prepared);
            travel.cancel(player);
            loading.complete(mock(Chunk.class));
            nativeTeleports.forEach(Runnable::run);
            assertThat(result.join()).isFalse();
            assertThat(source).isEqualTo(original);
            verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void finalMovementWaitsForPapersNeighborChunkFootprint() {
        Location target = new Location(world, 13, 64, 8.5);
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(target)).join().orElseThrow();
            CompletableFuture<Chunk> neighbor = new CompletableFuture<>();
            when(world.isChunkLoaded(1, 0)).thenReturn(false);
            when(world.getChunkAtAsync(1, 0, true)).thenReturn(neighbor);
            when(player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND))
                    .thenReturn(CompletableFuture.completedFuture(false));
            when(player.teleport(target, PlayerTeleportEvent.TeleportCause.COMMAND)).thenAnswer(call -> {
                source = target.clone();
                return true;
            });
            CompletableFuture<Boolean> result = travel.commit(prepared);
            assertThat(result).isNotDone();
            verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
            when(world.isChunkLoaded(1, 0)).thenReturn(true);
            neighbor.complete(mock(Chunk.class));
            assertThat(result.orTimeout(5, TimeUnit.SECONDS).join()).isTrue();
            verify(player, never()).teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void unloadedFinalChunksAreRejectedBeforeTerrainReadsOrNativeMovement() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            clearInvocations(locations);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
            when(player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND)).thenAnswer(call -> {
                source = destination.clone();
                return CompletableFuture.completedFuture(true);
            });
            assertThat(travel.commit(prepared).orTimeout(5, TimeUnit.SECONDS).join()).isFalse();
            verify(player, never()).teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
            verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
            verify(locations, never()).safeLandingOwned(any(Player.class), any(Location.class));
        }
    }

    @Test
    void finalTeleportEventRejectsChangedClaimsEvenAfterPreparation() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND)).thenAnswer(call -> {
                when(claims.canTeleport(player, destination)).thenReturn(false);
                PlayerTeleportEvent event = new PlayerTeleportEvent(player, source, destination,
                        PlayerTeleportEvent.TeleportCause.COMMAND);
                assertThat(travel.accepts(event)).isFalse();
                return false;
            });
            assertThat(travel.commit(prepared).join()).isFalse();
        }
    }

    @Test
    void successfulNativeTeleportWithoutActualArrivalCannotCompleteTravel() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared prepared = travel.prepare(request(destination)).join().orElseThrow();
            when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND))
                    .thenReturn(true);
            assertThat(travel.commit(prepared).join()).isFalse();
        }
    }

    @Test
    void unsupportedMountedTravelNeverMovesOnlyPartOfTheGroup() {
        when(player.isInsideVehicle()).thenReturn(true);
        try (MockedStatic<J> scheduler = scheduler()) {
            assertThat(travel.prepare(request(destination)).join()).isEmpty();
        }
        verify(world, never()).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void aPortalTransferPreservesMotionAndAppliesThePlayerCooldownAfterArrival() {
        List<Throwable> errors = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler();
             MockedStatic<Adapt> logging = mockStatic(Adapt.class);
             MockedConstruction<ClaimPortalTravel> portals = mockConstruction(ClaimPortalTravel.class, (resolver, context) -> {
                 when(resolver.resolve(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(destination)));
                 when(resolver.safeDestination(eq(player), any())).thenReturn(true);
                 when(resolver.movement(player)).thenReturn(new Vector(1, 0.5, 0));
             })) {
            logging.when(() -> Adapt.error(any(Throwable.class))).thenAnswer(call -> {
                Throwable failure = call.getArgument(0);
                errors.add(failure);
                return null;
            });
            when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)).thenAnswer(call -> {
                source = destination.clone();
                return true;
            });
            PlayerPortalEvent event = new PlayerPortalEvent(player, source, destination,
                    PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
            assertThat(travel.redirectPortal(event).orTimeout(5, TimeUnit.SECONDS).join()).isTrue();
            assertThat(errors).isEmpty();
            verify(player).setPortalCooldown(10);
            verify(player).playSound(destination, Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 1, 1);
            ArgumentCaptor<Vector> motion = ArgumentCaptor.forClass(Vector.class);
            verify(player).setVelocity(motion.capture());
            assertThat(motion.getValue().distance(new Vector(0, 0.5, 1))).isLessThan(0.000001);
        }
    }

    @Test
    void resetDiscardsPendingWorkWithoutDisablingNewRequests() {
        try (MockedStatic<J> scheduler = scheduler()) {
            ClaimTravelService.Prepared first = travel.prepare(request(destination)).join().orElseThrow();
            travel.reset();
            assertThat(travel.valid(first)).isFalse();
            assertThat(travel.prepare(request(destination)).join()).isPresent();
        }
    }

    private ClaimTravelService.Request request(Location target) {
        return new ClaimTravelService.Request(player, target, PlayerTeleportEvent.TeleportCause.COMMAND,
                ClaimArrivalProfile.NONE);
    }

    private MockedConstruction<ClaimPortalTravel> portalResolver(CompletableFuture<Optional<Location>> searching) {
        return mockConstruction(ClaimPortalTravel.class, (resolver, context) -> {
            when(resolver.resolve(any())).thenReturn(searching);
            when(resolver.safeDestination(eq(player), any())).thenReturn(true);
            when(resolver.movement(player)).thenReturn(new Vector());
        });
    }

    private MockedStatic<J> scheduler() {
        MockedStatic<J> scheduler = mockStatic(J.class);
        scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return true;
        });
        scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return true;
        });
        scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class), anyInt())).thenReturn(true);
        return scheduler;
    }
}
