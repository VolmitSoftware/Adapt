package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodeEdits;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuInput;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeItem;
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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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

  private final List<Consumer<GolemWindow>> closeCallbacks = new ArrayList<>();
  private MockedConstruction<GolemWindow> windows;
  private MockedStatic<GolemTheme> themes;
  private final NodeAreaSelector areas = mock(NodeAreaSelector.class);
  private PlayerRef player;

  @BeforeEach
  void setUp() {
    ItemOps itemOps = mock(ItemOps.class);
    lenient().when(itemOps.create(any(Id.class))).thenAnswer(invocation -> new FakeItem(invocation.getArgument(0), 1));
    lenient().when(itemOps.create(any(Id.class), anyInt())).thenAnswer(invocation -> new FakeItem(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(AdaptCore.platform().host().items()).thenReturn(itemOps);
    themes = mockStatic(GolemTheme.class);
    themes.when(() -> GolemTheme.element(anyString(), any(Id.class), any(String[].class)))
        .thenAnswer(invocation -> new GolemElement(invocation.getArgument(0)));
    windows = mockConstruction(GolemWindow.class, (window, context) -> {
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

    GolemElement element = (GolemElement) panelElement.invoke(gui, control);
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
    GolemWindow first = windows.constructed().getFirst();
    GolemWindow redrawn = windows.constructed().getLast();
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
    AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
    try (MockedStatic<AdaptApi> scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
         MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
      WorldRef world = mock(WorldRef.class);
      when(world.minHeight()).thenReturn(-64);
      when(world.maxHeight()).thenReturn(320);
      when(AdaptCore.platform().host().world(WORLD)).thenReturn(world);
      when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenReturn(true);
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
      Constructor<NodeGui> constructor = NodeGui.class.getDeclaredConstructor(PlayerRef.class, NodeKey.class, NodeTagPrompt.class, NodeAreaSelector.class);
      constructor.setAccessible(true);
      NodeGui gui = constructor.newInstance(player, key, mock(NodeTagPrompt.class), areas);
      Method placeArea = NodeGui.class.getDeclaredMethod("placeArea", GolemWindow.class, NodeRecord.class, int.class);
      placeArea.setAccessible(true);
      GolemWindow target = mock(GolemWindow.class);

      placeArea.invoke(gui, target, record, 10);

      ArgumentCaptor<GolemElement> placed = ArgumentCaptor.forClass(GolemElement.class);
      verify(target, times(4)).setElement(anyInt(), anyInt(), placed.capture());
      GolemElement x = placed.getAllValues().get(0);
      GolemElement y = placed.getAllValues().get(1);
      GolemElement z = placed.getAllValues().get(2);
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
    Method placeArea = NodeGui.class.getDeclaredMethod("placeArea", GolemWindow.class, NodeRecord.class, int.class);
    placeArea.setAccessible(true);
    GolemWindow target = mock(GolemWindow.class);

    placeArea.invoke(gui, target, record, 8);

    ArgumentCaptor<GolemElement> placed = ArgumentCaptor.forClass(GolemElement.class);
    verify(target, times(4)).setElement(anyInt(), anyInt(), placed.capture());
    GolemElement x = placed.getAllValues().get(0);
    GolemElement y = placed.getAllValues().get(1);
    GolemElement z = placed.getAllValues().get(2);
    GolemElement select = placed.getAllValues().get(3);
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
    GolemWindow window = windows.constructed().getFirst();

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
    assertThat(mode.value()).isEqualTo(Text.key("adapt.golemancy.node_gui.whitelist").resolve());
    assertThat(mode.lore()).hasSize(mode.description().size() + 1);
    PanelTile tag = MenuReplay.tile(view, 3, 0);
    assertThat(tag.role()).isEqualTo(TileRole.SETTING);
    assertThat(tag.hint().kind()).isEqualTo(HintKind.TEXT);
    assertThat(tag.hint().typed()).isTrue();
    assertThat(tag.lore()).anyMatch(line -> line.contains("#logs"));
    assertThat(MenuReplay.actions(tag)).containsEntry(ClickKind.LEFT, Text.key("adapt.golemancy.node_gui.tag_add").resolve());
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
    ArgumentCaptor<GolemElement> placed = ArgumentCaptor.forClass(GolemElement.class);
    verify(windows.constructed().getFirst()).setElement(eq(3), eq(0), placed.capture());
    GolemElement tag = placed.getValue();

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
    assertThat(none.name()).contains(Text.key("adapt.golemancy.node.editor.channel_clear").resolve());
    assertThat(none.value()).isEqualTo(Text.key("adapt.golemancy.gui.channel_selected").resolve());
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

  private void click(GolemElement element, ClickKind kind) {
    element.button().action().click(new MenuClick(player, null, 0, kind));
  }
}
