package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.portal.NetherPortalAccess;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Axis;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimPortalTravelTest extends AdaptTestBase {
    private ClaimsService claims;
    private NetherPortalAccess access;
    private Player player;
    private World originWorld;
    private World world;
    private Location origin;
    private Location requested;
    private ClaimPortalTravel travel;
    private ClaimChunkRetention retained;

    @BeforeAll
    static void initializeBlockRegistry() {
        RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
            registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
            Registry<BlockType> blocks = Registry.BLOCK;
            doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).get(any(NamespacedKey.class));
            doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).getOrThrow(any(Key.class));
            when(Material.STONE.asBlockType().isSolid()).thenReturn(true);
            when(Material.AIR.asBlockType().isAir()).thenReturn(true);
        }
    }

    @BeforeEach
    void prepare() {
        claims = mock(ClaimsService.class);
        ClaimManager manager = mock(ClaimManager.class);
        access = mock(NetherPortalAccess.class);
        player = mock(Player.class);
        originWorld = mock(World.class);
        world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.addPluginChunkTicket(anyInt(), anyInt(), eq(plugin))).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getWidth()).thenReturn(0.6);
        when(claims.enabled()).thenReturn(true);
        when(claims.manager()).thenReturn(manager);
        when(manager.snapshot()).thenReturn(new ClaimSnapshot(Map.of(), Map.of()));
        when(claims.canTeleport(eq(player), any(Location.class))).thenReturn(true);
        when(claims.canPrepareArrival(eq(player), any(Location.class))).thenReturn(true);
        when(access.loadSearch(any(Location.class), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        when(access.find(any(Location.class), anyInt())).thenReturn(List.of());
        when(access.create(any(NetherPortalAccess.Creation.class))).thenReturn(Optional.empty());
        when(access.clearDestination(eq(player), any(Location.class))).thenReturn(true);
        terrain(64, Material.STONE);
        origin = new Location(originWorld, 800, 70, 800);
        requested = new Location(world, 100, 70, 100);
        travel = new ClaimPortalTravel(claims, access);
        retained = new ClaimChunkRetention();
    }

    @AfterEach
    void releaseRetainedChunks() {
        try (MockedStatic<J> scheduler = scheduler()) {
            retained.close();
        }
    }

    @Test
    void scaledPortalArrivalsCheckEveryOccupiedClaimColumn() {
        when(player.getWidth()).thenReturn(0.6 * 1.32);
        when(claims.canTeleport(eq(player), any(Location.class))).thenAnswer(call ->
                ((Location) call.getArgument(1)).getBlockX() < 112);
        assertThat(travel.safeDestination(player, new Location(world, 111.65, 65, 100.5))).isFalse();
    }

    @Test
    void scaledPortalArrivalsCheckHazardsAtTheFullHeadHeight() {
        when(player.getWidth()).thenReturn(0.6 * 1.32);
        Block fire = mock(Block.class);
        when(fire.getType()).thenReturn(Material.FIRE);
        when(world.getBlockAt(100, 67, 100)).thenReturn(fire);
        assertThat(travel.safeDestination(player, new Location(world, 100.5, 65, 100.5))).isFalse();
    }

    @Test
    void permittedExistingPortalIsReusedWithoutRescalingOrCreatingBlocks() {
        Location portal = new Location(world, 108, 65, 101);
        Location destination = new Location(world, 108.5, 65, 101.5);
        NetherPortalAccess.Rectangle rectangle = new NetherPortalAccess.Rectangle(world, 108, 65, 101, Axis.X, 2, 3);
        when(access.find(requested, 128)).thenReturn(List.of(portal));
        when(access.inspect(portal)).thenReturn(Optional.of(rectangle));
        when(access.destination(player, origin, rectangle)).thenReturn(destination);

        assertThat(resolve(true)).contains(destination);
        verify(access).loadSearch(requested, 128);
        verify(access, never()).create(any());
    }

    @Test
    void existingPortalWhoseFrameCrossesATownIsSkipped() {
        Location denied = new Location(world, 15, 65, 100);
        Location allowed = new Location(world, 40, 65, 100);
        NetherPortalAccess.Rectangle first = new NetherPortalAccess.Rectangle(world, 15, 65, 100, Axis.X, 2, 3);
        NetherPortalAccess.Rectangle second = new NetherPortalAccess.Rectangle(world, 40, 65, 100, Axis.X, 2, 3);
        when(access.find(requested, 128)).thenReturn(List.of(denied, allowed));
        when(access.inspect(denied)).thenReturn(Optional.of(first));
        when(access.inspect(allowed)).thenReturn(Optional.of(second));
        when(claims.canTeleport(eq(player), any(Location.class)))
                .thenAnswer(call -> ((Location) call.getArgument(1)).getBlockX() != 17);
        when(access.destination(player, origin, second)).thenReturn(allowed);

        assertThat(resolve(true)).contains(allowed);
        verify(access, never()).destination(player, origin, first);
    }

    @Test
    void finalCollisionAdjustedExitMustStillPassTheClaimPolicy() {
        Location portal = new Location(world, 100, 65, 100);
        Location deniedExit = new Location(world, 102, 65, 100);
        NetherPortalAccess.Rectangle rectangle = new NetherPortalAccess.Rectangle(world, 100, 65, 100, Axis.Z, 2, 3);
        when(access.find(requested, 128)).thenReturn(List.of(portal));
        when(access.inspect(portal)).thenReturn(Optional.of(rectangle));
        when(access.destination(player, origin, rectangle)).thenReturn(deniedExit);
        when(claims.canTeleport(player, deniedExit)).thenReturn(false);

        assertThat(resolve(false)).isEmpty();
        verify(access, never()).create(any());
    }

    @Test
    void portalCreationDisabledByTheOriginalEventRemainsDisabled() {
        assertThat(resolve(false)).isEmpty();
        verify(access, never()).create(any());
    }

    @Test
    void creationMovesTheEntireNativeFootprintBeyondTheDeniedChunks() {
        when(claims.canTeleport(eq(player), any(Location.class)))
                .thenAnswer(call -> ((Location) call.getArgument(1)).getBlockX() >= 112);
        NetherPortalAccess.Rectangle rectangle = new NetherPortalAccess.Rectangle(world, 115, 65, 100, Axis.X, 2, 3);
        Location destination = new Location(world, 115.5, 65, 100.5);
        when(access.create(any(NetherPortalAccess.Creation.class))).thenAnswer(call -> {
            NetherPortalAccess.Creation creation = call.getArgument(0);
            assertThat(creation.radius()).isZero();
            assertThat(creation.target().getBlockX()).isGreaterThanOrEqualTo(115);
            assertThat(creation.player()).isSameAs(player);
            return Optional.of(rectangle);
        });
        when(access.destination(player, origin, rectangle)).thenReturn(destination);

        assertThat(resolve(true)).contains(destination);
    }

    @Test
    void failedNativeCreationIsBoundedAndNeverProducesAnArrival() {
        assertThat(resolve(true)).isEmpty();
        verify(access, atMost(32)).create(any());
    }

    @Test
    void portalArrivalRetainsSafeRelativeHeightInsteadOfRequiringAirAtTheFeet() {
        Location elevated = new Location(world, 100.5, 66.5, 100.5);
        assertThat(travel.safeDestination(player, elevated)).isTrue();
    }

    @Test
    void portalArrivalRejectsUnsafeLongDropsAndDamagingSupport() {
        assertThat(travel.safeDestination(player, new Location(world, 100.5, 70, 100.5))).isFalse();
        terrain(64, Material.MAGMA_BLOCK);
        assertThat(travel.safeDestination(player, new Location(world, 100.5, 65, 100.5))).isFalse();
    }

    @Test
    void tallPortalExitFallsBackToItsSupportedBottomWithoutChangingRotation() {
        Location portal = new Location(world, 100, 65, 100);
        Location high = new Location(world, 100.5, 76, 100.5, 43, -12);
        NetherPortalAccess.Rectangle rectangle = new NetherPortalAccess.Rectangle(world, 100, 65, 100, Axis.X, 2, 18);
        when(access.find(requested, 128)).thenReturn(List.of(portal));
        when(access.inspect(portal)).thenReturn(Optional.of(rectangle));
        when(access.destination(player, origin, rectangle)).thenReturn(high);

        assertThat(resolve(false)).contains(new Location(world, 100.5, 65, 100.5, 43, -12));
    }

    @Test
    void nativeCollisionVetoIsAppliedBeforeReadingDestinationTerrain() {
        Location blocked = new Location(world, 100.5, 65, 100.5);
        when(access.clearDestination(player, blocked)).thenReturn(false);
        assertThat(travel.safeDestination(player, blocked)).isFalse();
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void staleRequestDoesNotInspectOrCreateAfterChunkLoading() {
        CompletableFuture<Void> loading = new CompletableFuture<>();
        AtomicBoolean active = new AtomicBoolean(true);
        when(access.loadSearch(any(Location.class), anyInt())).thenReturn(loading);
        try (MockedStatic<J> scheduler = scheduler()) {
            CompletableFuture<Optional<Location>> result = travel.resolve(new ClaimPortalTravel.Request(
                    player, origin, requested, 128, true, active::get, retained));
            active.set(false);
            loading.complete(null);
            assertThat(result.join()).isEmpty();
        }
        verify(access, never()).find(any(), anyInt());
        verify(access, never()).create(any());
    }

    private Optional<Location> resolve(boolean create) {
        try (MockedStatic<J> scheduler = scheduler()) {
            return travel.resolve(new ClaimPortalTravel.Request(player, origin, requested, 128, create, () -> true, retained)).join();
        }
    }

    private void terrain(int floorY, Material material) {
        Block floor = mock(Block.class);
        Block portal = mock(Block.class);
        when(floor.getType()).thenReturn(material);
        when(portal.getType()).thenReturn(Material.NETHER_PORTAL);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(1) <= floorY ? floor : portal);
    }

    private MockedStatic<J> scheduler() {
        MockedStatic<J> scheduler = mockStatic(J.class);
        scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return true;
        });
        return scheduler;
    }
}
