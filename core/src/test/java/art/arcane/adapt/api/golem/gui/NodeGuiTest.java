package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.NodeEditorMessages;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuInput;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeGuiTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private final List<Consumer<GuiWindow>> closeCallbacks = new ArrayList<>();
  private MockedConstruction<GuiWindow> windows;
  private MockedStatic<GuiTheme> themes;
  private final NodeAreaSelector areas = mock(NodeAreaSelector.class);
  private PlayerRef player;

  @BeforeEach
  void setUp() {
    ItemOps itemOps = mock(ItemOps.class);
    lenient().when(itemOps.create(any(Id.class))).thenAnswer(invocation -> new FakeItem(invocation.getArgument(0), 1));
    lenient().when(itemOps.create(any(Id.class), anyInt())).thenAnswer(invocation -> new FakeItem(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(AdaptCore.platform().host().items()).thenReturn(itemOps);
    themes = mockStatic(GuiTheme.class);
    themes.when(() -> GuiTheme.element(anyString(), any(Id.class), any(String[].class)))
        .thenAnswer(invocation -> new GuiElement(invocation.getArgument(0)));
    windows = mockConstruction(GuiWindow.class, (window, context) -> {
      lenient().when(window.isVisible()).thenReturn(true);
      lenient().when(window.onClosed(any())).thenAnswer(invocation -> {
        closeCallbacks.add(invocation.getArgument(0));
        return window;
      });
    });
    player = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.minHeight()).thenReturn(-64);
    lenient().when(world.maxHeight()).thenReturn(320);
    lenient().when(player.world()).thenReturn(world);
    lenient().when(player.uuid()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    windows.close();
    themes.close();
  }

  @Test
  void theChannelPickerRendersItsOwnTitleWithoutNodeArguments() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.HARVEST, BlockFace.UP).build();
    Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(PlayerRef.class, NodeKey.class, NodeTagPrompt.class, NodeAreaSelector.class);
    constructor.setAccessible(true);
    NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class), areas);
    Field picker = NodeGui.class.getDeclaredField("channelPicker");
    picker.setAccessible(true);
    picker.setBoolean(gui, true);
    Method render = NodeGui.class.getDeclaredMethod("render", NodeRecord.class);
    render.setAccessible(true);

    render.invoke(gui, record);

    verify(windows.constructed().getFirst()).setTitle("Choose node channel");
  }

  @Test
  void aPanelControlThatOpensAMenuHandsTheViewerToItOnLeftClick() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(PlayerRef.class, NodeKey.class, NodeTagPrompt.class, NodeAreaSelector.class);
    constructor.setAccessible(true);
    NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class), areas);
    Method panelElement = NodeGui.class.getDeclaredMethod("panelElement", NodePanelControl.class);
    panelElement.setAccessible(true);
    List<PlayerRef> opened = new ArrayList<>();
    NodePanelControl control = new NodePanelControl("fetch", GolemTestItems.stack(Id.minecraft("compass"), 1), "Fetch", List.of(), null, null, opened::add);

    GuiElement element = (GuiElement) panelElement.invoke(gui, control);
    click(element, ClickKind.LEFT);

    assertThat(opened).containsExactly(player);
  }

  @Test
  void aPanelControlCannotBothEditAndOpenOnLeftClick() {
    assertThatThrownBy(() -> new NodePanelControl("both", GolemTestItems.stack(Id.minecraft("compass"), 1), "Both", List.of(), record -> record, null,
        viewer -> {
        })).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theOldMenusLateCloseKeepsInventoryPickupWorkingInTheRedrawnMenu() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.DEPOSIT, BlockFace.UP).build();
    Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(PlayerRef.class, NodeKey.class, NodeTagPrompt.class, NodeAreaSelector.class);
    constructor.setAccessible(true);
    NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class), areas);
    Method render = NodeGui.class.getDeclaredMethod("render", NodeRecord.class);
    render.setAccessible(true);

    render.invoke(gui, record);
    render.invoke(gui, record);
    GuiWindow first = windows.constructed().getFirst();
    GuiWindow redrawn = windows.constructed().getLast();
    verify(redrawn).setFreePlayerInventory(true);

    closeCallbacks.getFirst().accept(first);

    verify(redrawn, never()).close();
    verify(redrawn).setFreePlayerInventory(true);

    closeCallbacks.getLast().accept(redrawn);

    verify(redrawn, never()).setFreePlayerInventory(false);
  }

  @Test
  void areaControlsResizeByOneOnClickAndByFourOnShiftClickWithinTheCap() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(2, 8, 30)).build();
    NodeService service = mock(NodeService.class);
    AtomicReference<NodeRecord> result = new AtomicReference<>();
    lenient().when(service.update(eq(VIEWER), eq(key), any())).thenAnswer(invocation -> {
      UnaryOperator<NodeRecord> change = invocation.getArgument(2);
      result.set(change.apply(record));
      return Optional.of(result.get());
    });
    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
         MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      WorldRef world = mock(WorldRef.class);
      when(world.minHeight()).thenReturn(-64);
      when(world.maxHeight()).thenReturn(320);
      when(AdaptCore.platform().host().world(WORLD)).thenReturn(world);
      scheduling.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenReturn(true);
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
      Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(PlayerRef.class, NodeKey.class, NodeTagPrompt.class, NodeAreaSelector.class);
      constructor.setAccessible(true);
      NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class), areas);
      Method placeArea = NodeGui.class.getDeclaredMethod("placeArea", GuiWindow.class, NodeRecord.class, int.class);
      placeArea.setAccessible(true);
      GuiWindow target = mock(GuiWindow.class);

      placeArea.invoke(gui, target, record, 10);

      ArgumentCaptor<GuiElement> placed = ArgumentCaptor.forClass(GuiElement.class);
      verify(target, times(4)).setElement(anyInt(), anyInt(), placed.capture());
      GuiElement x = placed.getAllValues().get(0);
      GuiElement y = placed.getAllValues().get(1);
      GuiElement z = placed.getAllValues().get(2);
      assertThat(x.getLore()).anyMatch(line -> line.contains("Shift: 4 blocks"));
      assertThat(x.getName()).contains("X Area: -2 to +2");
      assertThat(z.getName()).contains("Z Area: -8 to +7");

      click(x, ClickKind.LEFT);
      assertThat(result.get().area()).isEqualTo(new NodeCuboid(-2, -8, -8, 3, 7, 7));
      click(x, ClickKind.SHIFT_LEFT);
      assertThat(result.get().area()).isEqualTo(new NodeCuboid(-4, -8, -8, 4, 7, 7));
      click(x, ClickKind.RIGHT);
      assertThat(result.get().area()).isEqualTo(new NodeCuboid(-2, -8, -8, 1, 7, 7));
      click(x, ClickKind.SHIFT_RIGHT);
      assertThat(result.get().area()).isEqualTo(new NodeCuboid(0, -8, -8, 0, 7, 7));
      click(y, ClickKind.SHIFT_LEFT);
      assertThat(result.get().area()).isEqualTo(new NodeCuboid(-2, -8, -8, 2, 7, 7));
      click(z, ClickKind.SHIFT_RIGHT);
      assertThat(result.get().area()).isEqualTo(new NodeCuboid(-2, -8, -6, 2, 7, 5));
      assertThat(NodeEdits.SHIFT_STEP).isEqualTo(4);
    }
  }

  @Test
  void theSelectAreaButtonHandsTheNodeToCrookSelection() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.HARVEST, BlockFace.UP).area(new NodeCuboid(0, -1, -3, 5, 2, 0)).build();
    Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(PlayerRef.class, NodeKey.class, NodeTagPrompt.class, NodeAreaSelector.class);
    constructor.setAccessible(true);
    NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class), areas);
    Method placeArea = NodeGui.class.getDeclaredMethod("placeArea", GuiWindow.class, NodeRecord.class, int.class);
    placeArea.setAccessible(true);
    GuiWindow target = mock(GuiWindow.class);

    placeArea.invoke(gui, target, record, 8);

    ArgumentCaptor<GuiElement> placed = ArgumentCaptor.forClass(GuiElement.class);
    verify(target, times(4)).setElement(anyInt(), anyInt(), placed.capture());
    GuiElement x = placed.getAllValues().get(0);
    GuiElement y = placed.getAllValues().get(1);
    GuiElement z = placed.getAllValues().get(2);
    GuiElement select = placed.getAllValues().get(3);
    assertThat(x.getName()).contains("X Area: 0 to +5");
    assertThat(y.getName()).contains("Y Area: -1 to +2");
    assertThat(z.getName()).contains("Z Area: -3 to 0");
    assertThat(select.getName()).contains("Shift work area");

    click(select, ClickKind.LEFT);

    verify(areas).toggle(player, key, record);
  }

  @Test
  void theNodeMenuIsAGridOfNamedSettingsWithAnInScreenTagField() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.HARVEST, BlockFace.UP).priority(2).area(NodeCuboid.symmetric(2, 8, 30)).build();
    NodeTagPrompt prompt = mock(NodeTagPrompt.class);
    MenuInput typed = (context, text) -> {
    };
    when(prompt.input(key)).thenReturn(typed);
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.level(player)).thenReturn(5);
    lenient().when(policy.filterSlots(anyInt())).thenReturn(9);
    lenient().when(policy.maxAreaRadius(anyInt())).thenReturn(8);
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
      render(newGui(key, prompt), record);
    }
    GuiWindow window = windows.constructed().getFirst();

    PanelView view = MenuReplay.view(window);

    assertThat(view.layout()).isEqualTo(PanelLayout.GRID);
    PanelTile priority = MenuReplay.tile(view, -1, 0);
    assertThat(priority.role()).isEqualTo(TileRole.SETTING);
    assertThat(priority.hint().kind()).isEqualTo(HintKind.NUMBER);
    assertThat(priority.hint().typed()).isFalse();
    assertThat(priority.value()).isEqualTo("2");
    assertThat(priority.amount()).isEqualTo(2);
    PanelTile redstone = MenuReplay.tile(view, 0, 0);
    assertThat(redstone.hint().kind()).isEqualTo(HintKind.CHOICE);
    assertThat(redstone.hint().options()).hasSize(3);
    PanelTile mode = MenuReplay.tile(view, 2, 0);
    assertThat(mode.hint().kind()).isEqualTo(HintKind.CHOICE);
    assertThat(mode.value()).isEqualTo(AdaptLanguage.text(GolemancyMessages.NODE_GUI_WHITELIST));
    assertThat(mode.lore()).hasSize(mode.description().size() + 1);
    PanelTile tag = MenuReplay.tile(view, 3, 0);
    assertThat(tag.role()).isEqualTo(TileRole.SETTING);
    assertThat(tag.hint().kind()).isEqualTo(HintKind.TEXT);
    assertThat(tag.hint().typed()).isTrue();
    assertThat(tag.lore()).anyMatch(line -> line.contains("#logs"));
    assertThat(MenuReplay.actions(tag)).containsEntry(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG_ADD));
    PanelTile x = MenuReplay.tile(view, -2, 1);
    assertThat(x.hint().kind()).isEqualTo(HintKind.NUMBER);
    assertThat(x.value()).isEqualTo("5");
    assertThat(MenuReplay.actions(x)).containsEntry(ClickKind.LEFT, "+1").containsEntry(ClickKind.RIGHT, "-1")
        .containsEntry(ClickKind.SHIFT_LEFT, "+4").containsEntry(ClickKind.SHIFT_RIGHT, "-4");
    assertThat(x.lore()).anyMatch(line -> line.contains("Shift: 4 blocks"));
    assertThat(x.description()).noneMatch(line -> line.contains("Shift: 4 blocks"));
    assertThat(MenuReplay.tile(view, -4, 0).role()).isEqualTo(TileRole.INFO);
    assertThat(MenuReplay.tile(view, -2, 0).role()).isEqualTo(TileRole.NAVIGATION);
    assertThat(MenuReplay.tile(view, -4, 2).role()).isEqualTo(TileRole.ACTION);
  }

  @Test
  void theTagButtonStillStartsTheChatPromptOnClick() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.HARVEST, BlockFace.UP).build();
    NodeTagPrompt prompt = mock(NodeTagPrompt.class);
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.level(player)).thenReturn(5);
    lenient().when(policy.filterSlots(anyInt())).thenReturn(9);
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
      render(newGui(key, prompt), record);
    }
    ArgumentCaptor<GuiElement> placed = ArgumentCaptor.forClass(GuiElement.class);
    verify(windows.constructed().getFirst()).setElement(eq(3), eq(0), placed.capture());
    GuiElement tag = placed.getValue();

    click(tag, ClickKind.LEFT);

    verify(prompt).begin(player, key);
    assertThat(tag.getHint().kind()).isEqualTo(MenuHint.Kind.TEXT);
    assertThat(tag.button().icon().lore()).containsExactly("§7Click, then type an item tag such as #logs", "§7in chat");
  }

  @Test
  void theChannelPickerIsAListThatMarksTheChosenChannel() throws ReflectiveOperationException {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(VIEWER, NodeType.HARVEST, BlockFace.UP).build();
    NodeGui gui = newGui(key, mock(NodeTagPrompt.class));
    Field picker = NodeGui.class.getDeclaredField("channelPicker");
    picker.setAccessible(true);
    picker.setBoolean(gui, true);

    render(gui, record);
    PanelView view = MenuReplay.view(windows.constructed().getFirst());

    assertThat(view.layout()).isEqualTo(PanelLayout.LIST);
    assertThat(view.breadcrumb()).hasSize(1);
    PanelTile none = MenuReplay.tile(view, 0, 3);
    assertThat(none.name()).contains(AdaptLanguage.text(NodeEditorMessages.CHANNEL_CLEAR));
    assertThat(none.value()).isEqualTo(AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED));
    assertThat(MenuReplay.tile(view, -3, 1).value()).isEmpty();
  }

  private NodeGui newGui(NodeKey key, NodeTagPrompt prompt) throws ReflectiveOperationException {
    Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(PlayerRef.class, NodeKey.class, NodeTagPrompt.class, NodeAreaSelector.class);
    constructor.setAccessible(true);
    return constructor.newInstance(player, key, prompt, areas);
  }

  private static void render(NodeGui gui, NodeRecord record) throws ReflectiveOperationException {
    Method render = NodeGui.class.getDeclaredMethod("render", NodeRecord.class);
    render.setAccessible(true);
    render.invoke(gui, record);
  }

  private void click(GuiElement element, ClickKind kind) {
    element.button().action().click(new MenuClick(player, null, 0, kind));
  }
}
