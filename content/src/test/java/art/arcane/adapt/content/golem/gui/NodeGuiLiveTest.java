package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeItem;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
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
  private final Map<GolemWindow, AtomicBoolean> visibility = new IdentityHashMap<>();
  private final List<Consumer<GolemWindow>> closeCallbacks = new ArrayList<>();
  private MockedConstruction<GolemWindow> windows;
  private MockedStatic<GolemTheme> themes;
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<HudStatus> hud;
  private NodeRecord record;
  private PlayerRef player;

  @BeforeEach
  void setUp() {
    ItemOps itemOps = mock(ItemOps.class);
    lenient().when(itemOps.create(any(Id.class))).thenAnswer(invocation -> new FakeItem(invocation.getArgument(0), 1));
    lenient().when(itemOps.create(any(Id.class), anyInt())).thenAnswer(invocation -> new FakeItem(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(AdaptCore.platform().host().items()).thenReturn(itemOps);
    record = NodeRecord.builder(VIEWER, NodeType.BREAKER, BlockFace.UP).build();
    stored.set(Optional.of(record));
    player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenAnswer(invocation -> online.get());
    NodeService service = mock(NodeService.class);
    lenient().when(service.node(KEY)).thenAnswer(invocation -> {
      scheduler.expect(anchor, "node read");
      return stored.get();
    });
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    lenient().when(policy.level(player)).thenAnswer(invocation -> level.get());
    lenient().when(policy.maxAreaRadius(anyInt())).thenReturn(4);
    lenient().when(policy.filterSlots(anyInt())).thenReturn(3);

    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    scheduler.install(adaptScheduler);
    scheduler.installAt(adaptScheduler, anchor);
    themes = mockStatic(GolemTheme.class);
    themes.when(() -> GolemTheme.element(anyString(), any(Id.class), any(String[].class)))
        .thenAnswer(invocation -> new GolemElement(invocation.getArgument(0)));
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.minHeight()).thenReturn(-64);
    lenient().when(world.maxHeight()).thenReturn(320);
    lenient().when(player.world()).thenReturn(world);
    lenient().when(AdaptCore.platform().host().world(WORLD_ID)).thenReturn(world);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    hud = mockStatic(HudStatus.class);
    windows = mockConstruction(GolemWindow.class, (window, context) -> {
      AtomicBoolean visible = new AtomicBoolean(true);
      visibility.put(window, visible);
      lenient().when(window.isVisible()).thenAnswer(invocation -> visible.get());
      lenient().doAnswer(invocation -> {
        scheduler.expect(player, "window close");
        visible.set(false);
        return null;
      }).when(window).close();
      lenient().when(window.open()).thenAnswer(invocation -> {
        for (Map.Entry<GolemWindow, AtomicBoolean> other : visibility.entrySet()) {
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
    windows.close();
    hud.close();
    runtime.close();
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
    GolemWindow window = windows.constructed().getFirst();
    stored.set(Optional.empty());

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    hud.verify(() -> HudStatus.show(eq(player), anyString()), times(1));
    assertThat(scheduler.pending()).isZero();
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aNodeThatNowBelongsToSomeoneElseClosesTheMenu() {
    openAndDraw();
    GolemWindow window = windows.constructed().getFirst();
    stored.set(Optional.of(NodeRecord.builder(UUID.randomUUID(), NodeType.BREAKER, BlockFace.UP).build()));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void closingTheMenuStopsTheRefresh() {
    openAndDraw();
    GolemWindow window = windows.constructed().getFirst();
    closeCallbacks.getFirst().accept(window);
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
    GolemWindow window = windows.constructed().getFirst();
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
    NodeGui.open(player, KEY, mock(NodeTagPrompt.class), mock(NodeAreaSelector.class));
    scheduler.advance(0);
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).as("one pending poll after the first draw").isEqualTo(1);
  }

  private static NodePanelControl control(String id, String name) {
    return new NodePanelControl(id, GolemTestItems.stack(Id.minecraft("iron_pickaxe"), 1), name, List.of("lore"), null, null, null);
  }
}
