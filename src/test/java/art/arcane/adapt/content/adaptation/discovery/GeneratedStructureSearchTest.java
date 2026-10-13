package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeneratedStructureSearchTest {
  @Test
  void spiralVisitsEveryBoundedChunkOnceFromNegativeOrigins() {
    GeneratedStructureSearch.Cursor cursor = new GeneratedStructureSearch.Cursor(-0.5D, -16.5D, 32D);
    Set<GeneratedStructureSearch.Position> positions = new HashSet<>();
    GeneratedStructureSearch.Position first = cursor.next();
    assertThat(first).isEqualTo(new GeneratedStructureSearch.Position(-1, -2));
    positions.add(first);
    GeneratedStructureSearch.Position next;
    while ((next = cursor.next()) != null) {
      assertThat(positions.add(next)).isTrue();
      assertThat(next.x()).isBetween(-3, 1);
      assertThat(next.z()).isBetween(-4, 0);
    }
    assertThat(positions).hasSize(25);
  }

  @Test
  void dispatcherLimitsRequestsAndWaitsForChunkOwnersWithoutGenerating() {
    World world = mock(World.class);
    Queue<Runnable> owned = new ArrayDeque<>();
    List<CompletableFuture<Chunk>> loads = new ArrayList<>();
    when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false), eq(false))).thenAnswer(invocation -> {
      CompletableFuture<Chunk> future = new CompletableFuture<>();
      loads.add(future);
      return future;
    });
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    search.begin(request(world, 500D));
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
        owned.add(invocation.getArgument(1));
        return true;
      });
      search.tick();
      assertThat(owned).hasSize(GeneratedStructureSearch.REQUESTS_PER_TICK);
      verify(world, never()).getChunkAtAsync(anyInt(), anyInt(), eq(false), eq(false));
      drain(owned);
      assertThat(loads).hasSize(8);
      search.tick();
      drain(owned);
      search.tick();
      assertThat(loads).hasSize(GeneratedStructureSearch.MAX_IN_FLIGHT);
      loads.getFirst().complete(null);
      search.tick();
      drain(owned);
      assertThat(loads).hasSize(GeneratedStructureSearch.MAX_IN_FLIGHT + 1);
      verify(world, never()).getChunkAtAsync(anyInt(), anyInt(), eq(true), eq(false));
    }
  }

  @Test
  void cancelledSweepIgnoresLateLoadsAndCannotMatchItsReplacement() {
    World world = mock(World.class);
    UUID worldId = UUID.randomUUID();
    CompletableFuture<Chunk> future = new CompletableFuture<>();
    when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false), eq(false))).thenReturn(future);
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    GeneratedStructureSearch.Request request = new GeneratedStructureSearch.Request(
        UUID.randomUUID(), world, worldId, 0D, 0D, 16D, List.of(), candidate -> {
          throw new AssertionError("Cancelled sweep published a candidate");
        });
    GeneratedStructureSearch.Sweep old = search.begin(request);
    GeneratedChunkStructures metadata = mock(GeneratedChunkStructures.class);
    try (MockedStatic<J> scheduler = mockStatic(J.class);
         MockedStatic<NativeAdapters> nativeAdapters = mockStatic(NativeAdapters.class)) {
      nativeAdapters.when(() -> NativeAdapters.require(GeneratedChunkStructures.class)).thenReturn(metadata);
      search.initialize();
      scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
        ((Runnable) invocation.getArgument(1)).run();
        return true;
      });
      search.tick();
      old.cancel();
      GeneratedStructureSearch.Sweep replacement = search.begin(request(world, 16D));
      future.complete(mock(Chunk.class));
      verifyNoInteractions(metadata);
      GeneratedStructureSearch.Candidate stale = new GeneratedStructureSearch.Candidate(
          request.id(), worldId, 0D, 0D, "Old", "", null);
      assertThat(old.owns(stale)).isFalse();
      assertThat(replacement.owns(stale)).isFalse();
    }
  }

  @Test
  void boundaryReferencesLoadTheirStartOnceWithoutFollowingMoreReferences() {
    World world = mock(World.class);
    List<GeneratedStructureSearch.Position> loads = new ArrayList<>();
    GeneratedStructureSearch.Position outside = new GeneratedStructureSearch.Position(4, 0);
    when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false), eq(false))).thenAnswer(invocation -> {
      int x = invocation.getArgument(0);
      int z = invocation.getArgument(1);
      loads.add(new GeneratedStructureSearch.Position(x, z));
      Chunk chunk = mock(Chunk.class);
      when(chunk.getWorld()).thenReturn(world);
      when(chunk.getX()).thenReturn(x);
      when(chunk.getZ()).thenReturn(z);
      return CompletableFuture.completedFuture(chunk);
    });
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    GeneratedStructureSearch.Sweep sweep = search.begin(request(world, 16D));
    GeneratedChunkStructures metadata = mock(GeneratedChunkStructures.class);
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = mockStatic(J.class);
         MockedStatic<NativeAdapters> nativeAdapters = mockStatic(NativeAdapters.class)) {
      nativeAdapters.when(() -> NativeAdapters.require(GeneratedChunkStructures.class)).thenReturn(metadata);
      search.initialize();
      scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
        ((Runnable) invocation.getArgument(1)).run();
        return true;
      });
      bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(World.class), anyInt(), anyInt())).thenReturn(true);
      when(metadata.read(any(Chunk.class))).thenAnswer(invocation -> {
        Chunk chunk = invocation.getArgument(0);
        return new GeneratedChunkStructures.Snapshot(List.of(), List.of(
            chunk.getX() == 4 ? new ChunkPosition(10, 0) : new ChunkPosition(outside.x(), outside.z())));
      });
      for (int tick = 0; tick < 10; tick++) {
        search.tick();
      }
      assertThat(loads).hasSize(10).containsOnlyOnce(outside);
      assertThat(loads).doesNotContain(new GeneratedStructureSearch.Position(10, 0));
      assertThat(sweep.running()).isFalse();
      assertThat(sweep.withinRange(16D, 0D)).isTrue();
      assertThat(sweep.withinRange(16.01D, 0D)).isFalse();
    }
  }

  @Test
  void completedAndPendingLoadsReadMetadataBeforeTheirOwningCallbackReturns() {
    World world = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    when(chunk.getWorld()).thenReturn(world);
    Queue<Runnable> owned = new ArrayDeque<>();
    List<CompletableFuture<Chunk>> loads = new ArrayList<>();
    AtomicBoolean callbackOwned = new AtomicBoolean();
    when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false), eq(false))).thenAnswer(invocation -> {
      CompletableFuture<Chunk> future = loads.isEmpty()
          ? CompletableFuture.completedFuture(chunk) : new CompletableFuture<>();
      loads.add(future);
      return future;
    });
    GeneratedChunkStructures metadata = mock(GeneratedChunkStructures.class);
    when(metadata.read(chunk)).thenAnswer(invocation -> {
      assertThat(callbackOwned.get()).isTrue();
      return new GeneratedChunkStructures.Snapshot(List.of(), List.of());
    });
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    search.begin(request(world, 16D));
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = mockStatic(J.class);
         MockedStatic<NativeAdapters> nativeAdapters = mockStatic(NativeAdapters.class)) {
      nativeAdapters.when(() -> NativeAdapters.require(GeneratedChunkStructures.class)).thenReturn(metadata);
      search.initialize();
      bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(eq(world), anyInt(), anyInt())).thenAnswer(invocation -> callbackOwned.get());
      scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
        owned.add(invocation.getArgument(1));
        return true;
      });
      search.tick();
      callbackOwned.set(true);
      drain(owned);
      verify(metadata).read(chunk);
      assertThat(owned).isEmpty();
      loads.get(1).complete(chunk);
      callbackOwned.set(false);
      assertThat(owned).isEmpty();
      verify(metadata, times(2)).read(chunk);
    }
  }

  @Test
  void closingTheServicePreventsQueuedAndFutureRequests() {
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    World world = mock(World.class);
    GeneratedStructureSearch.Sweep pending = search.begin(request(world, 500D));
    search.close();
    search.tick();
    assertThat(pending.running()).isFalse();
    assertThat(search.begin(request(world, 500D)).running()).isFalse();
    verify(world, never()).getChunkAtAsync(anyInt(), anyInt(), eq(false), eq(false));
  }

  private GeneratedStructureSearch.Request request(World world, double range) {
    return new GeneratedStructureSearch.Request(UUID.randomUUID(), world, UUID.randomUUID(),
        0D, 0D, range, List.of(), candidate -> { });
  }

  private void drain(Queue<Runnable> tasks) {
    Runnable task;
    while ((task = tasks.poll()) != null) {
      task.run();
    }
  }
}
