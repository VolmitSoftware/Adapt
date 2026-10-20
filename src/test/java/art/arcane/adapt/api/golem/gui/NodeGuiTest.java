package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.ElementEvent;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.scheduling.Callback;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

  @Test
  void areaControlsResizeByOneOnClickAndByFourOnShiftClickWithinTheCap() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.HARVEST, BlockFace.UP).radii(2, 8, 30).build();
    themes.when(() -> GuiTheme.element(anyString(), any(Material.class), any(String[].class)))
        .thenAnswer(invocation -> new UIElement(invocation.getArgument(0)));
    NodeService service = mock(NodeService.class);
    AtomicReference<NodeRecord> result = new AtomicReference<>();
    lenient().when(service.update(eq(VIEWER), eq(key), any())).thenAnswer(invocation -> {
      UnaryOperator<NodeRecord> change = invocation.getArgument(2);
      result.set(change.apply(record));
      return Optional.of(result.get());
    });
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(mock(World.class));
      scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
      Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(Player.class, NodeKey.class, NodeTagPrompt.class);
      constructor.setAccessible(true);
      NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class));
      Method placeArea = NodeGui.class.getDeclaredMethod("placeArea", UIWindow.class, NodeRecord.class, int.class);
      placeArea.setAccessible(true);
      UIWindow target = mock(UIWindow.class);

      placeArea.invoke(gui, target, record, 10);

      ArgumentCaptor<Element> placed = ArgumentCaptor.forClass(Element.class);
      verify(target, times(3)).setElement(anyInt(), anyInt(), placed.capture());
      Element x = placed.getAllValues().get(0);
      Element y = placed.getAllValues().get(1);
      Element z = placed.getAllValues().get(2);
      assertThat(x.getLore()).anyMatch(line -> line.contains("Shift-click to change by 4"));

      x.call(ElementEvent.LEFT, x);
      assertThat(result.get().radiusX()).isEqualTo(3);
      x.call(ElementEvent.SHIFT_LEFT, x);
      assertThat(result.get().radiusX()).isEqualTo(6);
      x.call(ElementEvent.RIGHT, x);
      assertThat(result.get().radiusX()).isEqualTo(1);
      x.call(ElementEvent.SHIFT_RIGHT, x);
      assertThat(result.get().radiusX()).isZero();
      y.call(ElementEvent.SHIFT_LEFT, y);
      assertThat(result.get().radiusY()).isEqualTo(10);
      z.call(ElementEvent.SHIFT_RIGHT, z);
      assertThat(result.get().radiusZ()).isEqualTo(26);
      assertThat(NodeEdits.SHIFT_STEP).isEqualTo(4);
    }
  }
}
