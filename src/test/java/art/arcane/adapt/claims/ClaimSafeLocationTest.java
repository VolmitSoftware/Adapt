package art.arcane.adapt.claims;

import art.arcane.adapt.api.claims.ClaimManager;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
        claims = mock(ClaimsService.class);
        ClaimManager manager = mock(ClaimManager.class);
        world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
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
        when(world.getBlockAt(0, 64, 0)).thenReturn(feet);
        when(world.getBlockAt(0, 65, 0)).thenReturn(head);
        when(world.getBlockAt(0, 63, 0)).thenReturn(floor);
        locations = new ClaimSafeLocation(claims);
    }

    @Test
    void clearHeadroomAndSolidFloorAreRequired() {
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(destination)).thenReturn(true);
            assertThat(locations.safeOwned(destination)).isTrue();
            when(head.getType()).thenReturn(Material.STONE);
            assertThat(locations.safeOwned(destination)).isFalse();
            when(head.getType()).thenReturn(Material.AIR);
            when(floor.getType()).thenReturn(Material.AIR);
            assertThat(locations.safeOwned(destination)).isFalse();
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
            assertThat(locations.safeOwned(destination)).isFalse();
        }
        verify(world, never()).getBlockAt(0, 64, 0);
    }

    @Test
    void timedOutSearchDoesNotScheduleTerrainInspectionWhenChunkLoadingFinishes() {
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(loading);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            CompletableFuture<Optional<Location>> result = locations.findOutside(destination);
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
            CompletableFuture<Optional<Location>> result = locations.findOutside(destination);
            result.cancel(false);
            queued.get().run();
        }
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt());
        verify(world).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());
    }
}
