package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.scheduling.Callback;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RosterGuiLiveTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000000f4");
  private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-0000000000f5");

  private final ManualScheduler scheduler = new ManualScheduler();
  private final GolemRoster roster = new GolemRoster();
  private final AtomicReference<Double> health = new AtomicReference<>(20D);
  private final AtomicInteger blockX = new AtomicInteger(10);
  private final AtomicBoolean online = new AtomicBoolean(true);
  private final AtomicBoolean dataLoaded = new AtomicBoolean(true);
  private final Map<UIWindow, AtomicBoolean> visibility = new IdentityHashMap<>();
  private final List<Callback<Window>> closeCallbacks = new ArrayList<>();
  private MockedConstruction<UIWindow> windows;
  private MockedStatic<GuiTheme> themes;
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<GolemancyRuntime> runtime;
  private Player player;

  @BeforeEach
  void setUp() {
    when(plugin.getGuiLeftovers()).thenReturn(new ConcurrentHashMap<>());
    World world = mock(World.class);
    lenient().when(world.getName()).thenReturn("world");
    IronGolem golem = mock(IronGolem.class);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.getHealth()).thenAnswer(invocation -> health.get());
    lenient().when(golem.getLocation()).thenAnswer(invocation -> new Location(world, blockX.get(), 64, 0));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(FIRST);
    lenient().when(handle.isRetired()).thenReturn(false);
    lenient().when(handle.entity()).thenReturn(golem);
    GolemRegistry registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(VIEWER)).thenReturn(List.of(handle));
    roster.add(new GolemRoster.Binding(FIRST, GolemBody.IRON, GolemCoreType.BREAKER, WORLD_ID, 0L, 0L));
    roster.add(new GolemRoster.Binding(SECOND, GolemBody.COPPER, GolemCoreType.LUMBER, WORLD_ID, 0L, 0L));
    PlayerData data = mock(PlayerData.class);
    lenient().when(data.getGolemancy()).thenReturn(roster);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    lenient().when(adaptPlayer.getData()).thenReturn(data);
    AdaptServer server = mock(AdaptServer.class);
    lenient().when(server.getOnlineAdaptPlayer(VIEWER)).thenAnswer(invocation -> dataLoaded.get() ? adaptPlayer : null);
    lenient().when(plugin.getAdaptServer()).thenReturn(server);
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenAnswer(invocation -> online.get());
    lenient().when(player.getWorld()).thenReturn(world);

    scheduling = mockStatic(J.class);
    scheduler.install(scheduling);
    scheduling.when(J::isPrimaryThread).thenReturn(true);
    themes = mockStatic(GuiTheme.class, invocation -> "element".equals(invocation.getMethod().getName())
        ? new UIElement(invocation.getArgument(0))
        : null);
    bukkit = mockStatic(Bukkit.class);
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(20D);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
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
      lenient().when(window.batch(any())).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(0).run();
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
    windows.close();
    runtime.close();
    vitals.close();
    bukkit.close();
    themes.close();
    scheduling.close();
  }

  @Test
  void aRosterThatNeverChangesIsNeverRedrawn() {
    openAndDraw();

    scheduler.advance(LiveRefresh.POLL_TICKS * 50);

    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void aGolemThatLosesHealthRedrawsTheRosterInPlace() {
    openAndDraw();
    health.set(15D);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(2);
    assertThat(scheduler.pending()).as("only the redrawn roster keeps polling").isEqualTo(1);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void healthThatKeepsTheShownValueDoesNotRedraw() {
    openAndDraw();
    health.set(19.5D);

    scheduler.advance(LiveRefresh.POLL_TICKS * 5);

    assertThat(windows.constructed()).hasSize(1);
  }

  @Test
  void aGolemThatWalksToAnotherBlockRedrawsTheRoster() {
    openAndDraw();
    blockX.set(11);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(2);
  }

  @Test
  void aGolemThatIsPackedRedrawsTheRoster() {
    openAndDraw();
    roster.transition(FIRST, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(2);
  }

  @Test
  void aNewlyBoundGolemRedrawsTheRoster() {
    openAndDraw();
    roster.add(new GolemRoster.Binding(THIRD, GolemBody.FROST, GolemCoreType.GUARDIAN, WORLD_ID, 0L, 0L));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(2);
  }

  @Test
  void aRedrawnRosterKeepsTheSamePage() {
    openAndDraw();
    health.set(15D);
    scheduler.advance(LiveRefresh.POLL_TICKS);
    health.set(14D);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(windows.constructed()).hasSize(3);
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void closingTheRosterStopsTheRefresh() {
    openAndDraw();
    UIWindow window = windows.constructed().getFirst();
    closeCallbacks.getFirst().run(window);
    health.set(15D);

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aPlayerWhoQuitsStopsTheRefresh() {
    openAndDraw();
    online.set(false);
    health.set(15D);

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aPlayerWhoseDataUnloadedClosesTheRoster() {
    openAndDraw();
    UIWindow window = windows.constructed().getFirst();
    dataLoaded.set(false);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window).close();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aRosterReplacedByAnotherMenuStopsTheRefresh() {
    openAndDraw();
    visibility.get(windows.constructed().getFirst()).set(false);
    health.set(15D);

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(windows.constructed().getFirst(), never()).close();
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void nothingIsScheduledWhileNoRosterIsOpen() {
    scheduler.advance(LiveRefresh.POLL_TICKS * 100);

    assertThat(scheduler.pending()).isZero();
    assertThat(windows.constructed()).isEmpty();
  }

  @Test
  void aViewOfOnePageIgnoresChangesToGolemsOnTheOtherPage() {
    GolemRoster large = new GolemRoster();
    List<UUID> ids = new ArrayList<>();
    for (int index = 0; index < 60; index++) {
      UUID id = new UUID(0L, index + 1L);
      ids.add(id);
      large.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BREAKER, WORLD_ID, 0L, 0L));
    }
    Map<UUID, GolemHandle> none = Map.of();
    RosterGui.RosterView before = RosterGui.view(player, RosterGui.entries(large), none, 0, false, false, false);

    large.transition(ids.getLast(), GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);
    RosterGui.RosterView afterLastPacked = RosterGui.view(player, RosterGui.entries(large), none, 0, false, false, false);
    large.transition(ids.getFirst(), GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);
    RosterGui.RosterView afterFirstPacked = RosterGui.view(player, RosterGui.entries(large), none, 0, false, false, false);

    assertThat(before.rows()).hasSize(45);
    assertThat(afterLastPacked).isEqualTo(before);
    assertThat(afterFirstPacked).isNotEqualTo(before);
  }

  private void openAndDraw() {
    RosterGui.open(player, 0);
    scheduler.advance(0);
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).as("one pending poll after the first draw").isEqualTo(1);
    verify(windows.constructed().getFirst(), never()).close();
  }
}
