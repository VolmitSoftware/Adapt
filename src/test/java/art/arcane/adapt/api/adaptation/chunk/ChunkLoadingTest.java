package art.arcane.adapt.api.adaptation.chunk;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import de.crazydev22.platformutils.Platform;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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
    Platform previous = Adapt.platform;
    Platform platform = mock(Platform.class);
    World world = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    Location location = new Location(world, 160.0D, 64.0D, 160.0D);
    AtomicReference<Chunk> loaded = new AtomicReference<>();
    when(platform.getChunkAtAsync(location)).thenReturn(CompletableFuture.completedFuture(chunk));
    Adapt.platform = platform;

    try (MockedStatic<Adapt> adapt = mockStatic(Adapt.class);
         MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(location)).thenReturn(false);
      scheduling.when(() -> J.runAt(same(location), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });

      ChunkLoading.loadChunkAsync(location, loaded::set);
    } finally {
      Adapt.platform = previous;
    }

    assertThat(loaded.get()).isSameAs(chunk);
    verify(world, never()).isChunkLoaded(anyInt(), anyInt());
    verify(world, never()).getChunkAt(anyInt(), anyInt());
  }
}
