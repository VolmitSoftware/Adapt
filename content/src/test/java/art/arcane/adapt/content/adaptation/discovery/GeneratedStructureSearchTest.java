package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.ChunkStructures;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeneratedStructureSearchTest extends AdaptTestBase {
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
    WorldRef world = mock(WorldRef.class);
    Queue<Runnable> owned = new ArrayDeque<>();
    List<CompletableFuture<ChunkStructures>> loads = new ArrayList<>();
    when(world.generatedStructures(anyInt(), anyInt())).thenAnswer(invocation -> {
      CompletableFuture<ChunkStructures> future = new CompletableFuture<>();
      loads.add(future);
      return future;
    });
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    search.begin(request(world, 500D));

    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        owned.add(invocation.getArgument(1));
        return true;
      });
      search.tick();
      assertThat(owned).hasSize(GeneratedStructureSearch.REQUESTS_PER_TICK);
      verify(world, never()).generatedStructures(anyInt(), anyInt());

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
      verify(world, times(GeneratedStructureSearch.MAX_IN_FLIGHT + 1)).generatedStructures(anyInt(), anyInt());
    }
  }

  @Test
  void cancelledSweepIgnoresLateLoadsAndCannotMatchItsReplacement() {
    WorldRef world = mock(WorldRef.class);
    UUID worldId = UUID.randomUUID();
    CompletableFuture<ChunkStructures> future = new CompletableFuture<>();
    when(world.generatedStructures(anyInt(), anyInt())).thenReturn(future);
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    GeneratedStructureSearch.Request request = new GeneratedStructureSearch.Request(
        UUID.randomUUID(), world, worldId, 0D, 0D, 16D, List.of(), candidate -> {
          throw new AssertionError("Cancelled sweep published a candidate");
        });
    GeneratedStructureSearch.Sweep old = search.begin(request);
    ChunkStructures structures = mock(ChunkStructures.class);

    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        ((Runnable) invocation.getArgument(1)).run();
        return true;
      });
      search.tick();
      old.cancel();
      GeneratedStructureSearch.Sweep replacement = search.begin(request(world, 16D));
      future.complete(structures);

      verifyNoInteractions(structures);
      GeneratedStructureSearch.Candidate stale = new GeneratedStructureSearch.Candidate(
          request.id(), worldId, 0D, 0D, "Old", "", null);
      assertThat(old.owns(stale)).isFalse();
      assertThat(replacement.owns(stale)).isFalse();
    }
  }

  @Test
  void boundaryReferencesLoadTheirStartOnceWithoutFollowingMoreReferences() {
    WorldRef world = mock(WorldRef.class);
    List<GeneratedStructureSearch.Position> loads = new ArrayList<>();
    GeneratedStructureSearch.Position outside = new GeneratedStructureSearch.Position(4, 0);
    when(world.generatedStructures(anyInt(), anyInt())).thenAnswer(invocation -> {
      int x = invocation.getArgument(0);
      int z = invocation.getArgument(1);
      loads.add(new GeneratedStructureSearch.Position(x, z));
      return CompletableFuture.completedFuture(new ChunkStructures(List.of(), List.of(
          x == 4 ? new ChunkPos(10, 0) : new ChunkPos(outside.x(), outside.z()))));
    });
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    GeneratedStructureSearch.Sweep sweep = search.begin(request(world, 16D));

    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        ((Runnable) invocation.getArgument(1)).run();
        return true;
      });
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(any(WorldRef.class), anyInt(), anyInt())).thenReturn(true);
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
    WorldRef world = mock(WorldRef.class);
    ChunkStructures structures = mock(ChunkStructures.class);
    Queue<Runnable> owned = new ArrayDeque<>();
    List<CompletableFuture<ChunkStructures>> loads = new ArrayList<>();
    AtomicBoolean callbackOwned = new AtomicBoolean();
    when(world.generatedStructures(anyInt(), anyInt())).thenAnswer(invocation -> {
      CompletableFuture<ChunkStructures> future = loads.isEmpty()
          ? CompletableFuture.completedFuture(structures) : new CompletableFuture<>();
      loads.add(future);
      return future;
    });
    when(structures.starts()).thenAnswer(invocation -> {
      assertThat(callbackOwned.get()).isTrue();
      return List.of();
    });
    when(structures.references()).thenReturn(List.of());
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    search.begin(request(world, 16D));

    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(eq(world), anyInt(), anyInt())).thenAnswer(invocation -> callbackOwned.get());
      scheduler.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        owned.add(invocation.getArgument(1));
        return true;
      });
      search.tick();
      callbackOwned.set(true);
      drain(owned);

      verify(structures).starts();
      assertThat(owned).isEmpty();
      loads.get(1).complete(structures);
      callbackOwned.set(false);
      assertThat(owned).isEmpty();
      verify(structures, times(2)).starts();
    }
  }

  @Test
  void closingTheServicePreventsQueuedAndFutureRequests() {
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    WorldRef world = mock(WorldRef.class);
    GeneratedStructureSearch.Sweep pending = search.begin(request(world, 500D));
    search.close();
    search.tick();

    assertThat(pending.running()).isFalse();
    assertThat(search.begin(request(world, 500D)).running()).isFalse();
    verify(world, never()).generatedStructures(anyInt(), anyInt());
  }

  @Test
  void unsupportedStructureDataClosesTheSearch() {
    WorldRef world = mock(WorldRef.class);
    when(world.generatedStructures(anyInt(), anyInt()))
        .thenReturn(CompletableFuture.failedFuture(new UnsupportedOperationException("no structure data")));
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    GeneratedStructureSearch.Sweep sweep = search.begin(request(world, 16D));

    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        ((Runnable) invocation.getArgument(1)).run();
        return true;
      });
      search.tick();

      assertThat(sweep.running()).isFalse();
      assertThat(search.hasWork()).isFalse();
      assertThat(search.begin(request(world, 16D)).running()).isFalse();
    }
  }

  private GeneratedStructureSearch.Request request(WorldRef world, double range) {
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
