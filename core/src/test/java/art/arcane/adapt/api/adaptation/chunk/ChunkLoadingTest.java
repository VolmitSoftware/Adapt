package art.arcane.adapt.api.adaptation.chunk;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkLoadingTest {
  @Test
  void foreignFoliaTargetSkipsSynchronousChunkAccess() {
    WorldRef world = mock(WorldRef.class);
    Loc location = new Loc(world, 160.0D, 64.0D, 160.0D);
    AtomicBoolean loaded = new AtomicBoolean();
    when(world.loadChunkAsync(10, 10)).thenReturn(CompletableFuture.completedFuture(null));

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.isOwnedByCurrentRegion(location)).thenReturn(false);
      scheduling.when(() -> Tasks.runAt(same(location), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });

      ChunkLoading.loadChunkAsync(location, () -> loaded.set(true));
    }

    assertThat(loaded.get()).isTrue();
    verify(world).loadChunkAsync(10, 10);
    verify(world, never()).isChunkLoaded(anyInt(), anyInt());
  }
}
