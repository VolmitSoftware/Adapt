package art.arcane.adapt.bukkit.feature.menu;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuClickRoutingTest {
  private static final MenuIcon ICON = MenuIcon.of(Id.minecraft("stone"), "Stone", List.of());

  @Test
  void clickReachesTheButtonInThatSlotWithItsKind() {
    List<MenuClick> clicks = new ArrayList<>();
    ChestMenu menu = ChestMenu.builder("Test", 3)
        .set(1, 4, new MenuButton(ICON, clicks::add))
        .build();
    PlayerRef viewer = mock(PlayerRef.class);
    MenuSession session = session(viewer, menu);

    BukkitMenuHost.dispatch(session, 13, ClickKind.SHIFT_RIGHT);

    assertThat(clicks).singleElement().satisfies((MenuClick click) -> {
      assertThat(click.slot()).isEqualTo(13);
      assertThat(click.kind()).isEqualTo(ClickKind.SHIFT_RIGHT);
      assertThat(click.viewer()).isSameAs(viewer);
      assertThat(click.session()).isSameAs(session);
    });
  }

  @Test
  void displayOnlyAndEmptySlotsIgnoreClicks() {
    List<MenuClick> clicks = new ArrayList<>();
    ChestMenu menu = ChestMenu.builder("Test", 1)
        .set(0, MenuButton.display(ICON))
        .set(8, new MenuButton(ICON, clicks::add))
        .build();
    MenuSession session = session(mock(PlayerRef.class), menu);

    BukkitMenuHost.dispatch(session, 0, ClickKind.LEFT);
    BukkitMenuHost.dispatch(session, 4, ClickKind.LEFT);

    assertThat(clicks).isEmpty();
  }

  @Test
  void clicksFollowTheSessionsCurrentMenu() {
    List<String> fired = new ArrayList<>();
    ChestMenu first = ChestMenu.builder("Test", 1).set(2, new MenuButton(ICON, (MenuClick click) -> fired.add("first"))).build();
    ChestMenu second = ChestMenu.builder("Test", 1).set(2, new MenuButton(ICON, (MenuClick click) -> fired.add("second"))).build();
    MenuSession session = mock(MenuSession.class);
    when(session.menu()).thenReturn(first, second);

    BukkitMenuHost.dispatch(session, 2, ClickKind.LEFT);
    BukkitMenuHost.dispatch(session, 2, ClickKind.LEFT);

    assertThat(fired).containsExactly("first", "second");
  }

  @Test
  void windowSwallowedKindsAreReportedAndElementKindsAreNot() {
    assertThat(MenuClicks.extraKind(ClickType.NUMBER_KEY)).isEqualTo(ClickKind.NUMBER_KEY);
    assertThat(MenuClicks.extraKind(ClickType.DROP)).isEqualTo(ClickKind.DROP);
    assertThat(MenuClicks.extraKind(ClickType.CONTROL_DROP)).isEqualTo(ClickKind.CONTROL_DROP);
    assertThat(MenuClicks.extraKind(ClickType.DOUBLE_CLICK)).isEqualTo(ClickKind.DOUBLE_CLICK);
    assertThat(MenuClicks.extraKind(ClickType.SWAP_OFFHAND)).isEqualTo(ClickKind.SWAP_OFFHAND);
    assertThat(MenuClicks.extraKind(ClickType.LEFT)).isNull();
    assertThat(MenuClicks.extraKind(ClickType.RIGHT)).isNull();
    assertThat(MenuClicks.extraKind(ClickType.SHIFT_LEFT)).isNull();
    assertThat(MenuClicks.extraKind(ClickType.MIDDLE)).isNull();
  }

  @Test
  void slotsMapOntoCenteredWindowColumns() {
    assertThat(MenuWindow.position(0)).isEqualTo(-4);
    assertThat(MenuWindow.position(4)).isEqualTo(0);
    assertThat(MenuWindow.position(17)).isEqualTo(4);
    assertThat(MenuWindow.row(17)).isEqualTo(1);
    assertThat(MenuWindow.row(53)).isEqualTo(5);
  }

  private static MenuSession session(PlayerRef viewer, ChestMenu menu) {
    MenuSession session = mock(MenuSession.class);
    when(session.viewer()).thenReturn(viewer);
    when(session.menu()).thenReturn(menu);
    return session;
  }
}
