package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.ElementEvent;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.scheduling.Callback;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NodeGuiTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private final List<Callback<Window>> closeCallbacks = new ArrayList<>();
  private final Inventory firstTop = mock(Inventory.class);
  private final Inventory redrawnTop = mock(Inventory.class);
  private MockedConstruction<UIWindow> windows;
  private MockedStatic<GuiTheme> themes;
  private Player player;

  @BeforeEach
  void setUp() {
    when(plugin.getGuiLeftovers()).thenReturn(new ConcurrentHashMap<>());
    themes = mockStatic(GuiTheme.class);
    windows = mockConstruction(UIWindow.class, (window, context) -> {
      lenient().when(window.isVisible()).thenReturn(true);
      lenient().when(window.onClosed(any())).thenAnswer(invocation -> {
        closeCallbacks.add(invocation.getArgument(0));
        return window;
      });
    });
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(firstTop, redrawnTop);
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.getOpenInventory()).thenReturn(view);
  }

  @AfterEach
  void tearDown() {
    GuiCursor.untrack(VIEWER, firstTop);
    GuiCursor.untrack(VIEWER, redrawnTop);
    windows.close();
    themes.close();
  }

  @Test
  void aPanelControlThatOpensAMenuHandsTheViewerToItOnLeftClick() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(Player.class, NodeKey.class, NodeTagPrompt.class);
    constructor.setAccessible(true);
    NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class));
    Method panelElement = NodeGui.class.getDeclaredMethod("panelElement", NodePanelControl.class);
    panelElement.setAccessible(true);
    List<Player> opened = new ArrayList<>();
    NodePanelControl control = new NodePanelControl("fetch", GolemTestItems.stack(Material.COMPASS, 1), "Fetch", List.of(), null, null, opened::add);

    Element element = (Element) panelElement.invoke(gui, control);
    element.call(ElementEvent.LEFT, element);

    assertThat(opened).containsExactly(player);
  }

  @Test
  void aPanelControlCannotBothEditAndOpenOnLeftClick() {
    assertThatThrownBy(() -> new NodePanelControl("both", GolemTestItems.stack(Material.COMPASS, 1), "Both", List.of(), record -> record, null,
        viewer -> {
        })).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theOldMenusLateCloseKeepsInventoryPickupWorkingInTheRedrawnMenu() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.DEPOSIT, BlockFace.UP).build();
    Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(Player.class, NodeKey.class, NodeTagPrompt.class);
    constructor.setAccessible(true);
    NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class));
    Method render = NodeGui.class.getDeclaredMethod("render", NodeRecord.class);
    render.setAccessible(true);

    render.invoke(gui, record);
    render.invoke(gui, record);
    assertThat(GuiCursor.isTracked(VIEWER, redrawnTop)).as("redrawn menu tracked after opening").isTrue();

    closeCallbacks.getFirst().run(windows.constructed().getFirst());

    assertThat(GuiCursor.isTracked(VIEWER, redrawnTop)).as("redrawn menu tracked after the old menu's late close").isTrue();

    closeCallbacks.getLast().run(windows.constructed().getLast());

    assertThat(GuiCursor.isTracked(VIEWER, redrawnTop)).as("redrawn menu untracked after its own close").isFalse();
  }
}
