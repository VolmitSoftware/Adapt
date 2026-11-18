package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class RiftAccessViewRegistryTest {
  private static final UUID WORLD_ID = new UUID(1L, 2L);
  private static final RiftAccessViewRegistry.ChunkKey FIRST_CHUNK =
      new RiftAccessViewRegistry.ChunkKey(WORLD_ID, 4, 8);
  private static final RiftAccessViewRegistry.ChunkKey SECOND_CHUNK =
      new RiftAccessViewRegistry.ChunkKey(WORLD_ID, 5, 8);
  private static final RiftAccessViewRegistry.BlockKey FIRST_BLOCK =
      new RiftAccessViewRegistry.BlockKey(WORLD_ID, 64, 70, 128);
  private static final RiftAccessViewRegistry.BlockKey SECOND_BLOCK =
      new RiftAccessViewRegistry.BlockKey(WORLD_ID, 65, 70, 128);
  private static final RiftAccessViewRegistry.BlockKey CROSS_CHUNK_BLOCK =
      new RiftAccessViewRegistry.BlockKey(WORLD_ID, 80, 70, 128);

  @Test
  void closesOnlyTheExactPlayersActiveView() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    UUID playerId = UUID.randomUUID();
    Object remoteView = new Object();
    Object unrelatedView = new Object();
    RiftAccessViewRegistry.Registration registration = registry.register(
        playerId, Set.of(FIRST_BLOCK), Set.of(FIRST_CHUNK));

    assertThat(registry.activate(registration.session(), remoteView)).isTrue();
    assertThat(registry.closeView(playerId, unrelatedView)).isNull();
    assertThat(registry.activeCount()).isEqualTo(1);

    assertThat(registry.closeView(playerId, remoteView)).isSameAs(registration.session());
    assertThat(registry.activeCount()).isZero();
    assertThat(registry.requiresTicket(FIRST_CHUNK)).isFalse();
  }

  @Test
  void replacingAViewInTheSameChunkKeepsOneReferenceAndRejectsStaleCallbacks() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    UUID playerId = UUID.randomUUID();
    RiftAccessViewRegistry.Registration first = registry.register(
        playerId, Set.of(FIRST_BLOCK), Set.of(FIRST_CHUNK));
    RiftAccessViewRegistry.Registration replacement = registry.register(
        playerId, Set.of(SECOND_BLOCK), Set.of(FIRST_CHUNK));

    assertThat(replacement.retired()).isSameAs(first.session());
    assertThat(replacement.session().token()).isGreaterThan(first.session().token());
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isEqualTo(1);
    assertThat(registry.activate(first.session(), new Object())).isFalse();
    assertThat(registry.cancel(first.session())).isNull();
    assertThat(registry.isPending(replacement.session())).isTrue();
  }

  @Test
  void replacingAcrossChunksReleasesTheOldChunkAndAcquiresTheNewOne() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    UUID playerId = UUID.randomUUID();
    RiftAccessViewRegistry.Registration first = registry.register(
        playerId, Set.of(FIRST_BLOCK), Set.of(FIRST_CHUNK));

    RiftAccessViewRegistry.Registration replacement = registry.register(
        playerId, Set.of(CROSS_CHUNK_BLOCK), Set.of(SECOND_CHUNK));

    assertThat(replacement.retired()).isSameAs(first.session());
    assertThat(registry.requiresTicket(FIRST_CHUNK)).isFalse();
    assertThat(registry.requiresTicket(SECOND_CHUNK)).isTrue();
  }

  @Test
  void oneThousandViewsShareOneTicketTransitionAndCloseByBlock() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    for (int index = 0; index < 1_000; index++) {
      RiftAccessViewRegistry.Registration registration = registry.register(
          new UUID(0L, index + 1L), Set.of(FIRST_BLOCK), Set.of(FIRST_CHUNK));
      assertThat(registry.activate(registration.session(), new Object())).isTrue();
    }

    assertThat(registry.activeCount()).isEqualTo(1_000);
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isEqualTo(1_000);

    List<RiftAccessViewRegistry.Session> closed = registry.closeBlock(FIRST_BLOCK);

    assertThat(closed).hasSize(1_000);
    assertThat(registry.activeCount()).isZero();
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isZero();
  }

  @Test
  void eitherDoubleChestHalfClosesTheSessionAndReleasesBothChunkReferences() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    RiftAccessViewRegistry.Registration firstHalfClosure = registry.register(
        UUID.randomUUID(),
        Set.of(FIRST_BLOCK, CROSS_CHUNK_BLOCK),
        Set.of(FIRST_CHUNK, SECOND_CHUNK)
    );

    assertThat(firstHalfClosure.session().blockKeys()).containsExactlyInAnyOrder(FIRST_BLOCK, CROSS_CHUNK_BLOCK);
    assertThat(firstHalfClosure.session().chunkKeys()).containsExactlyInAnyOrder(FIRST_CHUNK, SECOND_CHUNK);
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isEqualTo(1);
    assertThat(registry.chunkReferenceCount(SECOND_CHUNK)).isEqualTo(1);

    assertThat(registry.closeBlock(FIRST_BLOCK)).containsExactly(firstHalfClosure.session());
    assertThat(registry.activeCount()).isZero();
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isZero();
    assertThat(registry.chunkReferenceCount(SECOND_CHUNK)).isZero();

    RiftAccessViewRegistry.Registration secondHalfClosure = registry.register(
        UUID.randomUUID(),
        Set.of(FIRST_BLOCK, CROSS_CHUNK_BLOCK),
        Set.of(FIRST_CHUNK, SECOND_CHUNK)
    );

    assertThat(registry.closeBlock(CROSS_CHUNK_BLOCK)).containsExactly(secondHalfClosure.session());
    assertThat(registry.activeCount()).isZero();
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isZero();
    assertThat(registry.chunkReferenceCount(SECOND_CHUNK)).isZero();
  }

  @Test
  void eitherDoubleChestChunkClosesTheSessionAndReleasesBothChunkReferences() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    RiftAccessViewRegistry.Registration firstChunkClosure = registry.register(
        UUID.randomUUID(),
        Set.of(FIRST_BLOCK, CROSS_CHUNK_BLOCK),
        Set.of(FIRST_CHUNK, SECOND_CHUNK)
    );

    assertThat(registry.closeChunk(FIRST_CHUNK)).containsExactly(firstChunkClosure.session());
    assertThat(registry.activeCount()).isZero();
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isZero();
    assertThat(registry.chunkReferenceCount(SECOND_CHUNK)).isZero();

    RiftAccessViewRegistry.Registration secondChunkClosure = registry.register(
        UUID.randomUUID(),
        Set.of(FIRST_BLOCK, CROSS_CHUNK_BLOCK),
        Set.of(FIRST_CHUNK, SECOND_CHUNK)
    );

    assertThat(registry.closeChunk(SECOND_CHUNK)).containsExactly(secondChunkClosure.session());
    assertThat(registry.activeCount()).isZero();
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isZero();
    assertThat(registry.chunkReferenceCount(SECOND_CHUNK)).isZero();
  }

  @Test
  void replacementKeepsOnlyItsOverlappingChunkReference() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    UUID playerId = UUID.randomUUID();
    registry.register(
        playerId,
        Set.of(FIRST_BLOCK, CROSS_CHUNK_BLOCK),
        Set.of(FIRST_CHUNK, SECOND_CHUNK)
    );

    registry.register(playerId, Set.of(CROSS_CHUNK_BLOCK), Set.of(SECOND_CHUNK));

    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isZero();
    assertThat(registry.chunkReferenceCount(SECOND_CHUNK)).isEqualTo(1);
    assertThat(registry.closeBlock(FIRST_BLOCK)).isEmpty();
    assertThat(registry.closeBlock(CROSS_CHUNK_BLOCK)).hasSize(1);
  }

  @Test
  void chunkCloseAndDrainLeaveNoRetainedReferences() {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    registry.register(UUID.randomUUID(), Set.of(FIRST_BLOCK), Set.of(FIRST_CHUNK));
    registry.register(UUID.randomUUID(), Set.of(SECOND_BLOCK), Set.of(FIRST_CHUNK));
    registry.register(UUID.randomUUID(), Set.of(CROSS_CHUNK_BLOCK), Set.of(SECOND_CHUNK));

    assertThat(registry.closeChunk(FIRST_CHUNK)).hasSize(2);
    assertThat(registry.activeCount()).isEqualTo(1);
    assertThat(registry.requiresTicket(FIRST_CHUNK)).isFalse();
    assertThat(registry.drain()).hasSize(1);
    assertThat(registry.activeCount()).isZero();
    assertThat(registry.requiresTicket(SECOND_CHUNK)).isFalse();
    assertThat(registry.register(
        UUID.randomUUID(), Set.of(FIRST_BLOCK), Set.of(FIRST_CHUNK))).isNull();
  }

  @Test
  void concurrentOpenAndCancelLeavesNoSessionsOrChunkReferences() throws Exception {
    RiftAccessViewRegistry registry = new RiftAccessViewRegistry();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Void>> tasks = new ArrayList<>(1_000);
      for (int index = 0; index < 1_000; index++) {
        UUID playerId = new UUID(3L, index + 1L);
        tasks.add(() -> {
          RiftAccessViewRegistry.Registration registration = registry.register(
              playerId,
              Set.of(FIRST_BLOCK, CROSS_CHUNK_BLOCK),
              Set.of(FIRST_CHUNK, SECOND_CHUNK)
          );
          registry.cancel(registration.session());
          return null;
        });
      }

      List<Future<Void>> results = executor.invokeAll(tasks);
      for (Future<Void> result : results) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(registry.activeCount()).isZero();
    assertThat(registry.chunkReferenceCount(FIRST_CHUNK)).isZero();
    assertThat(registry.chunkReferenceCount(SECOND_CHUNK)).isZero();
  }
}
