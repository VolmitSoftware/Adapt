package art.arcane.adapt.api.fx;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewerGlowCoordinatorTest {
  @Test
  void pluginInitializesCoordinatorBeforeSkillsAreConstructed() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/Adapt.java"));
    int coordinatorInitialization = source.indexOf("initializeGlowingEntities();");
    int skillConstruction = source.indexOf("runStartupPhase(\"start-sim\"");

    assertThat(coordinatorInitialization).isGreaterThanOrEqualTo(0).isLessThan(skillConstruction);
  }

  @Test
  void restoresLowerLayerWhenHigherLayerReleasesItsClaim() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    Entity target = entity(UUID.randomUUID(), 31);

    assertThat(coordinator.set(ViewerGlowCoordinator.Layer.STEALTH_SIGHT, target, viewer, ChatColor.AQUA)).isTrue();
    assertThat(coordinator.set(ViewerGlowCoordinator.Layer.RANGED_HEARTSEEKER, target, viewer, ChatColor.RED)).isTrue();
    assertThat(coordinator.unset(ViewerGlowCoordinator.Layer.RANGED_HEARTSEEKER, target, viewer)).isTrue();

    InOrder calls = inOrder(sink);
    calls.verify(sink).set(target, viewer, ChatColor.AQUA);
    calls.verify(sink).set(target, viewer, ChatColor.RED);
    calls.verify(sink).set(target, viewer, ChatColor.AQUA);
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId()))
        .isEqualTo(ViewerGlowCoordinator.Layer.STEALTH_SIGHT);
  }

  @Test
  void replacesTheRuntimeEntityIdBeforeApplyingClaimsToAReusedUuid() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    UUID targetId = UUID.randomUUID();
    Entity original = entity(targetId, 41);
    Entity replacement = entity(targetId, 72);

    assertThat(coordinator.set(ViewerGlowCoordinator.Layer.STEALTH_SIGHT, original, viewer, ChatColor.AQUA)).isTrue();
    assertThat(coordinator.set(ViewerGlowCoordinator.Layer.STEALTH_SIGHT, replacement, viewer, ChatColor.AQUA)).isTrue();

    InOrder calls = inOrder(sink);
    calls.verify(sink).set(original, viewer, ChatColor.AQUA);
    calls.verify(sink).unset(41, viewer);
    calls.verify(sink).set(replacement, viewer, ChatColor.AQUA);
  }

  @Test
  void failedHigherLayerPacketRollsBackToTheVisibleLowerLayer() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    doAnswer(invocation -> {
      if (invocation.getArgument(2) == ChatColor.RED) {
        throw new ReflectiveOperationException("rejected");
      }
      return null;
    }).when(sink).set(any(Entity.class), any(Player.class), any(ChatColor.class));
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    Entity target = entity(UUID.randomUUID(), 19);

    assertThat(coordinator.set(ViewerGlowCoordinator.Layer.STEALTH_SIGHT, target, viewer, ChatColor.AQUA)).isTrue();
    assertThat(coordinator.set(ViewerGlowCoordinator.Layer.RANGED_HEARTSEEKER, target, viewer, ChatColor.RED)).isFalse();
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId()))
        .isEqualTo(ViewerGlowCoordinator.Layer.STEALTH_SIGHT);
    assertThat(coordinator.unset(ViewerGlowCoordinator.Layer.STEALTH_SIGHT, target, viewer)).isTrue();
    verify(sink).unset(19, viewer);
  }

  @Test
  void layerCleanupRunsThroughTheViewerExecutorAndRestoresRemainingClaims() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    AtomicInteger executions = new AtomicInteger();
    ViewerGlowCoordinator.ViewerExecutor executor = (viewer, task) -> {
      executions.incrementAndGet();
      task.run();
      return true;
    };
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink, executor);
    Player viewer = player(UUID.randomUUID());
    Entity target = entity(UUID.randomUUID(), 25);
    coordinator.set(ViewerGlowCoordinator.Layer.STEALTH_SIGHT, target, viewer, ChatColor.AQUA);
    coordinator.set(ViewerGlowCoordinator.Layer.RANGED_HEARTSEEKER, target, viewer, ChatColor.RED);

    coordinator.clearLayer(ViewerGlowCoordinator.Layer.RANGED_HEARTSEEKER);

    assertThat(executions).hasValue(1);
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId()))
        .isEqualTo(ViewerGlowCoordinator.Layer.STEALTH_SIGHT);
    InOrder calls = inOrder(sink);
    calls.verify(sink).set(target, viewer, ChatColor.AQUA);
    calls.verify(sink).set(target, viewer, ChatColor.RED);
    calls.verify(sink).set(target, viewer, ChatColor.AQUA);
  }

  @Test
  void shutdownCleanupUnsetsEveryViewerTargetBeforeDroppingState() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    Entity first = entity(UUID.randomUUID(), 10);
    Entity second = entity(UUID.randomUUID(), 11);
    coordinator.set(ViewerGlowCoordinator.Layer.STEALTH_SIGHT, first, viewer, ChatColor.AQUA);
    coordinator.set(ViewerGlowCoordinator.Layer.TRAGOUL_DEATH_SENSE, second, viewer, ChatColor.DARK_RED);

    assertThat(coordinator.clearAndAwait(100L)).isTrue();

    verify(sink).unset(10, viewer);
    verify(sink).unset(11, viewer);
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), first.getUniqueId())).isNull();
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), second.getUniqueId())).isNull();
  }

  @Test
  void mutationPriorityIsExplicitAgainstEveryAdaptationGlowLayer() {
    assertThat(Arrays.stream(ViewerGlowCoordinator.Layer.values())
        .mapToInt(ViewerGlowCoordinator.Layer::priority)
        .toArray()).containsExactly(100, 200, 300, 400, 500, 600, 700);
    assertThat(ViewerGlowCoordinator.Layer.MUTATION_UMBRAL_ECHO.priority())
        .isGreaterThan(ViewerGlowCoordinator.Layer.STEALTH_SIGHT.priority())
        .isGreaterThan(ViewerGlowCoordinator.Layer.TRAGOUL_DEATH_SENSE.priority())
        .isGreaterThan(ViewerGlowCoordinator.Layer.RANGED_TRAJECTORY_SIGHT.priority())
        .isGreaterThan(ViewerGlowCoordinator.Layer.STEALTH_THREAT.priority())
        .isLessThan(ViewerGlowCoordinator.Layer.TAMING_ALPHAS_COMMAND.priority())
        .isLessThan(ViewerGlowCoordinator.Layer.RANGED_HEARTSEEKER.priority());
  }

  private static Player player(UUID id) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(id);
    return player;
  }

  private static Entity entity(UUID id, int runtimeId) {
    Entity entity = mock(Entity.class);
    when(entity.getUniqueId()).thenReturn(id);
    when(entity.getEntityId()).thenReturn(runtimeId);
    return entity;
  }
}
