package art.arcane.adapt.claims;

import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimSafeLocationTest {
    private Player player;
    private ClaimsService claims;
    private World world;
    private ClaimSafeLocation locations;
    private Location destination;
    private Block head;
    private Block floor;

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
        player = mock(Player.class);
        when(player.getWidth()).thenReturn(0.6);
        claims = mock(ClaimsService.class);
        ClaimManager manager = mock(ClaimManager.class);
        world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getWorldBorder()).thenReturn(border);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(claims.manager()).thenReturn(manager);
        when(claims.enabled()).thenReturn(true);
        when(manager.snapshot()).thenReturn(new ClaimSnapshot(Map.of(), Map.of()));
        destination = new Location(world, 0.5, 64, 0.5);
        Block feet = mock(Block.class);
        head = mock(Block.class);
        floor = mock(Block.class);
        when(feet.getType()).thenReturn(Material.AIR);
        when(head.getType()).thenReturn(Material.AIR);
        when(floor.getType()).thenReturn(Material.STONE);
        collision(floor, 1);
        when(world.getBlockAt(0, 64, 0)).thenReturn(feet);
        when(world.getBlockAt(0, 65, 0)).thenReturn(head);
        when(world.getBlockAt(0, 63, 0)).thenReturn(floor);
        locations = new ClaimSafeLocation(claims);
    }

    @Test
    void clearHeadroomAndSolidFloorAreRequired() {
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(destination)).thenReturn(true);
            assertThat(locations.safeOwned(player, destination)).isTrue();
            when(head.getType()).thenReturn(Material.STONE);
            assertThat(locations.safeOwned(player, destination)).isFalse();
            when(head.getType()).thenReturn(Material.AIR);
            when(floor.getType()).thenReturn(Material.AIR);
            assertThat(locations.safeOwned(player, destination)).isFalse();
        }
    }

    @Test
    void landingRequiresSupportWithoutIntersectingTheFloorShape() {
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(destination)).thenReturn(true);
            collision(floor, 1.5);
            assertThat(locations.safeLandingOwned(player, destination)).isFalse();
            collision(floor, 0.5);
            assertThat(locations.safeLandingOwned(player, destination)).isFalse();
            collision(floor, 1);
            assertThat(locations.safeLandingOwned(player, destination)).isTrue();
        }
    }

    @Test
    void fractionalArrivalHeightMustClearTheEntireStandingBody() {
        Location fractional = destination.clone().add(0, 0.5, 0);
        Block ceiling = mock(Block.class);
        when(ceiling.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(0, 66, 0)).thenReturn(ceiling);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(fractional)).thenReturn(true);
            assertThat(locations.safeLandingOwned(player, fractional)).isFalse();
        }
    }

    @Test
    void damagingOrFluidSurfacesAreNeverSafeLandingFloors() {
        for (Material material : new Material[]{Material.LAVA, Material.WATER, Material.CACTUS,
                Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
                Material.POINTED_DRIPSTONE, Material.SWEET_BERRY_BUSH, Material.END_PORTAL}) {
            assertThat(ClaimSafeLocation.safeFloor(material)).as(material.name()).isFalse();
        }
    }

    @Test
    void terrainIsNeverReadOffItsOwningRegion() {
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            assertThat(locations.safeOwned(player, destination)).isFalse();
        }
        verify(world, never()).getBlockAt(0, 64, 0);
    }

    @Test
    void caveTravelStaysNearTheRequestedHeightInsteadOfTheSurface() {
        terrain(y -> y == 64 || y == 65 || y >= 128);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(127);
        Location found = find(destination).orElseThrow();
        assertThat(found.getY()).isEqualTo(64);
    }

    @Test
    void redirectedTravelRetainsTheRequestedRotation() {
        terrain(y -> y >= 64);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(63);
        destination.setYaw(43);
        destination.setPitch(-12);
        Location found = find(destination).orElseThrow();
        assertThat(found.getYaw()).isEqualTo(43);
        assertThat(found.getPitch()).isEqualTo(-12);
    }

    @Test
    void brackenSearchPreservesHeightAndFractionsAboveTheBuildLimit() {
        terrain(y -> true);
        Location origin = new Location(world, -0.58, 700.0025, -0.89, 42, 12);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
                ((Runnable) call.getArgument(1)).run();
                return true;
            });
            Location found = locations.findOutside(origin, ClaimArrivalProfile.BRACKEN_FALLING, candidate -> true)
                    .join().orElseThrow();
            assertThat(found.getY()).isEqualTo(origin.getY());
            assertThat(found.getX() - found.getBlockX()).isCloseTo(0.42, Offset.offset(0.000001));
            assertThat(found.getZ() - found.getBlockZ()).isCloseTo(0.11, Offset.offset(0.000001));
            assertThat(found.getYaw()).isEqualTo(42);
            assertThat(found.getPitch()).isEqualTo(12);
        }
    }

    @Test
    void searchingBelowANetherCeilingCannotChooseItsRoof() {
        terrain(y -> y >= 128);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(256);
        when(world.hasCeiling()).thenReturn(true);
        when(world.getLogicalHeight()).thenReturn(128);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(127);
        assertThat(find(new Location(world, 0.5, 125, 0.5))).isEmpty();
    }

    @Test
    void scaledStandingBodyCannotCrossTheLogicalCeiling() {
        terrain(y -> y >= 126);
        when(player.getWidth()).thenReturn(0.6 * 1.32);
        when(world.getMinHeight()).thenReturn(125);
        when(world.getMaxHeight()).thenReturn(256);
        when(world.hasCeiling()).thenReturn(true);
        when(world.getLogicalHeight()).thenReturn(3);
        assertThat(find(new Location(world, 0.5, 126, 0.5))).isEmpty();
    }

    @Test
    void redirectionPrefersTheNearSideOfTheTown() {
        terrain(y -> y >= 64);
        ClaimBlock anchor = new ClaimBlock(world.getUID(), 8, 64, 8);
        ClaimCity city = new ClaimCity(UUID.randomUUID(), "Meadow", UUID.randomUUID(), anchor, Set.of(),
                ClaimSnapshot.footprint(anchor.chunk()), new ClaimRanks(true, 1, 1, 0, false), 0);
        when(claims.manager().snapshot()).thenReturn(new ClaimSnapshot(Map.of(city.id(), city), Map.of()));
        Location found = find(new Location(world, 31.5, 64, 8.5)).orElseThrow();
        assertThat(found.getX()).isEqualTo(34.5);
        assertThat(found.getZ()).isEqualTo(8.5);
    }

    @Test
    void customTravelAboveLogicalHeightKeepsItsIntendedLayer() {
        terrain(y -> y == 247 || y == 248 || y >= 301);
        when(world.hasCeiling()).thenReturn(true);
        when(world.getLogicalHeight()).thenReturn(100);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(300);
        Location found = find(new Location(world, 0.5, 247, 0.5)).orElseThrow();
        assertThat(found.getY()).isEqualTo(247);
    }

    @Test
    void timedOutSearchDoesNotScheduleTerrainInspectionWhenChunkLoadingFinishes() {
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(loading);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            CompletableFuture<Optional<Location>> result = locations.findOutside(player, destination);
            result.completeExceptionally(new TimeoutException());
            loading.complete(mock(Chunk.class));
            scheduler.verifyNoInteractions();
        }
        verify(world).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void completedSearchDoesNotInspectTerrainInAnAlreadyQueuedRegionTask() {
        CompletableFuture<Chunk> loaded = CompletableFuture.completedFuture(mock(Chunk.class));
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(loaded);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
                queued.set(call.getArgument(1));
                return true;
            });
            CompletableFuture<Optional<Location>> result = locations.findOutside(player, destination);
            result.cancel(false);
            queued.get().run();
        }
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt());
        verify(world).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());
    }

    private void terrain(IntPredicate airAt) {
        Block air = mock(Block.class);
        Block stone = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(stone.getType()).thenReturn(Material.STONE);
        collision(stone, 1);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                airAt.test(call.getArgument(1)) ? air : stone);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
    }

    private Optional<Location> find(Location origin) {
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
                Runnable task = call.getArgument(1);
                task.run();
                return true;
            });
            return locations.findOutside(player, origin).join();
        }
    }

    private void collision(Block block, double height) {
        VoxelShape shape = mock(VoxelShape.class);
        BoundingBox bounds = new BoundingBox(0, 0, 0, 1, height, 1);
        when(block.getCollisionShape()).thenReturn(shape);
        when(shape.overlaps(any(BoundingBox.class))).thenAnswer(call -> bounds.overlaps((BoundingBox) call.getArgument(0)));
    }
}
