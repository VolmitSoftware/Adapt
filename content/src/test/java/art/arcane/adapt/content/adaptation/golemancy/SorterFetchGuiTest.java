package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.gui.GolemGuiSupport;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SorterFetchGuiTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c001");

  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private PlatformHost host;
  private MockedStatic<GolemGuiSupport> support;
  private SorterFetchGui gui;
  private PlayerRef player;

  @BeforeEach
  void setUp() {
    host = AdaptCore.platform().host();
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    support = mockStatic(GolemGuiSupport.class);
    gui = new SorterFetchGui(mock(GolemSorterCore.class), mock(SorterNetwork.class), mock(SorterFetches.class));
    player = mock(PlayerRef.class);
  }

  @AfterEach
  void tearDown() {
    support.close();
    scheduling.close();
  }

  @Test
  void aNodeWhoseWorldIsGoneTellsThePlayer() {
    when(host.world(WORLD)).thenReturn(null);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone")));
  }

  @Test
  void aNodeWhoseRegionRefusesTheTaskTellsThePlayer() {
    WorldRef world = mock(WorldRef.class);
    when(host.world(WORLD)).thenReturn(world);
    when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class))).thenReturn(false);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone")));
  }

  @Test
  void aNodeGoneWhenTheRegionTaskRunsWarnsWithoutAnotherSchedulerHop() {
    WorldRef world = mock(WorldRef.class);
    when(host.world(WORLD)).thenReturn(world);
    when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });

    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.empty());
      gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    }

    support.verify(() -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone")));
    verify(adaptScheduler, never()).runEntity(any(), any(Runnable.class));
  }

  @Test
  void anAcceptedTaskSaysNothingUpFront() {
    WorldRef world = mock(WorldRef.class);
    when(host.world(WORLD)).thenReturn(world);
    when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class))).thenReturn(true);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(any(PlayerRef.class), any()), never());
  }
}
