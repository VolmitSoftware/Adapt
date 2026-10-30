package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimArrivalProfileTest extends AdaptTestBase {
    private final Map<String, Block> blocks = new HashMap<>();
    private ClaimsService claims;
    private ClaimSafeLocation locations;
    private Player player;
    private World world;

    @BeforeAll
    static void initializeBlocks() {
        RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
            registry.when(RegistryAccess::registryAccess).thenReturn(access);
            Registry<BlockType> blocks = Registry.BLOCK;
            doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).get(any(NamespacedKey.class));
            doAnswer(call -> mock(BlockType.Typed.class)).when(blocks).getOrThrow(any(Key.class));
            when(Material.AIR.asBlockType().isAir()).thenReturn(true);
            when(Material.STONE.asBlockType().isSolid()).thenReturn(true);
            when(Material.SPAWNER.asBlockType().isSolid()).thenReturn(true);
        }
    }

    @BeforeEach
    void prepareWorld() {
        claims = mock(ClaimsService.class);
        locations = mock(ClaimSafeLocation.class);
        player = mock(Player.class);
        world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWidth()).thenReturn(0.6);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(512);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(claims.canTeleport(eq(player), any(Location.class))).thenReturn(true);
        when(claims.canPrepareArrival(eq(player), eq(world), any(BoundingBox.class))).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                blocks.computeIfAbsent(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2),
                        key -> block(Material.AIR, null)));
    }

    @Test
    void scaledStandingPlayersNeedClearanceForTheirActualHeadHeight() {
        place(8, 63, 8, Material.STONE, new BoundingBox(0, 0, 0, 1, 1, 1));
        place(8, 66, 8, Material.STONE, new BoundingBox(0, 0, 0, 1, 1, 1));
        Location arrival = new Location(world, 8.5, 64, 8.5);
        assertThat(ClaimArrivalProfile.BRACKEN_STANDING.ready(claims, locations, player, arrival)).isTrue();
        when(player.getWidth()).thenReturn(0.6 * 1.32);
        assertThat(ClaimArrivalProfile.BRACKEN_STANDING.ready(claims, locations, player, arrival)).isFalse();
        assertThat(ClaimArrivalProfile.BODY_AIR.permitted(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void scaledBodiesCannotExtendIntoAnAdjacentForbiddenChunk() {
        when(player.getWidth()).thenReturn(0.6 * 1.32);
        when(claims.canTeleport(eq(player), any(Location.class))).thenAnswer(call ->
                ((Location) call.getArgument(1)).getBlockX() < 16);
        assertThat(ClaimArrivalProfile.BRACKEN_FALLING.permitted(claims, locations, player,
                new Location(world, 15.65, 64, 8.5))).isFalse();
    }

    @Test
    void cageLandingCanStandOnASpawnerWithItsBodyOverhangingAir() {
        place(10, 8, 7, Material.SPAWNER, new BoundingBox(0, 0, 0, 1, 1, 1));
        Location arrival = new Location(world, 10.4203659617, 9.0025229881, 7.1087761935);
        assertThat(ClaimArrivalProfile.DORMIS_CAGE.ready(claims, locations, player, arrival)).isTrue();
        blocks.clear();
        assertThat(ClaimArrivalProfile.DORMIS_CAGE.ready(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void swimmingAboveAConduitChecksItsActualShape() {
        place(2, 3, 2, Material.CONDUIT, new BoundingBox(0.3125, 0.3125, 0.3125, 0.6875, 0.6875, 0.6875));
        place(2, 4, 2, Material.WATER, null);
        Location arrival = new Location(world, 2.5, 3.6875, 2.5);
        assertThat(ClaimArrivalProfile.BRACKEN_SWIMMING.ready(claims, locations, player, arrival)).isTrue();
        arrival.subtract(0, 0.1, 0);
        assertThat(ClaimArrivalProfile.BRACKEN_SWIMMING.ready(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void fractionalArrivalsCheckTheCompleteHeadHeight() {
        place(8, 66, 8, Material.STONE, new BoundingBox(0, 0, 0, 1, 1, 1));
        assertThat(ClaimArrivalProfile.BRACKEN_FALLING.ready(claims, locations, player,
                new Location(world, 8.5, 64.8, 8.5))).isFalse();
    }

    @Test
    void intentionalFallingCanStartAboveBuildHeightWithoutAuthorizingEditsThere() {
        Location arrival = new Location(world, 8.5, 700, 8.5);
        assertThat(ClaimArrivalProfile.BRACKEN_FALLING.permitted(claims, locations, player, arrival)).isTrue();
        assertThat(ClaimArrivalProfile.BRACKEN_STANDING.permitted(claims, locations, player, arrival)).isFalse();
        assertThat(ClaimArrivalProfile.BODY_AIR.permitted(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void omnidromeLoopCanArriveBelowItsBuildableLayer() {
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(128);
        Location arrival = new Location(world, 8.5, -38, 8.5);
        assertThat(ClaimArrivalProfile.BRACKEN_FALLING.permitted(claims, locations, player, arrival)).isTrue();
        assertThat(ClaimArrivalProfile.BRACKEN_STANDING.permitted(claims, locations, player, arrival)).isFalse();
        assertThat(ClaimArrivalProfile.BODY_AIR.permitted(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void aKeptFloorMustAlreadyProvideClearStandingSupport() {
        Location arrival = new Location(world, 8.5, 64, 8.5);
        place(8, 63, 8, Material.STONE, new BoundingBox(0, 0, 0, 1, 0.5, 1));
        assertThat(ClaimArrivalProfile.UNDERDARK_TO_OVERWORLD.permitted(claims, locations, player, arrival)).isFalse();
        place(8, 63, 8, Material.STONE, new BoundingBox(0, 0, 0, 1, 1.5, 1));
        assertThat(ClaimArrivalProfile.UNDERDARK_TO_OVERWORLD.permitted(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void cageApprovalIncludesItsEntireTemplateVolume() {
        Location arrival = new Location(world, 10.4203659617, 9.0025229881, 7.1087761935);
        assertThat(ClaimArrivalProfile.DORMIS_CAGE.permitted(claims, locations, player, arrival)).isTrue();
        verify(claims).canPrepareArrival(player, world, new BoundingBox(0, 0, 0, 20, 48, 20));
        when(claims.canPrepareArrival(eq(player), eq(world), any(BoundingBox.class))).thenReturn(false);
        assertThat(ClaimArrivalProfile.DORMIS_CAGE.permitted(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void preparationCannotUseLavaAsAKeptFloor() {
        place(8, 63, 8, Material.LAVA, null);
        assertThat(ClaimArrivalProfile.UNDERDARK_TO_OVERWORLD.permitted(claims, locations, player,
                new Location(world, 8.5, 64, 8.5))).isFalse();
    }

    @Test
    void astralReturnChecksBodyClearanceBeforeRestoringANonspectatorMode() {
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        place(8, 64, 8, Material.STONE, new BoundingBox(0, 0, 0, 1, 1, 1));
        Location arrival = new Location(world, 8.5, 64, 8.5);
        assertThat(ClaimArrivalProfile.BRACKEN_SWIMMING.ready(claims, locations, player, arrival)).isTrue();
        assertThat(ClaimArrivalProfile.ASTRAL_RETURN.ready(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void completionRechecksEditPermissionAfterPreparation() {
        place(8, 63, 8, Material.STONE, new BoundingBox(0, 0, 0, 1, 1, 1));
        Location arrival = new Location(world, 8.5, 64, 8.5);
        assertThat(ClaimArrivalProfile.VOID_CUBE.ready(claims, locations, player, arrival)).isTrue();
        when(claims.canPrepareArrival(eq(player), eq(world), any(BoundingBox.class))).thenReturn(false);
        assertThat(ClaimArrivalProfile.VOID_CUBE.ready(claims, locations, player, arrival)).isFalse();
    }

    @Test
    void preparationWaitsForAllNineChunksTouchedByALargeTemplate() {
        Map<String, CompletableFuture<Chunk>> loading = new HashMap<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(true))).thenAnswer(call ->
                loading.computeIfAbsent(call.getArgument(0) + ":" + call.getArgument(1), key -> new CompletableFuture<>()));
        try (MockedStatic<J> scheduler = mockStatic(J.class); ClaimChunkRetention retained = new ClaimChunkRetention()) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            CompletableFuture<Void> complete = ClaimArrivalProfile.DORMIS_CAGE.load(player,
                    new Location(world, 8.42, 100.0025, 5.1088), retained);
            assertThat(loading).hasSize(9);
            assertThat(complete).isNotDone();
            CompletableFuture<Chunk> last = loading.remove("1:1");
            loading.values().forEach(future -> future.complete(mock(Chunk.class)));
            assertThat(complete).isNotDone();
            last.complete(mock(Chunk.class));
            assertThat(complete).isCompletedWithValue(null);
        }
    }

    @Test
    void templateLoadingIncludesTheCommandsOnePastSizeBoundary() {
        ClaimChunkRetention retained = mock(ClaimChunkRetention.class);
        when(retained.load(eq(world), anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        ClaimArrivalProfile.OMNI_PLATFORM.load(player, new Location(world, 10.5, 65, 6.5), retained).join();
        verify(retained).load(world, 1, 1);
    }

    private void place(int x, int y, int z, Material material, BoundingBox collision) {
        blocks.put(x + ":" + y + ":" + z, block(material, collision));
    }

    private Block block(Material material, BoundingBox collision) {
        Block block = mock(Block.class);
        VoxelShape shape = mock(VoxelShape.class);
        when(block.getType()).thenReturn(material);
        when(block.getCollisionShape()).thenReturn(shape);
        when(shape.overlaps(any(BoundingBox.class))).thenAnswer(call ->
                collision != null && collision.overlaps((BoundingBox) call.getArgument(0)));
        return block;
    }
}
