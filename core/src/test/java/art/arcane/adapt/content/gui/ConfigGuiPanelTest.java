package art.arcane.adapt.content.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.server.panel.PanelViews;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigGuiPanelTest extends AdaptTestBase {
  private PlayerRef player;
  private MenuHost menus;

  @BeforeEach
  void setUpViewer() {
    player = mock(PlayerRef.class, Answers.RETURNS_MOCKS);
    lenient().when(player.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(player.isOp()).thenReturn(true);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(core.getPlatform().host().items()).thenReturn(mock(ItemOps.class, Answers.RETURNS_MOCKS));
    menus = core.getPlatform().menus();
    lenient().when(menus.open(any(PlayerRef.class), any(ChestMenu.class))).thenReturn(mock(MenuSession.class));
  }

  @Test
  void generalSettingsBecomeAFormWithControlsPerType() {
    ChestMenu menu = open("core.$general");
    PanelView view = PanelViews.view(1, 0, menu);

    assertThat(view.layout()).isEqualTo(PanelLayout.FORM);
    assertThat(view.breadcrumb()).containsExactly("Configure Adapt", "Core");
    PanelTile toggle = tile(view, "core.metrics");
    assertThat(toggle.role()).isEqualTo(TileRole.SETTING);
    assertThat(toggle.hint().kind()).isEqualTo(HintKind.TOGGLE);
    assertThat(toggle.name()).doesNotContain("[Boolean]");
    assertThat(toggle.actions()).extracting(action -> action.click()).containsExactly(ClickKind.LEFT);
    assertThat(toggle.shownDescription()).noneMatch(line -> line.contains("Left click"));
    assertThat(toggle.lore()).anyMatch(line -> line.contains("Left click: toggle"));
    for (PanelTile tile : view.tiles()) {
      if (tile.hint() != null && tile.hint().kind() == HintKind.NUMBER) {
        assertThat(tile.hint().typed()).isTrue();
        assertThat(tile.hint().step()).isNotNull();
      }
      if (tile.hint() != null && tile.hint().kind() == HintKind.CHOICE) {
        assertThat(tile.hint().options()).contains(tile.hint().value());
      }
    }
    assertThat(view.tiles()).anyMatch(tile -> tile.hint() != null && tile.hint().kind() == HintKind.NUMBER);
    assertThat(view.tiles()).anyMatch(tile -> tile.hint() != null && tile.hint().kind() == HintKind.TEXT);
    assertThat(view.tiles()).filteredOn(tile -> tile.role() == TileRole.DECORATION).isNotEmpty()
        .allMatch(tile -> tile.name().isBlank());
  }

  @Test
  void rootIndexIsAListOfNavigationRows() {
    PanelView view = PanelViews.view(1, 0, open(""));

    assertThat(view.layout()).isEqualTo(PanelLayout.LIST);
    assertThat(view.breadcrumb()).isEmpty();
    assertThat(view.paging()).isNull();
    List<PanelTile> rows = view.tiles().stream().filter(tile -> tile.role() == TileRole.NAVIGATION).toList();
    assertThat(rows).hasSize(3).allMatch(tile -> tile.shownDescription().size() == 1);
  }

  @Test
  void chestRenderingKeepsTheChatInstructionsAndClicks() {
    ChestMenu menu = open("core.$general");
    MenuButton toggle = button(menu, "core.metrics");

    assertThat(toggle.clickable()).isTrue();
    assertThat(toggle.icon().lore()).anyMatch(line -> line.contains("Left click: toggle"));
    assertThat(toggle.icon().name()).contains("[Boolean]");
    for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
      if (entry.getValue().hint() != null && entry.getValue().hint().kind() == art.arcane.adapt.platform.menu.MenuHint.Kind.NUMBER) {
        assertThat(entry.getValue().icon().lore()).anyMatch(line -> line.contains("Left click: edit in chat"));
      }
    }
  }

  @Test
  void typedValuesFromThePanelApplyWithoutAChatSession() {
    ChestMenu menu = open("core.$general");
    MenuButton number = numberButton(menu);

    try (MockedStatic<ConfigGui> gui = mockStatic(ConfigGui.class, Answers.CALLS_REAL_METHODS)) {
      gui.when(() -> ConfigGui.confirmAndApply(any(PlayerRef.class), anyString(), anyInt(), anyString(), any()))
          .then(invocation -> null);
      number.hint().input().input(new MenuClick(player, mock(MenuSession.class), 0, ClickKind.OTHER), "7");
      gui.verify(() -> ConfigGui.confirmAndApply(eq(player), eq("core.$general"), eq(0), anyString(), any()));
    }
    verify(player, never()).closeInventory();
  }

  @Test
  void invalidTypedValuesAreRefusedWithAMessage() {
    ChestMenu menu = open("core.$general");
    MenuButton number = numberButton(menu);

    try (MockedStatic<ConfigGui> gui = mockStatic(ConfigGui.class, Answers.CALLS_REAL_METHODS)) {
      gui.when(() -> ConfigGui.confirmAndApply(any(PlayerRef.class), anyString(), anyInt(), anyString(), any()))
          .then(invocation -> null);
      number.hint().input().input(new MenuClick(player, mock(MenuSession.class), 0, ClickKind.OTHER), "not a number");
      gui.verify(() -> ConfigGui.confirmAndApply(any(PlayerRef.class), anyString(), anyInt(), anyString(), any()), never());
    }
    verify(player, atLeastOnce()).sendMessage(anyString());
  }

  @Test
  void vanillaClicksStillStartTheChatPrompt() {
    ChestMenu menu = open("core.$general");
    MenuButton number = numberButton(menu);
    ConfigInput input = new ConfigInput();

    try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class)) {
      input.start();
      number.action().click(new MenuClick(player, mock(MenuSession.class), 0, ClickKind.LEFT));
    } finally {
      input.stop();
    }
    verify(player).closeInventory();
  }

  @Test
  void numberStepsFollowTheValueType() {
    assertThat(ConfigGui.numberStep(int.class, 5)).isEqualTo(1D);
    assertThat(ConfigGui.numberStep(double.class, 0.25D)).isEqualTo(0.01D);
    assertThat(ConfigGui.numberStep(double.class, 2.5D)).isEqualTo(0.1D);
    assertThat(ConfigGui.numberStep(Double.class, 40D)).isEqualTo(1D);
  }

  @Test
  void breadcrumbsNameEveryPageAbove() {
    assertThat(ConfigGui.breadcrumb("")).isEmpty();
    assertThat(ConfigGui.breadcrumb("adaptations.$skills.agility")).containsExactly("Configure Adapt", "Adaptations");
    assertThat(ConfigGui.breadcrumb("core.gui")).containsExactly("Configure Adapt", "Core");
  }

  @Test
  void closingAPageReopensItsParentWhenNothingReplacedIt() {
    assertThat(reopensAfterClose(false)).isEqualTo(1);
  }

  @Test
  void closingAPageLeavesAWindowOpenedOutsideTheMenuHostAlone() {
    assertThat(reopensAfterClose(true)).isZero();
  }

  private int reopensAfterClose(boolean otherWindowOpen) {
    List<MenuSession> sessions = new ArrayList<>();
    when(menus.open(any(PlayerRef.class), any(ChestMenu.class))).thenAnswer(invocation -> {
      MenuSession session = mock(MenuSession.class);
      ChestMenu opened = invocation.getArgument(1);
      lenient().when(session.menu()).thenReturn(opened);
      lenient().when(session.isOpen()).thenReturn(true);
      sessions.add(session);
      return session;
    });
    ChestMenu menu = open("core.$general");
    MenuSession shown = sessions.get(sessions.size() - 1);
    when(menus.current(player)).thenReturn(null);
    when(player.hasOpenContainer()).thenReturn(otherWindowOpen);
    int before = sessions.size();
    try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class)) {
      tasks.when(Tasks::isFoliaThreading).thenReturn(false);
      tasks.when(Tasks::isPrimaryThread).thenReturn(true);
      tasks.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), anyInt())).then(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      menu.onClose().accept(shown);
    }
    return sessions.size() - before;
  }

  private ChestMenu open(String path) {
    try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class)) {
      tasks.when(Tasks::isFoliaThreading).thenReturn(false);
      tasks.when(Tasks::isPrimaryThread).thenReturn(true);
      ConfigGui.open(player, path, 0);
    }
    ArgumentCaptor<ChestMenu> captor = ArgumentCaptor.forClass(ChestMenu.class);
    verify(menus, atLeastOnce()).open(eq(player), captor.capture());
    return captor.getValue();
  }

  private static PanelTile tile(PanelView view, String path) {
    return view.tiles().stream().filter(tile -> tile.lore().stream().anyMatch(line -> line.endsWith(path)))
        .findFirst().orElseThrow(() -> new AssertionError("no tile for " + path));
  }

  private static MenuButton button(ChestMenu menu, String path) {
    return menu.buttons().values().stream().filter(button -> button.icon().lore().stream().anyMatch(line -> line.endsWith(path)))
        .findFirst().orElseThrow(() -> new AssertionError("no button for " + path));
  }

  private static MenuButton numberButton(ChestMenu menu) {
    return menu.buttons().values().stream()
        .filter(button -> button.hint() != null && button.hint().kind() == art.arcane.adapt.platform.menu.MenuHint.Kind.NUMBER)
        .findFirst().orElseThrow(() -> new AssertionError("no number setting"));
  }
}
