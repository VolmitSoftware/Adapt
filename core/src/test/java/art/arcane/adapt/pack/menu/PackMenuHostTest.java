package art.arcane.adapt.pack.menu;

import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.Goldens;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuMeta;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PackMenuHostTest {
  private static final MenuIcon STONE = MenuIcon.of(Id.minecraft("stone"), "Stone", List.of("first", "second"));
  private static final MenuIcon PANE = MenuIcon.of(Id.minecraft("black_stained_glass_pane"), " ", List.of());
  private static final MenuIcon ARROW = MenuIcon.of(Id.minecraft("arrow"), "Back", List.of("to the list"));

  private final FakePlayer player = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
  private final FakeMenuHost delegate = new FakeMenuHost();
  private final FakePackWindows windows = new FakePackWindows();
  private final Set<PlayerRef> book = new HashSet<>();
  private PackResources resources = PackSamples.resources();
  private final PackMenuHost host = new PackMenuHost(delegate, windows, book::contains, () -> resources);
  private final List<MenuClick> clicks = new ArrayList<>();

  private ChestMenu menu(int rows) {
    return ChestMenu.builder("§6Settings", rows)
        .breadcrumb(List.of("Adapt"))
        .set(0, new MenuButton(STONE, clicks::add))
        .set(1, MenuButton.display(PANE))
        .set(2, MenuButton.display(STONE).withMeta(MenuMeta.role(MenuRole.DECORATION)))
        .set(rows * 9 - 9, new MenuButton(ARROW, clicks::add).withHint(MenuHint.navigation("back")))
        .build();
  }

  @Test
  void viewersWithoutThePackGetTheDelegateUntouched() {
    ChestMenu menu = menu(3);

    MenuSession session = host.open(player, menu);

    assertThat(delegate.opened).containsExactly(menu);
    assertThat(delegate.opened.get(0)).isSameAs(menu);
    assertThat(windows.opened).isEmpty();
    assertThat(host.current(player)).isSameAs(session);
  }

  @Test
  void viewersGetTheDelegateWhileThePackIsNotReady() {
    book.add(player);
    resources = null;
    ChestMenu menu = menu(3);

    host.open(player, menu);

    assertThat(delegate.opened).containsExactly(menu);
    assertThat(windows.opened).isEmpty();
  }

  @Test
  void bookViewersGetTheLedgerSkin() {
    book.add(player);

    MenuSession session = host.open(player, menu(3));

    assertThat(delegate.opened).isEmpty();
    FakePackWindows.Window window = windows.window(player);
    assertThat(window.menu.size()).isEqualTo(27);
    assertThat(window.menu.title()).isEqualTo("§6Settings");
    assertThat(window.surface.titleText()).contains("Adapt / Settings");
    assertThat(window.surface.title()).anySatisfy((TitleRun run) -> assertThat(run.colour()).isEqualTo(0xFFAA00));
    assertThat(window.surface.look(PackSlot.top(1)).hideTooltip()).isTrue();
    assertThat(window.surface.look(PackSlot.top(2)).hideTooltip()).isTrue();
    SlotLook back = window.surface.look(PackSlot.top(18));
    assertThat(back.itemModel()).isEqualTo(Id.adapt("adapt_ui/art/ctl/back"));
    assertThat(window.menu.button(18).icon().lore()).containsExactly("to the list");
    assertThat(window.menu.button(18).icon().type()).isEqualTo(Id.minecraft("book"));
    assertThat(window.menu.button(0).icon()).isSameAs(STONE);
    assertThat(window.surface.look(PackSlot.top(0)).tooltipStyle()).isEqualTo("adapt:tome");
    assertThat(window.surface.lower()).isEmpty();
    assertThat(window.surface.topRowsOnly()).isTrue();
    assertThat(host.current(player)).isSameAs(session);
    Goldens.check("ledger-3", PackSamples.dump(window.menu, window.surface));
  }

  @Test
  void clicksReachTheOriginalHandlerWithTheCallersSession() {
    book.add(player);
    MenuSession session = host.open(player, menu(3));

    windows.window(player).click(0, ClickKind.SHIFT_RIGHT);
    windows.window(player).click(1, ClickKind.LEFT);
    windows.window(player).click(30, ClickKind.LEFT);

    assertThat(clicks).containsExactly(new MenuClick(player, session, 0, ClickKind.SHIFT_RIGHT));
  }

  @Test
  void updatesThroughTheCallersSessionAreSkinnedAgain() {
    book.add(player);
    MenuSession session = host.open(player, menu(3));

    session.update(ChestMenu.builder("Renamed", 3).set(4, MenuButton.display(PANE)).build());

    FakePackWindows.Window window = windows.window(player);
    assertThat(window.updates).isEqualTo(1);
    assertThat(window.surface.titleText()).contains("Renamed");
    assertThat(window.surface.look(PackSlot.top(4)).hideTooltip()).isTrue();
    assertThat(session.menu().title()).isEqualTo("Renamed");
  }

  @Test
  void freeInventoryMenusKeepThePlayerInventory() {
    book.add(player);

    host.open(player, menu(2).toBuilder().freePlayerInventory(true).build());

    FakePackWindows.Window window = windows.window(player);
    assertThat(window.surface.lower()).isEmpty();
    assertThat(window.surface.topRowsOnly()).isTrue();
    assertThat(window.menu.freePlayerInventory()).isTrue();
  }

  @Test
  void topRowsOnlyViewersGetNoLowerButtons() {
    book.add(player);
    windows.support = PackSupport.NONE;

    host.open(player, menu(6));

    assertThat(windows.window(player).surface.lower()).isEmpty();
    assertThat(windows.window(player).surface.topRowsOnly()).isTrue();
  }

  @Test
  void closingRunsTheMenusCallbackOnceWithTheCallersSession() {
    book.add(player);
    List<MenuSession> closed = new ArrayList<>();
    MenuSession session = host.open(player, menu(1).toBuilder().onClose(closed::add).build());

    windows.window(player).close();

    assertThat(closed).containsExactly(session);
    assertThat(host.current(player)).isSameAs(delegate.current(player));
    assertThat(host.window(player)).isNull();
  }

  @Test
  void storageWindowsAlwaysGoToTheDelegate() {
    book.add(player);

    host.openStorage(player, "Chest", 27, (art.arcane.adapt.platform.menu.StorageView view) -> { });

    assertThat(delegate.storage).containsExactly("Chest");
    assertThat(windows.opened).isEmpty();
  }

  @Test
  void closeAllClosesPackWindowsAndTheDelegate() {
    book.add(player);
    host.open(player, menu(3));

    host.closeAll();

    assertThat(windows.window(player)).isNull();
    assertThat(delegate.closedAll).isTrue();
  }
}
