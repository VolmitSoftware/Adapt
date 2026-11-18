package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.GolemGuiSupport;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class SorterFetchGuiTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c001");

  private MockedStatic<AdaptCore> core;
  private MockedStatic<Tasks> scheduling;
  private PlatformHost host;
  private MockedStatic<GolemGuiSupport> support;
  private SorterFetchGui gui;
  private PlayerRef player;

  @BeforeEach
  void setUp() {
    host = mock(PlatformHost.class);
    AdaptPlatform platform = mock(AdaptPlatform.class);
    when(platform.host()).thenReturn(host);
    core = mockStatic(AdaptCore.class);
    core.when(AdaptCore::platform).thenReturn(platform);
    scheduling = mockStatic(Tasks.class);
    support = mockStatic(GolemGuiSupport.class);
    gui = new SorterFetchGui(mock(GolemSorterCore.class), mock(SorterNetwork.class), mock(SorterFetches.class));
    player = mock(PlayerRef.class);
  }

  @AfterEach
  void tearDown() {
    support.close();
    scheduling.close();
    core.close();
  }

  @Test
  void aNodeWhoseWorldIsGoneTellsThePlayer() {
    when(host.world(WORLD)).thenReturn(null);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE));
  }

  @Test
  void aNodeWhoseRegionRefusesTheTaskTellsThePlayer() {
    WorldRef world = mock(WorldRef.class);
    when(host.world(WORLD)).thenReturn(world);
    scheduling.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenReturn(false);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE));
  }

  @Test
  void aNodeGoneWhenTheRegionTaskRunsWarnsWithoutAnotherSchedulerHop() {
    WorldRef world = mock(WorldRef.class);
    when(host.world(WORLD)).thenReturn(world);
    scheduling.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });

    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.empty());
      gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    }

    support.verify(() -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE));
    scheduling.verify(() -> Tasks.runEntity(any(), any(Runnable.class)), never());
  }

  @Test
  void anAcceptedTaskSaysNothingUpFront() {
    WorldRef world = mock(WorldRef.class);
    when(host.world(WORLD)).thenReturn(world);
    scheduling.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenReturn(true);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(any(PlayerRef.class), any()), never());
  }
}
