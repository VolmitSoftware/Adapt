package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimPlot;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimRepository;
import art.arcane.adapt.api.claims.ClaimResistance;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimsAnchorReconcilerTest extends AdaptTestBase {
    private final UUID worldId = UUID.randomUUID();
    private final UUID founder = UUID.randomUUID();
    private final ClaimRanks ranks = new ClaimRanks(true, 1, 1, 4, true);
    private final ClaimWorldAccess access = new ClaimWorldAccess("world", true);
    private final ClaimBlock position = new ClaimBlock(worldId, 2048, 64, 2048);
    private ClaimsService claims;
    private ClaimManager manager;
    private ClaimsAnchorReconciler reconciler;
    private ClaimCity city;
    private ClaimsConfig config;
    private Path data;
    private World world;
    private Block anchor;
    private Player attacker;
    private PluginManager plugins;

    @BeforeEach
    void prepare() throws IOException {
        claims = mock(ClaimsService.class);
        world = mock(World.class);
        attacker = mock(Player.class);
        plugins = mock(PluginManager.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false)))
                .thenReturn(CompletableFuture.completedFuture(chunk));
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getGameMode()).thenReturn(GameMode.SURVIVAL);
        anchor = block(position, Material.LODESTONE);
        when(world.getBlockAt(position.x(), position.y(), position.z())).thenReturn(anchor);
        config = new ClaimsConfig();
        config.resistanceRanks.rank1 = new ClaimResistance(1, 1, 1, 1);
        data = dataFolder.toPath().resolve("claims.json");
        manager = new ClaimManager(config, new ClaimRepository(data));
        city = manager.found(founder, "Town", position, ranks, access);
        when(claims.manager()).thenAnswer(ignored -> manager);
        when(claims.enabled()).thenReturn(true);
        reconciler = new ClaimsAnchorReconciler(claims);
    }

    @AfterEach
    void close() throws IOException {
        reconciler.close();
        manager.close();
    }

    @Test
    void restartRecoversCompletedBreachWhoseDeferredObservationNeverRan() throws IOException {
        ClaimProtectionListener protection = new ClaimProtectionListener(claims);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            BlockBreakEvent broken = new BlockBreakEvent(anchor, attacker);
            protection.onBreak(broken);
            assertThat(broken.isCancelled()).isFalse();
            assertThat(delayed).hasSize(1);
            change(anchor, Material.AIR);
            manager.close();
            manager = new ClaimManager(config, new ClaimRepository(data));
            assertThat(manager.snapshot().cities()).containsKey(city.id());
            delayed.clear();
            reconciler.validateWorld(world);
            assertThat(manager.snapshot().cities()).containsKey(city.id());
            assertThat(delayed).hasSize(1);
            delayed.getFirst().run();
            assertThat(manager.snapshot().cities()).isEmpty();
        }
        manager.flush();
        try (ClaimRepository reader = new ClaimRepository(data)) {
            assertThat(reader.load().cities()).isEmpty();
        }
    }

    @Test
    void existingLodestoneRetainsLand() {
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed)) {
            reconciler.validateWorld(world);
            delayed.getFirst().run();
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
    }

    @Test
    void missingPersonalAnchorReleasesOnlyItsPlot() {
        ClaimPlot plot = plot();
        Block personal = block(plot.anchor(), Material.AIR);
        when(world.getBlockAt(plot.anchor().x(), plot.anchor().y(), plot.anchor().z())).thenReturn(personal);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed)) {
            reconciler.validateWorld(world);
            delayed.forEach(Runnable::run);
        }
        assertThat(manager.snapshot().plots()).isEmpty();
        assertThat(manager.snapshot().cities()).containsKey(city.id());
    }

    @Test
    void replacementCityIsNeverRemovedByOlderValidation() {
        change(anchor, Material.AIR);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed)) {
            reconciler.validateWorld(world);
            manager.removeCity(city.id());
            ClaimCity replacement = manager.found(founder, "Replacement", position, ranks, access);
            delayed.getFirst().run();
            assertThat(manager.snapshot().cities()).containsOnlyKeys(replacement.id());
        }
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void replacementPlotAtIdenticalAnchorIsNeverRemovedByOlderValidation() {
        ClaimPlot original = plot();
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed)) {
            reconciler.validateWorld(world);
            manager.removePlot(founder);
            ClaimPlot replacement = manager.createPlot(founder, original.anchor(), ranks);
            delayed.forEach(Runnable::run);
            assertThat(manager.snapshot().plots().get(founder)).isSameAs(replacement);
        }
        verify(world, never()).getBlockAt(original.anchor().x(), original.anchor().y(), original.anchor().z());
    }

    @Test
    void rankAndOnlineChangesDoNotHideMissingAnchor() {
        change(anchor, Material.AIR);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed)) {
            reconciler.validateWorld(world);
            manager.updateRanks(founder, new ClaimRanks(true, 4, 4, 4, true));
            manager.setOnline(founder, true, System.currentTimeMillis());
            delayed.getFirst().run();
        }
        assertThat(manager.snapshot().cities()).isEmpty();
    }

    @Test
    void failedChunkLoadRetainsOwnershipAndLogsFullFailure() {
        IOException failure = new IOException("chunk load failed");
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false)))
                .thenReturn(CompletableFuture.failedFuture(failure));
        try (MockedStatic<Adapt> logging = mockStatic(Adapt.class)) {
            reconciler.validateWorld(world);
            logging.verify(() -> Adapt.error(any(IllegalStateException.class)));
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void queuedValidationNeverReadsTerrainOutsideItsOwningRegion() {
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed); MockedStatic<Adapt> logging = mockStatic(Adapt.class)) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(false);
            reconciler.validateWorld(world);
            delayed.getFirst().run();
            logging.verify(() -> Adapt.error(any(IllegalStateException.class)));
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void chunkUnloadingBeforeOwnedValidationRetainsOwnership() {
        change(anchor, Material.AIR);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed); MockedStatic<Adapt> logging = mockStatic(Adapt.class)) {
            reconciler.validateWorld(world);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
            delayed.getFirst().run();
            logging.verify(() -> Adapt.error(any(IllegalStateException.class)));
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void schedulerRejectionRetainsOwnership() {
        change(anchor, Material.AIR);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Adapt> logging = mockStatic(Adapt.class)) {
            reconciler.validateWorld(world);
            logging.verify(() -> Adapt.error(any(IllegalStateException.class)));
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void anchorBreakIsRejectedWhenCompletionCannotBeScheduled() {
        ClaimProtectionListener protection = new ClaimProtectionListener(claims);
        try (MockedStatic<J> scheduler = mockStatic(J.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            BlockBreakEvent event = new BlockBreakEvent(anchor, attacker);
            protection.onBreak(event);
            assertThat(event.isCancelled()).isTrue();
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
    }

    @Test
    void closingBeforeChunkLoadCompletesPreventsRegionAccess() {
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenReturn(loading);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            reconciler.validateWorld(world);
            reconciler.close();
            loading.complete(mock(Chunk.class));
            scheduler.verifyNoInteractions();
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void anchorsRemovedWhileDisabledAreReconciledWhenEnabled() {
        change(anchor, Material.AIR);
        when(claims.enabled()).thenReturn(false);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed)) {
            reconciler.validateWorld(world);
            assertThat(delayed).isEmpty();
            assertThat(manager.snapshot().cities()).containsKey(city.id());
            when(claims.enabled()).thenReturn(true);
            reconciler.validateWorld(world);
            delayed.getFirst().run();
        }
        assertThat(manager.snapshot().cities()).isEmpty();
    }

    @Test
    void identicalReplacementCannotHideCompletedBreachEvenWhenPlacementIsCancelled() {
        ClaimProtectionListener protection = new ClaimProtectionListener(claims);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            BlockBreakEvent broken = new BlockBreakEvent(anchor, attacker);
            protection.onBreak(broken);
            assertThat(broken.isCancelled()).isFalse();
            BlockPlaceEvent replacement = replacement(Material.AIR);
            replacement.setCancelled(true);
            protection.onAnchorReplacement(replacement);
            assertThat(manager.snapshot().cities()).isEmpty();
            assertThat(replacement.isCancelled()).isTrue();
            delayed.getFirst().run();
        }
    }

    @Test
    void cancelledBreakCannotReleaseOwnership() {
        ClaimProtectionListener protection = new ClaimProtectionListener(claims);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            BlockBreakEvent broken = new BlockBreakEvent(anchor, attacker);
            protection.onBreak(broken);
            broken.setCancelled(true);
            protection.onAnchorReplacement(replacement(Material.AIR));
            delayed.getFirst().run();
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
    }

    @Test
    void syntheticReplacementCannotReleaseOwnership() {
        ClaimProtectionListener protection = new ClaimProtectionListener(claims);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            protection.onBreak(new BlockBreakEvent(anchor, attacker));
            doAnswer(call -> {
                protection.onAnchorReplacement(call.getArgument(0));
                return null;
            }).when(plugins).callEvent(any(Event.class));
            ProtectionEventProbe.dispatch(replacement(Material.AIR));
        }
        assertThat(manager.snapshot().cities()).containsKey(city.id());
    }

    @Test
    void replacementAfterCityRecreationCannotReleaseNewOwnership() {
        ClaimProtectionListener protection = new ClaimProtectionListener(claims);
        List<Runnable> delayed = new ArrayList<>();
        try (MockedStatic<J> scheduler = scheduler(delayed); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            protection.onBreak(new BlockBreakEvent(anchor, attacker));
            manager.removeCity(city.id());
            ClaimCity replacement = manager.found(founder, "Replacement", position, ranks, access);
            protection.onAnchorReplacement(replacement(Material.AIR));
            assertThat(manager.snapshot().cities()).containsOnlyKeys(replacement.id());
        }
    }

    private MockedStatic<J> scheduler(List<Runnable> delayed) {
        MockedStatic<J> scheduler = mockStatic(J.class);
        scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
        scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
            delayed.add(call.getArgument(1));
            return true;
        });
        scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenAnswer(call -> {
            delayed.add(call.getArgument(1));
            return true;
        });
        return scheduler;
    }

    private ClaimPlot plot() {
        for (int x = 130; x <= 132; x++) {
            for (int z = 127; z <= 129; z++) {
                manager.expand(founder, new ClaimChunk(worldId, x, z), access);
            }
        }
        return manager.createPlot(founder, new ClaimBlock(worldId, 131 * 16, 64, 2048), ranks);
    }

    private Block block(ClaimBlock position, Material material) {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, position.x(), position.y(), position.z()));
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(position.x());
        when(block.getY()).thenReturn(position.y());
        when(block.getZ()).thenReturn(position.z());
        change(block, material);
        return block;
    }

    private void change(Block block, Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn("minecraft:" + material.name().toLowerCase(Locale.ROOT));
        when(block.getType()).thenReturn(material);
        when(block.getBlockData()).thenReturn(data);
    }

    private BlockPlaceEvent replacement(Material previous) {
        BlockState replaced = mock(BlockState.class);
        BlockData data = mock(BlockData.class);
        Location location = anchor.getLocation();
        when(data.getAsString()).thenReturn("minecraft:" + previous.name().toLowerCase(Locale.ROOT));
        when(replaced.getLocation()).thenReturn(location);
        when(replaced.getBlockData()).thenReturn(data);
        when(replaced.getBlock()).thenReturn(anchor);
        return new BlockPlaceEvent(anchor, replaced, anchor, mock(ItemStack.class), attacker, true, EquipmentSlot.HAND);
    }
}
