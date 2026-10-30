package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimChunkRetentionTest extends AdaptTestBase {
    @Test
    void coldLoadRetainsBeforeTheCompletionCallbackDropsItsNativeTicket() {
        World world = mock(World.class);
        AtomicBoolean held = new AtomicBoolean();
        AtomicBoolean loaded = new AtomicBoolean(true);
        CompletableFuture<Chunk> nativeLoad = new CompletableFuture<>();
        when(world.getChunkAtAsync(2, 3, true)).thenReturn(nativeLoad);
        when(world.isChunkLoaded(2, 3)).thenAnswer(call -> loaded.get());
        when(world.addPluginChunkTicket(2, 3, plugin)).thenAnswer(call -> !held.getAndSet(true));
        doAnswer(call -> { held.set(false); loaded.set(false); return true; })
                .when(world).removePluginChunkTicket(2, 3, plugin);
        try (MockedStatic<J> scheduler = owner(); ClaimChunkRetention retained = new ClaimChunkRetention()) {
            CompletableFuture<Void> result = retained.load(world, 2, 3);
            nativeLoad.complete(mock(Chunk.class));
            loaded.set(held.get());
            assertThat(result).isCompletedWithValue(null);
            assertThat(loaded).isTrue();
            retained.close();
            assertThat(loaded).isFalse();
        }
    }

    @Test
    void cancellationBeforeAnOutstandingLoadCannotAcquireALateTicket() {
        World world = mock(World.class);
        CompletableFuture<Chunk> nativeLoad = new CompletableFuture<>();
        when(world.getChunkAtAsync(2, 3, true)).thenReturn(nativeLoad);
        try (MockedStatic<J> scheduler = owner(); ClaimChunkRetention retained = new ClaimChunkRetention()) {
            retained.load(world, 2, 3);
            retained.close();
            nativeLoad.complete(mock(Chunk.class));
            verify(world, never()).addPluginChunkTicket(2, 3, plugin);
        }
    }

    @Test
    void aSelectedPortalRemainsRetainedAfterItsCandidateScopeCloses() {
        World world = mock(World.class);
        when(world.getChunkAtAsync(2, 3, true)).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        when(world.isChunkLoaded(2, 3)).thenReturn(true);
        when(world.addPluginChunkTicket(2, 3, plugin)).thenReturn(true);
        try (MockedStatic<J> scheduler = owner(); ClaimChunkRetention candidate = new ClaimChunkRetention();
             ClaimChunkRetention transfer = new ClaimChunkRetention()) {
            candidate.load(world, 2, 3).join();
            candidate.copyTo(transfer);
            candidate.close();
            verify(world, never()).removePluginChunkTicket(2, 3, plugin);
            transfer.close();
            verify(world, times(1)).removePluginChunkTicket(2, 3, plugin);
        }
    }

    private MockedStatic<J> owner() {
        MockedStatic<J> scheduler = mockStatic(J.class);
        scheduler.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
        return scheduler;
    }
}
