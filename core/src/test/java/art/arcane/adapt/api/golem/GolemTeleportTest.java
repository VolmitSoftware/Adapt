package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemTeleportTest {
  private static final UUID OVERWORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private MockedStatic<GolemancyRuntime> runtime;
  private RevealService reveal;
  private WorldRef overworld;
  private WorldRef nether;
  private MobRef golem;
  private GolemHandle handle;

  @BeforeEach
  void setUp() {
    reveal = mock(RevealService.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.of(reveal));
    overworld = world(OVERWORLD);
    nether = world(NETHER);
    golem = mock(MobRef.class);
    lenient().when(golem.world()).thenReturn(overworld);
    handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(golem);
  }

  @AfterEach
  void tearDown() {
    runtime.close();
  }

  @Test
  void sameWorldTeleportOnPaperKeepsTheLabelMounted() {
    Loc destination = new Loc(overworld, 4D, 64D, 4D);
    when(golem.teleport(destination, TeleportCause.PLUGIN, true)).thenReturn(CompletableFuture.completedFuture(true));

    assertThat(GolemTeleport.teleport(handle, destination)).isCompletedWithValue(true);

    verify(golem).teleport(destination, TeleportCause.PLUGIN, true);
    verify(golem, never()).eject();
    verify(reveal, never()).forget(any(GolemHandle.class));
  }

  @Test
  void crossWorldTeleportForgetsTheLabelBeforeTeleporting() {
    Loc destination = new Loc(nether, 4D, 64D, 4D);
    when(golem.teleport(destination, TeleportCause.PLUGIN, true)).thenReturn(CompletableFuture.completedFuture(true));

    assertThat(GolemTeleport.teleport(handle, destination)).isCompletedWithValue(true);

    InOrder order = inOrder(reveal, golem);
    order.verify(reveal).forget(handle);
    order.verify(golem).teleport(destination, TeleportCause.PLUGIN, true);
  }

  @Test
  void crossWorldTeleportStillRunsWithoutTheRevealService() {
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.empty());
    Loc destination = new Loc(nether, 4D, 64D, 4D);
    when(golem.teleport(destination, TeleportCause.PLUGIN, true)).thenReturn(CompletableFuture.completedFuture(true));

    assertThat(GolemTeleport.teleport(handle, destination)).isCompletedWithValue(true);
  }

  private static WorldRef world(UUID id) {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(id);
    return world;
  }
}
