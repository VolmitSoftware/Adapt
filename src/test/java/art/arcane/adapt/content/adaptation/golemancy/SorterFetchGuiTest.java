package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.GolemGuiSupport;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
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

class SorterFetchGuiTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c001");

  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemGuiSupport> support;
  private SorterFetchGui gui;
  private Player player;

  @BeforeEach
  void setUp() {
    bukkit = mockStatic(Bukkit.class);
    scheduling = mockStatic(J.class);
    support = mockStatic(GolemGuiSupport.class);
    gui = new SorterFetchGui(mock(GolemSorterCore.class), mock(SorterNetwork.class), mock(SorterFetches.class));
    player = mock(Player.class);
  }

  @AfterEach
  void tearDown() {
    support.close();
    scheduling.close();
    bukkit.close();
  }

  @Test
  void aNodeWhoseWorldIsGoneTellsThePlayer() {
    bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(null);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE));
  }

  @Test
  void aNodeWhoseRegionRefusesTheTaskTellsThePlayer() {
    World world = mock(World.class);
    bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
    scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenReturn(false);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE));
  }

  @Test
  void aNodeGoneWhenTheRegionTaskRunsWarnsWithoutAnotherSchedulerHop() {
    World world = mock(World.class);
    bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
    scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });

    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.empty());
      gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    }

    support.verify(() -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE));
    scheduling.verify(() -> J.runEntity(any(), any(Runnable.class)), never());
  }

  @Test
  void anAcceptedTaskSaysNothingUpFront() {
    World world = mock(World.class);
    bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
    scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenReturn(true);

    gui.open(player, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));

    support.verify(() -> GolemGuiSupport.warn(any(Player.class), any()), never());
  }
}
