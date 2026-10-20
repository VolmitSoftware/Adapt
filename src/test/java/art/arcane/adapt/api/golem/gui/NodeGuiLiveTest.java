package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.scheduling.Callback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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

class NodeGuiLiveTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final NodeKey KEY = new NodeKey(WORLD_ID, 0, 64, 0, BlockFace.UP);

  private final ManualScheduler scheduler = new ManualScheduler();
  private final Object anchor = new Object();
  private final AtomicReference<Optional<NodeRecord>> stored = new AtomicReference<>();
  private final AtomicInteger level = new AtomicInteger(3);
  private final AtomicReference<List<NodePanelControl>> panel = new AtomicReference<>(List.of());
  private final AtomicBoolean online = new AtomicBoolean(true);
  private final Map<UIWindow, AtomicBoolean> visibility = new IdentityHashMap<>();
  private final List<Callback<Window>> closeCallbacks = new ArrayList<>();
  private final Inventory top = mock(Inventory.class);
  private MockedConstruction<UIWindow> windows;
  private MockedStatic<GuiTheme> themes;
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<AdaptHud> hud;
  private NodeRecord record;
  private Player player;

  @BeforeEach
  void setUp() {
    when(plugin.getGuiLeftovers()).thenReturn(new ConcurrentHashMap<>());
    record = NodeRecord.builder(VIEWER, NodeType.BREAKER, BlockFace.UP).build();
    stored.set(Optional.of(record));
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenAnswer(invocation -> online.get());
    InventoryView view = mock(InventoryView.class);
    lenient().when(view.getTopInventory()).thenReturn(top);
    lenient().when(player.getOpenInventory()).thenReturn(view);
    NodeService service = mock(NodeService.class);
    lenient().when(service.node(KEY)).thenAnswer(invocation -> {
      scheduler.expect(anchor, "node read");
      return stored.get();
    });
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    lenient().when(policy.level(player)).thenAnswer(invocation -> level.get());
    lenient().when(policy.maxAreaRadius(anyInt())).thenReturn(4);
    lenient().when(policy.filterSlots(anyInt())).thenReturn(3);

    scheduling = mockStatic(J.class);
    scheduler.install(scheduling);
    scheduler.installAt(scheduling, anchor);
    themes = mockStatic(GuiTheme.class);
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getWorld(WORLD_ID)).thenReturn(mock(World.class));
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    hud = mockStatic(AdaptHud.class);
    windows = mockConstruction(UIWindow.class, (window, context) -> {
      AtomicBoolean visible = new AtomicBoolean(true);
      visibility.put(window, visible);
      lenient().when(window.isVisible()).thenAnswer(invocation -> visible.get());
      lenient().when(window.close()).thenAnswer(invocation -> {
        scheduler.expect(player, "window close");
        visible.set(false);
        return window;
      });
      lenient().when(window.open()).thenAnswer(invocation -> {
        for (Map.Entry<UIWindow, AtomicBoolean> other : visibility.entrySet()) {
          if (other.getKey() != window) {
            other.getValue().set(false);
          }
        }
        return window;
      });
      lenient().when(window.onClosed(any())).thenAnswer(invocation -> {
        closeCallbacks.add(invocation.getArgument(0));
        return window;
      });
    });
  }

  @AfterEach
  void tearDown() {
    NodePanels.of(NodeType.BREAKER).ifPresent(registered -> NodePanels.unregister(NodeType.BREAKER, registered));
    GuiCursor.untrack(VIEWER, top);
    windows.close();
    hud.close();
    runtime.close();
    bukkit.close();
    themes.close();
    scheduling.close();
  }

  @Test
  void aNodeThatNeverChangesIsNeverRedrawn() {
    openAndDraw();

    scheduler.advance(LiveRefresh.POLL_TICKS * 50);

    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void aNodeEditedElsewhereRedrawsTheOpenMenu() {
    openAndDraw();
    stored.set(Optional.of(record.toBuilder().priority(5).build()));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(2);
    assertThat(scheduler.pending()).as("only the redrawn menu keeps polling").isEqualTo(1);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aChangedNodeWeavingLevelRedrawsTheMenu() {
    openAndDraw();
    level.set(4);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(2);
  }

  @Test
  void aChangedPanelControlRedrawsTheMenu() {
    NodePanels.register(NodeType.BREAKER, context -> panel.get());
    panel.set(List.of(control("breaker-filter", "Filter needed")));
    openAndDraw();
    panel.set(List.of(control("breaker-filter", "Filter ready")));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(2);
  }

  @Test
  void anUnchangedPanelDoesNotRedrawTheMenu() {
    NodePanels.register(NodeType.BREAKER, context -> panel.get());
    panel.set(List.of(control("breaker-filter", "Filter needed")));
    openAndDraw();
    panel.set(List.of(control("breaker-filter", "Filter needed")));

    scheduler.advance(LiveRefresh.POLL_TICKS * 5);

    assertThat(windows.constructed()).hasSize(1);
  }

  @Test
  void aNodeThatWasRemovedClosesTheMenu() {
    openAndDraw();
    UIWindow window = windows.constructed().getFirst();
    stored.set(Optional.empty());

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    hud.verify(() -> AdaptHud.actionBar(eq(player), anyString()), times(1));
    assertThat(scheduler.pending()).isZero();
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aNodeThatNowBelongsToSomeoneElseClosesTheMenu() {
    openAndDraw();
    UIWindow window = windows.constructed().getFirst();
    stored.set(Optional.of(NodeRecord.builder(UUID.randomUUID(), NodeType.BREAKER, BlockFace.UP).build()));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void closingTheMenuStopsTheRefresh() {
    openAndDraw();
    UIWindow window = windows.constructed().getFirst();
    closeCallbacks.getFirst().run(window);
    stored.set(Optional.of(record.toBuilder().priority(5).build()));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aPlayerWhoQuitsStopsTheRefresh() {
    openAndDraw();
    online.set(false);
    stored.set(Optional.of(record.toBuilder().priority(5).build()));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aMenuReplacedByAnotherStopsTheRefreshWithoutClosingIt() {
    openAndDraw();
    UIWindow window = windows.constructed().getFirst();
    visibility.get(window).set(false);
    stored.set(Optional.of(record.toBuilder().priority(5).build()));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(window, never()).close();
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void nothingIsScheduledWhileNoNodeMenuIsOpen() {
    scheduler.advance(LiveRefresh.POLL_TICKS * 100);

    assertThat(scheduler.pending()).isZero();
    assertThat(windows.constructed()).isEmpty();
  }

  private void openAndDraw() {
    NodeGui.open(player, KEY, mock(NodeTagPrompt.class));
    scheduler.advance(0);
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).as("one pending poll after the first draw").isEqualTo(1);
  }

  private static NodePanelControl control(String id, String name) {
    return new NodePanelControl(id, GolemTestItems.stack(Material.IRON_PICKAXE, 1), name, List.of("lore"), null, null, null);
  }
}
