package art.arcane.adapt.engine.fx;

import art.arcane.adapt.api.viewer.GlowLayer;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewerGlowCoordinatorTest {
  private static final GlowLayer GOLEMANCY = new GlowLayer(Id.adapt("glow/golemancy"), 50);
  private static final GlowLayer GOLEMANCY_LOCATE = new GlowLayer(Id.adapt("glow/golemancy_locate"), 800);
  private static final GlowLayer RANGED_HEARTSEEKER = new GlowLayer(Id.adapt("glow/ranged_heartseeker"), 700);
  private static final GlowLayer STEALTH_SIGHT = new GlowLayer(Id.adapt("glow/stealth_sight"), 100);
  private static final GlowLayer TRAGOUL_DEATH_SENSE = new GlowLayer(Id.adapt("glow/tragoul_death_sense"), 200);
  private static final List<GlowLayer> BUILT_IN = List.of(GOLEMANCY, new GlowLayer(Id.adapt("glow/hunter"), 75), STEALTH_SIGHT,
      TRAGOUL_DEATH_SENSE, new GlowLayer(Id.adapt("glow/ranged_trajectory_sight"), 300),
      new GlowLayer(Id.adapt("glow/stealth_threat"), 400), new GlowLayer(Id.adapt("glow/taming_alphas_command"), 600),
      RANGED_HEARTSEEKER, GOLEMANCY_LOCATE);

  @Test
  void restoresLowerLayerWhenHigherLayerReleasesItsClaim() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    Entity target = entity(UUID.randomUUID(), 31);

    assertThat(coordinator.set(STEALTH_SIGHT, target, viewer, ChatColor.AQUA)).isTrue();
    assertThat(coordinator.set(RANGED_HEARTSEEKER, target, viewer, ChatColor.RED)).isTrue();
    assertThat(coordinator.unset(RANGED_HEARTSEEKER, target, viewer)).isTrue();

    InOrder calls = inOrder(sink);
    calls.verify(sink).set(target, viewer, ChatColor.AQUA);
    calls.verify(sink).set(target, viewer, ChatColor.RED);
    calls.verify(sink).set(target, viewer, ChatColor.AQUA);
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId()))
        .isEqualTo(STEALTH_SIGHT);
  }

  @Test
  void replacesTheRuntimeEntityIdBeforeApplyingClaimsToAReusedUuid() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    UUID targetId = UUID.randomUUID();
    Entity original = entity(targetId, 41);
    Entity replacement = entity(targetId, 72);

    assertThat(coordinator.set(STEALTH_SIGHT, original, viewer, ChatColor.AQUA)).isTrue();
    assertThat(coordinator.set(STEALTH_SIGHT, replacement, viewer, ChatColor.AQUA)).isTrue();

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

    assertThat(coordinator.set(STEALTH_SIGHT, target, viewer, ChatColor.AQUA)).isTrue();
    assertThat(coordinator.set(RANGED_HEARTSEEKER, target, viewer, ChatColor.RED)).isFalse();
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId()))
        .isEqualTo(STEALTH_SIGHT);
    assertThat(coordinator.unset(STEALTH_SIGHT, target, viewer)).isTrue();
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
    coordinator.set(STEALTH_SIGHT, target, viewer, ChatColor.AQUA);
    coordinator.set(RANGED_HEARTSEEKER, target, viewer, ChatColor.RED);

    coordinator.clearLayer(RANGED_HEARTSEEKER);

    assertThat(executions).hasValue(1);
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId()))
        .isEqualTo(STEALTH_SIGHT);
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
    coordinator.set(STEALTH_SIGHT, first, viewer, ChatColor.AQUA);
    coordinator.set(TRAGOUL_DEATH_SENSE, second, viewer, ChatColor.DARK_RED);

    assertThat(coordinator.clearAndAwait(100L)).isTrue();

    verify(sink).unset(10, viewer);
    verify(sink).unset(11, viewer);
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), first.getUniqueId())).isNull();
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), second.getUniqueId())).isNull();
  }

  @Test
  void golemancyGlowYieldsToEveryAbilityLayer() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    Entity golem = entity(UUID.randomUUID(), 12);
    coordinator.set(GOLEMANCY, golem, viewer, ChatColor.GOLD);

    for (GlowLayer layer : BUILT_IN) {
      if (layer.equals(GOLEMANCY)) {
        continue;
      }
      coordinator.set(layer, golem, viewer, ChatColor.RED);
      assertThat(coordinator.visibleLayer(viewer.getUniqueId(), golem.getUniqueId())).isEqualTo(layer);
      coordinator.unset(layer, golem, viewer);
      assertThat(coordinator.visibleLayer(viewer.getUniqueId(), golem.getUniqueId()))
          .isEqualTo(GOLEMANCY);
    }
  }

  @Test
  void golemancyLocateOutranksEveryOtherLayer() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    Entity golem = entity(UUID.randomUUID(), 13);
    for (GlowLayer layer : BUILT_IN) {
      coordinator.set(layer, golem, viewer, ChatColor.RED);
    }

    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), golem.getUniqueId()))
        .isEqualTo(GOLEMANCY_LOCATE);

    coordinator.unset(GOLEMANCY_LOCATE, golem, viewer);

    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), golem.getUniqueId()))
        .isEqualTo(RANGED_HEARTSEEKER);
  }

  @Test
  void anAddonLayerStacksWithTheBuiltInOnes() throws Exception {
    ViewerGlowCoordinator.PacketSink sink = mock(ViewerGlowCoordinator.PacketSink.class);
    ViewerGlowCoordinator coordinator = new ViewerGlowCoordinator(sink);
    Player viewer = player(UUID.randomUUID());
    Entity target = entity(UUID.randomUUID(), 14);
    GlowLayer addon = new GlowLayer(Id.of("demo", "marked"), 150);
    coordinator.set(STEALTH_SIGHT, target, viewer, ChatColor.AQUA);
    coordinator.set(addon, target, viewer, ChatColor.GREEN);

    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId())).isEqualTo(addon);
    coordinator.unset(addon, target, viewer);
    assertThat(coordinator.visibleLayer(viewer.getUniqueId(), target.getUniqueId()))
        .isEqualTo(STEALTH_SIGHT);
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
