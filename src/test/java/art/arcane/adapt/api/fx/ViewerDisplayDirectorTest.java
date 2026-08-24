package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewerDisplayDirectorTest extends AdaptTestBase {
  @AfterEach
  void clearDisplays() {
    ViewerDisplayDirector.clearAll();
    ViewerDisplayDirector.startRuntime();
  }

  @Test
  void rejectsZeroAndNonfiniteLines() {
    assertThat(ViewerDisplayDirector.isRenderableLine(new Vector())).isFalse();
    assertThat(ViewerDisplayDirector.isRenderableLine(new Vector(Double.NaN, 0D, 1D))).isFalse();
    assertThat(ViewerDisplayDirector.isRenderableLine(new Vector(0D, 0D, 1D))).isTrue();
  }

  @Test
  void lineTransformationUsesExactLengthAndCenteredThickness() {
    Transformation transformation = ViewerDisplayDirector.lineTransformation(new Vector(0D, 0D, 5D), 0.1D);

    assertThat(transformation.getScale().x).isEqualTo(0.1F);
    assertThat(transformation.getScale().y).isEqualTo(0.1F);
    assertThat(transformation.getScale().z).isEqualTo(5F);
    assertThat(transformation.getTranslation().x).isEqualTo(-0.05F);
    assertThat(transformation.getTranslation().y).isEqualTo(-0.05F);
  }

  @Test
  void lineTransformationRemainsFiniteWithSanitizedThickness() {
    double thickness = ViewerDisplayDirector.sanitizeLineThickness(Double.NaN);
    Transformation transformation = ViewerDisplayDirector.lineTransformation(new Vector(0D, 0D, 5D), thickness);

    assertThat(ViewerDisplayDirector.sanitizeLineThickness(Double.POSITIVE_INFINITY)).isEqualTo(0.05D);
    assertThat(ViewerDisplayDirector.sanitizeLineThickness(0D)).isEqualTo(0.015D);
    assertThat(ViewerDisplayDirector.sanitizeLineThickness(1D)).isEqualTo(0.5D);
    assertThat(transformation.getScale().x).isEqualTo(0.05F);
    assertThat(transformation.getScale().y).isEqualTo(0.05F);
    assertThat(transformation.getTranslation().x).isEqualTo(-0.025F);
    assertThat(transformation.getTranslation().y).isEqualTo(-0.025F);
  }

  @Test
  void earlyExpiryCallbacksRetainTheRemainingTickDelay() {
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(3_000L, 1_000L)).isEqualTo(40);
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(1_051L, 1_000L)).isEqualTo(2);
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(1_001L, 1_000L)).isEqualTo(1);
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(1_000L, 1_000L)).isZero();
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(999L, 1_000L)).isZero();
  }

  @Test
  void rejectedOrphanCleanupDispatchesAreRetriedInsteadOfDropped() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/api/fx/ViewerDisplayDirector.java"));

    assertThat(source).contains(
        "if (!J.runAt(anchor, () -> purgeOrphansOwned(current)))",
        "ORPHAN_PURGE_QUEUE.add(current.retry())",
        "Failed to clean stale Adapt private displays in chunk"
    );
  }

  @Test
  void ingressBackpressureStopsThousandRequestBurstBeforeOwnerDispatch() {
    World world = mock(World.class);
    Location location = mock(Location.class);
    Location ownedLocation = mock(Location.class);
    Player viewer = mock(Player.class);
    BlockData blockData = mock(BlockData.class);
    UUID viewerId = UUID.randomUUID();
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(location.getWorld()).thenReturn(world);
    when(location.clone()).thenReturn(ownedLocation);
    when(ownedLocation.getWorld()).thenReturn(world);
    when(ownedLocation.clone()).thenReturn(ownedLocation);
    when(blockData.clone()).thenReturn(blockData);
    when(viewer.getUniqueId()).thenReturn(viewerId);

    AtomicInteger dispatches = new AtomicInteger();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runAt(same(ownedLocation), any(Runnable.class)))
          .thenAnswer(invocation -> {
            dispatches.incrementAndGet();
            return true;
          });

      int accepted = 0;
      for (int request = 0; request < 1_000; request++) {
        if (ViewerDisplayDirector.showPersistentBlock(
            "burst", "key-" + request, viewer, location, blockData, null)) {
          accepted++;
        }
      }

      assertThat(accepted).isEqualTo(128);
      assertThat(dispatches.get()).isEqualTo(128);
      assertThat(ViewerDisplayDirector.activeCount()).isZero();
      assertThat(ViewerDisplayDirector.clearAllAndAwait(1_000L, ignored -> true)).isTrue();
    }
  }

  @Test
  void sameKeyBurstCoalescesIntoOneOwnerDispatch() {
    World world = mock(World.class);
    Location location = mock(Location.class);
    Location ownedLocation = mock(Location.class);
    Player viewer = mock(Player.class);
    BlockData blockData = mock(BlockData.class);
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(location.getWorld()).thenReturn(world);
    when(location.clone()).thenReturn(ownedLocation);
    when(ownedLocation.getWorld()).thenReturn(world);
    when(ownedLocation.clone()).thenReturn(ownedLocation);
    when(blockData.clone()).thenReturn(blockData);
    when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());

    AtomicInteger dispatches = new AtomicInteger();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runAt(same(ownedLocation), any(Runnable.class)))
          .thenAnswer(invocation -> {
            dispatches.incrementAndGet();
            return true;
          });

      int accepted = 0;
      for (int request = 0; request < 1_000; request++) {
        if (ViewerDisplayDirector.showPersistentBlock(
            "burst", "same-key", viewer, location, blockData, null)) {
          accepted++;
        }
      }

      assertThat(accepted).isEqualTo(1_000);
      assertThat(dispatches.get()).isOne();
      assertThat(ViewerDisplayDirector.clearAllAndAwait(1_000L, ignored -> true)).isTrue();
    }
  }

  @Test
  void indexedClearsRemoveOnlyTheirViewerChannelAndKeyScopes() {
    World world = mock(World.class);
    Location location = mock(Location.class);
    Location ownedLocation = mock(Location.class);
    Player firstViewer = mock(Player.class);
    Player secondViewer = mock(Player.class);
    BlockData blockData = mock(BlockData.class);
    BlockDisplay first = display();
    BlockDisplay second = display();
    BlockDisplay third = display();
    BlockDisplay fourth = display();
    Queue<BlockDisplay> displays = new ArrayDeque<>(List.of(first, second, third, fourth));
    UUID firstViewerId = UUID.randomUUID();
    UUID secondViewerId = UUID.randomUUID();
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(location.getWorld()).thenReturn(world);
    when(location.clone()).thenReturn(ownedLocation);
    when(ownedLocation.getWorld()).thenReturn(world);
    when(ownedLocation.clone()).thenReturn(ownedLocation);
    when(blockData.clone()).thenReturn(blockData);
    when(firstViewer.getUniqueId()).thenReturn(firstViewerId);
    when(secondViewer.getUniqueId()).thenReturn(secondViewerId);
    when(firstViewer.isOnline()).thenReturn(true);
    when(secondViewer.isOnline()).thenReturn(true);
    when(world.spawn(same(ownedLocation), same(BlockDisplay.class),
        ArgumentMatchers.<Consumer<? super BlockDisplay>>any())).thenAnswer(invocation -> {
      BlockDisplay display = displays.remove();
      Consumer<? super BlockDisplay> configurator = invocation.getArgument(2);
      configurator.accept(display);
      return display;
    });

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runAt(same(ownedLocation), any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return true;
          });
      scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return true;
          });

      assertThat(ViewerDisplayDirector.showPersistentBlock(
          "alpha", "target", firstViewer, location, blockData, null)).isTrue();
      assertThat(ViewerDisplayDirector.showPersistentBlock(
          "alpha", "other", firstViewer, location, blockData, null)).isTrue();
      assertThat(ViewerDisplayDirector.showPersistentBlock(
          "alpha", "target", secondViewer, location, blockData, null)).isTrue();
      assertThat(ViewerDisplayDirector.showPersistentBlock(
          "beta", "target", firstViewer, location, blockData, null)).isTrue();
      assertThat(ViewerDisplayDirector.activeCount()).isEqualTo(4);

      ViewerDisplayDirector.clearViewerKey("alpha", "target", firstViewerId);
      assertThat(ViewerDisplayDirector.activeCount()).isEqualTo(3);
      verify(first).remove();
      verify(second, never()).remove();
      verify(third, never()).remove();
      verify(fourth, never()).remove();

      ViewerDisplayDirector.clearViewer("alpha", firstViewerId);
      assertThat(ViewerDisplayDirector.activeCount()).isEqualTo(2);
      verify(second).remove();

      ViewerDisplayDirector.clearChannel("alpha");
      assertThat(ViewerDisplayDirector.activeCount()).isOne();
      verify(third).remove();

      ViewerDisplayDirector.clearViewer(firstViewerId);
      assertThat(ViewerDisplayDirector.activeCount()).isZero();
      verify(fourth).remove();
    }
  }

  @Test
  void shutdownCleanupWaitsForAnInFlightDisplayRequest() throws Exception {
    World world = mock(World.class);
    Location location = mock(Location.class);
    Location ownedLocation = mock(Location.class);
    Player viewer = mock(Player.class);
    BlockData blockData = mock(BlockData.class);
    UUID worldId = UUID.randomUUID();
    UUID viewerId = UUID.randomUUID();
    when(world.getUID()).thenReturn(worldId);
    when(location.getWorld()).thenReturn(world);
    when(location.clone()).thenReturn(ownedLocation);
    when(location.getBlockX()).thenReturn(1);
    when(location.getBlockY()).thenReturn(64);
    when(location.getBlockZ()).thenReturn(2);
    when(ownedLocation.getWorld()).thenReturn(world);
    when(ownedLocation.clone()).thenReturn(ownedLocation);
    when(blockData.clone()).thenReturn(blockData);
    when(viewer.getUniqueId()).thenReturn(viewerId);

    AtomicReference<Runnable> scheduledRequest = new AtomicReference<>();
    CountDownLatch requestEntered = new CountDownLatch(1);
    CountDownLatch releaseRequest = new CountDownLatch(1);
    CountDownLatch cleanupEntered = new CountDownLatch(1);
    BlockDisplay display = mock(BlockDisplay.class);
    when(world.spawn(same(ownedLocation), same(BlockDisplay.class),
        ArgumentMatchers.<Consumer<? super BlockDisplay>>any())).thenAnswer(invocation -> {
      requestEntered.countDown();
      assertThat(releaseRequest.await(5L, TimeUnit.SECONDS)).isTrue();
      Consumer<? super BlockDisplay> configurator = invocation.getArgument(2);
      configurator.accept(display);
      return display;
    });

    ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
    ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runAt(same(ownedLocation), any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduledRequest.set(invocation.getArgument(1));
            return true;
          });

      assertThat(ViewerDisplayDirector.showBlock(
          "test", "pending", viewer, location, blockData, null, 20)).isTrue();
      assertThat(scheduledRequest.get()).isNotNull();
      Future<?> request = requestExecutor.submit(scheduledRequest.get());
      assertThat(requestEntered.await(1L, TimeUnit.SECONDS)).isTrue();

      Future<Boolean> cleanup = cleanupExecutor.submit(() -> ViewerDisplayDirector.clearAllAndAwait(
          1_000L,
          anchor -> {
            cleanupEntered.countDown();
            return false;
          }
      ));
      assertThat(cleanupEntered.await(1L, TimeUnit.SECONDS)).isTrue();
      assertThat(cleanup.isDone()).isFalse();

      releaseRequest.countDown();
      request.get(1L, TimeUnit.SECONDS);
      assertThat(cleanup.get(1L, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseRequest.countDown();
      requestExecutor.shutdownNow();
      cleanupExecutor.shutdownNow();
    }
  }

  @Test
  void leasedDisplayIndexTracksLeaseLifecycle() {
    World world = mock(World.class);
    Location location = mock(Location.class);
    Location ownedLocation = mock(Location.class);
    Player viewer = mock(Player.class);
    BlockData blockData = mock(BlockData.class);
    BlockDisplay display = mock(BlockDisplay.class);
    UUID worldId = UUID.randomUUID();
    UUID viewerId = UUID.randomUUID();
    UUID displayId = UUID.randomUUID();
    when(world.getUID()).thenReturn(worldId);
    when(location.getWorld()).thenReturn(world);
    when(location.clone()).thenReturn(ownedLocation);
    when(location.getBlockX()).thenReturn(1);
    when(location.getBlockY()).thenReturn(64);
    when(location.getBlockZ()).thenReturn(2);
    when(ownedLocation.getWorld()).thenReturn(world);
    when(ownedLocation.clone()).thenReturn(ownedLocation);
    when(ownedLocation.getBlockX()).thenReturn(1);
    when(ownedLocation.getBlockY()).thenReturn(64);
    when(ownedLocation.getBlockZ()).thenReturn(2);
    when(blockData.clone()).thenReturn(blockData);
    when(viewer.getUniqueId()).thenReturn(viewerId);
    when(viewer.isOnline()).thenReturn(true);
    when(display.getUniqueId()).thenReturn(displayId);
    when(display.isValid()).thenReturn(true);
    when(world.spawn(same(ownedLocation), same(BlockDisplay.class),
        ArgumentMatchers.<Consumer<? super BlockDisplay>>any())).thenAnswer(invocation -> {
      Consumer<? super BlockDisplay> configurator = invocation.getArgument(2);
      configurator.accept(display);
      return display;
    });

    AtomicBoolean viewerThread = new AtomicBoolean();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runAt(same(ownedLocation), any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return true;
          });
      scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class)))
          .thenAnswer(invocation -> {
            Entity entity = invocation.getArgument(0);
            Runnable action = invocation.getArgument(1);
            boolean playerOwned = entity == viewer;
            if (playerOwned) {
              viewerThread.set(true);
            }
            try {
              action.run();
            } finally {
              if (playerOwned) {
                viewerThread.set(false);
              }
            }
            return true;
          });
      scheduling.when(() -> J.isOwnedByCurrentRegion(same(ownedLocation))).thenReturn(true);
      when(viewer.isOnline()).thenAnswer(invocation -> {
        assertThat(viewerThread.get()).isTrue();
        return true;
      });

      assertThat(ViewerDisplayDirector.showPersistentBlock(
          "test", "indexed", viewer, location, blockData, null)).isTrue();
      assertThat(ViewerDisplayDirector.isLeased(display)).isTrue();

      assertThat(ViewerDisplayDirector.clearAllAndAwait(1_000L)).isTrue();
      assertThat(ViewerDisplayDirector.isLeased(display)).isFalse();
      verify(display).remove();
    }
  }

  @Test
  void offOwnerVisibilityCheckDefersDisplayValidityReadToAnchorRegion() {
    World world = mock(World.class);
    Location location = mock(Location.class);
    Location ownedLocation = mock(Location.class);
    Player viewer = mock(Player.class);
    BlockData blockData = mock(BlockData.class);
    BlockDisplay display = mock(BlockDisplay.class);
    UUID worldId = UUID.randomUUID();
    UUID viewerId = UUID.randomUUID();
    UUID displayId = UUID.randomUUID();
    when(world.getUID()).thenReturn(worldId);
    when(location.getWorld()).thenReturn(world);
    when(location.clone()).thenReturn(ownedLocation);
    when(location.getBlockX()).thenReturn(1);
    when(location.getBlockY()).thenReturn(64);
    when(location.getBlockZ()).thenReturn(2);
    when(ownedLocation.getWorld()).thenReturn(world);
    when(ownedLocation.clone()).thenReturn(ownedLocation);
    when(blockData.clone()).thenReturn(blockData);
    when(viewer.getUniqueId()).thenReturn(viewerId);
    when(viewer.isOnline()).thenReturn(true);
    when(display.getUniqueId()).thenReturn(displayId);
    when(display.isValid()).thenReturn(false);
    when(world.spawn(same(ownedLocation), same(BlockDisplay.class),
        ArgumentMatchers.<Consumer<? super BlockDisplay>>any())).thenAnswer(invocation -> {
      Consumer<? super BlockDisplay> configurator = invocation.getArgument(2);
      configurator.accept(display);
      return display;
    });

    AtomicReference<Runnable> validation = new AtomicReference<>();
    AtomicBoolean requestDispatched = new AtomicBoolean();
    AtomicInteger validationDispatches = new AtomicInteger();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runAt(same(ownedLocation), any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            if (requestDispatched.compareAndSet(false, true)) {
              action.run();
            } else {
              validationDispatches.incrementAndGet();
              validation.set(action);
            }
            return true;
          });
      scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return true;
          });
      scheduling.when(() -> J.isOwnedByCurrentRegion(same(ownedLocation))).thenReturn(false);

      assertThat(ViewerDisplayDirector.showPersistentBlock(
          "test", "validation", viewer, location, blockData, null)).isTrue();
      clearInvocations(display);

      assertThat(ViewerDisplayDirector.isShowing(
          "test", "validation", viewerId, location)).isTrue();
      assertThat(ViewerDisplayDirector.isShowing(
          "test", "validation", viewerId, location)).isTrue();
      verify(display, never()).isValid();
      assertThat(validation.get()).isNotNull();
      assertThat(validationDispatches.get()).isEqualTo(1);

      validation.get().run();

      verify(display).isValid();
      assertThat(ViewerDisplayDirector.isLeased(display)).isFalse();
    }
  }

  private BlockDisplay display() {
    BlockDisplay display = mock(BlockDisplay.class);
    when(display.getUniqueId()).thenReturn(UUID.randomUUID());
    when(display.isValid()).thenReturn(true);
    return display;
  }
}
