package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuAction;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAwareMenuHostTest {
  private static final MenuIcon STONE = MenuIcon.of(Id.minecraft("stone"), "Stone", List.of("a", "b"));

  private final FakePlayer player = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
  private final RecordingLink link = new RecordingLink();
  private final FakeHost delegate = new FakeHost();
  private final List<Runnable> queued = new ArrayList<>();
  private boolean queue;
  private final ClientAwareMenuHost host = new ClientAwareMenuHost(delegate, link,
      (PlayerRef viewer, Runnable task, Runnable retired) -> {
        if (queue) {
          queued.add(task);
        } else {
          task.run();
        }
      });

  @Test
  void viewersWithoutThePanelCapabilityGetTheDelegateChest() {
    ChestMenu menu = menu("Menu", (MenuClick click) -> { });

    MenuSession session = host.open(player, menu);

    assertThat(delegate.opened).containsExactly(menu);
    assertThat(link.frames).isEmpty();
    assertThat(host.current(player)).isSameAs(session).isSameAs(delegate.current(player));
  }

  @Test
  void capableViewersGetANativePanel() {
    link.capable.add(player.uuid());
    ChestMenu menu = menu("Menu", (MenuClick click) -> { });

    MenuSession session = host.open(player, menu);

    assertThat(delegate.opened).isEmpty();
    PanelView view = ((ServerFrame.PanelOpen) link.frames.get(0)).panel();
    assertThat(view.revision()).isZero();
    assertThat(view.title()).isEqualTo("Menu");
    assertThat(view.rows()).isEqualTo(1);
    assertThat(view.tiles()).containsExactly(new PanelTile(4, IconRef.item("minecraft:stone"), 1, "Stone",
        List.of("a", "b"), false, null, TileRole.ENTRY, "", "", null, List.of()));
    assertThat(view.layout()).isEqualTo(PanelLayout.LIST);
    assertThat(host.current(player)).isSameAs(session);
    assertThat(session.isOpen()).isTrue();
    assertThat(session.menu()).isSameAs(menu);
  }

  @Test
  void updatesBumpTheRevisionAndSkipIdenticalContent() {
    link.capable.add(player.uuid());
    MenuSession session = host.open(player, menu("Menu", (MenuClick click) -> { }));

    session.update(menu("Menu", (MenuClick click) -> { }));
    assertThat(link.frames).hasSize(1);

    session.update(ChestMenu.builder("Menu", 2).set(4, MenuButton.display(STONE.withAmount(3))).build());
    session.update(ChestMenu.builder("Renamed", 2).set(4, MenuButton.display(STONE.withAmount(3))).build());

    assertThat(link.frames).hasSize(3);
    PanelView second = ((ServerFrame.PanelUpdate) link.frames.get(1)).panel();
    PanelView third = ((ServerFrame.PanelUpdate) link.frames.get(2)).panel();
    assertThat(second.revision()).isEqualTo(1);
    assertThat(second.rows()).isEqualTo(2);
    assertThat(second.tiles().get(0).amount()).isEqualTo(3);
    assertThat(third.revision()).isEqualTo(2);
    assertThat(third.title()).isEqualTo("Renamed");
    assertThat(third.sessionId()).isEqualTo(second.sessionId());
  }

  @Test
  void reopeningTheSameTitleUpdatesInPlace() {
    link.capable.add(player.uuid());
    MenuSession first = host.open(player, menu("Menu", (MenuClick click) -> { }));

    MenuSession second = host.open(player, ChestMenu.builder("Menu", 1).set(0, MenuButton.display(STONE)).build());

    assertThat(second).isSameAs(first);
    assertThat(link.frames.get(1)).isInstanceOf(ServerFrame.PanelUpdate.class);
  }

  @Test
  void clicksRunTheButtonWithTheClickKind() {
    link.capable.add(player.uuid());
    List<MenuClick> clicks = new ArrayList<>();
    MenuSession session = host.open(player, menu("Menu", clicks::add));
    int id = openedId();

    host.panels().click(player.uuid(), new ClientFrame.PanelClick(id, 0, 4, ClickKind.SHIFT_RIGHT));
    host.panels().click(player.uuid(), new ClientFrame.PanelClick(id, 0, 5, ClickKind.LEFT));
    host.panels().click(player.uuid(), new ClientFrame.PanelClick(id + 1, 0, 4, ClickKind.LEFT));
    host.panels().click(UUID.randomUUID(), new ClientFrame.PanelClick(id, 0, 4, ClickKind.LEFT));

    assertThat(clicks).containsExactly(new MenuClick(player, session, 4, ClickKind.SHIFT_RIGHT));
  }

  @Test
  void displayOnlyTilesIgnoreClicks() {
    link.capable.add(player.uuid());
    host.open(player, ChestMenu.builder("Menu", 1).set(4, MenuButton.display(STONE)).build());

    host.panels().click(player.uuid(), new ClientFrame.PanelClick(openedId(), 0, 4, ClickKind.LEFT));

    assertThat(link.frames).hasSize(1);
  }

  @Test
  void staleRevisionsAreIgnoredAndAnsweredWithTheCurrentLayout() {
    link.capable.add(player.uuid());
    List<MenuClick> clicks = new ArrayList<>();
    MenuSession session = host.open(player, menu("Menu", clicks::add));
    session.update(ChestMenu.builder("Menu", 1).set(4, new MenuButton(STONE.withAmount(2), clicks::add)).build());

    host.panels().click(player.uuid(), new ClientFrame.PanelClick(openedId(), 0, 4, ClickKind.LEFT));

    assertThat(clicks).isEmpty();
    ServerFrame.PanelUpdate answer = (ServerFrame.PanelUpdate) link.frames.get(link.frames.size() - 1);
    assertThat(answer.panel().revision()).isEqualTo(1);

    host.panels().click(player.uuid(), new ClientFrame.PanelClick(openedId(), 1, 4, ClickKind.LEFT));
    assertThat(clicks).hasSize(1);
  }

  @Test
  void clicksRunOnTheViewersThread() {
    link.capable.add(player.uuid());
    List<MenuClick> clicks = new ArrayList<>();
    host.open(player, menu("Menu", clicks::add));
    queue = true;

    host.panels().click(player.uuid(), new ClientFrame.PanelClick(openedId(), 0, 4, ClickKind.LEFT));

    assertThat(clicks).isEmpty();
    queued.forEach(Runnable::run);
    assertThat(clicks).hasSize(1);
  }

  @Test
  void clicksQueuedBeforeACloseAreDropped() {
    link.capable.add(player.uuid());
    List<MenuClick> clicks = new ArrayList<>();
    MenuSession session = host.open(player, menu("Menu", clicks::add));
    queue = true;
    host.panels().click(player.uuid(), new ClientFrame.PanelClick(openedId(), 0, 4, ClickKind.LEFT));

    session.close();
    queued.forEach(Runnable::run);

    assertThat(clicks).isEmpty();
  }

  @Test
  void typedTextReachesTheHintInput() {
    link.capable.add(player.uuid());
    List<String> typed = new ArrayList<>();
    MenuHint field = MenuHint.text("old", (MenuClick context, String text) -> typed.add(context.slot() + ":" + text));
    host.open(player, ChestMenu.builder("Menu", 1).set(2, MenuButton.display(STONE).withHint(field))
        .set(3, MenuButton.display(STONE)).build());

    host.panels().input(player.uuid(), new ClientFrame.PanelInput(openedId(), 0, 2, "new"));
    host.panels().input(player.uuid(), new ClientFrame.PanelInput(openedId(), 0, 3, "ignored"));
    host.panels().input(player.uuid(), new ClientFrame.PanelInput(openedId(), 4, 2, "stale"));

    assertThat(typed).containsExactly("2:new");
  }

  @Test
  void closingInCodeSendsCloseAndRunsOnCloseOnce() {
    link.capable.add(player.uuid());
    List<MenuSession> closed = new ArrayList<>();
    MenuSession session = host.open(player, closing("Menu", closed::add));

    session.close();
    session.close();
    host.panels().closed(player.uuid(), new ClientFrame.PanelClosed(openedId()));

    assertThat(closed).containsExactly(session);
    assertThat(link.frames).contains(new ServerFrame.PanelClose(openedId()));
    assertThat(session.isOpen()).isFalse();
    assertThat(host.current(player)).isNull();
  }

  @Test
  void theClientClosingRunsOnCloseWithoutEchoingAClose() {
    link.capable.add(player.uuid());
    List<MenuSession> closed = new ArrayList<>();
    MenuSession session = host.open(player, closing("Menu", closed::add));

    host.panels().closed(player.uuid(), new ClientFrame.PanelClosed(openedId()));
    host.panels().closed(player.uuid(), new ClientFrame.PanelClosed(openedId()));
    session.close();

    assertThat(closed).containsExactly(session);
    assertThat(link.frames).hasSize(1);
    assertThat(host.current(player)).isNull();
  }

  @Test
  void onCloseSeesTheReplacementAsCurrent() {
    link.capable.add(player.uuid());
    List<MenuSession> seen = new ArrayList<>();
    MenuSession first = host.open(player, closing("First", (MenuSession closed) -> seen.add(host.current(player))));

    MenuSession second = host.open(player, menu("Second", (MenuClick click) -> { }));

    assertThat(second).isNotSameAs(first);
    assertThat(seen).containsExactly(second);
    assertThat(first.isOpen()).isFalse();
    assertThat(link.frames.get(1)).isInstanceOf(ServerFrame.PanelOpen.class);
    assertThat(((ServerFrame.PanelOpen) link.frames.get(1)).panel().sessionId()).isNotEqualTo(openedId());
  }

  @Test
  void quitRunsOnCloseOnce() {
    link.capable.add(player.uuid());
    List<MenuSession> closed = new ArrayList<>();
    host.open(player, closing("Menu", closed::add));

    host.panels().quit(player.uuid());
    host.panels().quit(player.uuid());

    assertThat(closed).hasSize(1);
    assertThat(host.current(player)).isNull();
  }

  @Test
  void aNativeOpenClosesAChestOpenedBeforeNegotiation() {
    List<MenuSession> closed = new ArrayList<>();
    MenuSession chest = host.open(player, closing("Chest", closed::add));
    link.capable.add(player.uuid());

    host.open(player, menu("Panel", (MenuClick click) -> { }));

    assertThat(closed).containsExactly(chest);
    assertThat(delegate.current(player)).isNull();
  }

  @Test
  void storageAlwaysUsesTheDelegateAndClosesAnOpenPanel() {
    link.capable.add(player.uuid());
    List<MenuSession> closed = new ArrayList<>();
    MenuSession panel = host.open(player, closing("Menu", closed::add));

    StorageView storage = host.openStorage(player, "Bag", 27, null);

    assertThat(storage).isSameAs(delegate.storage);
    assertThat(host.currentStorage(player)).isSameAs(delegate.storage);
    assertThat(closed).containsExactly(panel);
    assertThat(link.frames).contains(new ServerFrame.PanelClose(openedId()));
  }

  @Test
  void withdrawingContinuesTheSameSessionAsAChest() {
    link.capable.add(player.uuid());
    List<MenuSession> closed = new ArrayList<>();
    List<MenuClick> clicks = new ArrayList<>();
    ChestMenu menu = ChestMenu.builder("Menu", 1).set(4, new MenuButton(STONE, clicks::add))
        .onClose(closed::add).build();
    MenuSession session = host.open(player, menu);
    link.capable.remove(player.uuid());

    host.panels().withdrawn(player.uuid());

    assertThat(delegate.opened).hasSize(1);
    assertThat(delegate.opened.get(0).buttons()).isEqualTo(menu.buttons());
    assertThat(host.current(player)).isSameAs(session);
    assertThat(session.isOpen()).isTrue();
    assertThat(session.menu()).isSameAs(menu);

    ChestMenu next = ChestMenu.builder("Menu", 1).set(4, new MenuButton(STONE.withAmount(5), clicks::add))
        .onClose(closed::add).build();
    session.update(next);
    assertThat(delegate.session.menu().button(4).icon().amount()).isEqualTo(5);
    assertThat(session.menu()).isSameAs(next);

    delegate.session.close();
    assertThat(closed).containsExactly(session);
    assertThat(session.isOpen()).isFalse();
    assertThat(host.current(player)).isNull();
  }

  @Test
  void withdrawingWhileOfflineClosesInstead() {
    link.capable.add(player.uuid());
    List<MenuSession> closed = new ArrayList<>();
    host.open(player, closing("Menu", closed::add));
    player.setOnline(false);

    host.panels().withdrawn(player.uuid());

    assertThat(delegate.opened).isEmpty();
    assertThat(closed).hasSize(1);
  }

  @Test
  void aChestOpenedAfterWithdrawingReplacesTheContinuedSession() {
    link.capable.add(player.uuid());
    MenuSession panel = host.open(player, menu("Menu", (MenuClick click) -> { }));
    link.capable.remove(player.uuid());
    host.panels().withdrawn(player.uuid());

    MenuSession chest = host.open(player, menu("Other", (MenuClick click) -> { }));

    assertThat(host.current(player)).isSameAs(chest);
    assertThat(panel.isOpen()).isFalse();
  }

  @Test
  void aFailedSendFallsBackToTheChest() {
    link.capable.add(player.uuid());
    link.failing = true;
    ChestMenu menu = menu("Menu", (MenuClick click) -> { });

    MenuSession session = host.open(player, menu);

    assertThat(delegate.opened).containsExactly(menu);
    assertThat(host.current(player)).isSameAs(session).isSameAs(delegate.session);
  }

  @Test
  void closeAllClosesPanelsAndChests() {
    link.capable.add(player.uuid());
    List<MenuSession> closed = new ArrayList<>();
    host.open(player, closing("Menu", closed::add));

    host.closeAll();

    assertThat(closed).hasSize(1);
    assertThat(delegate.closedAll).isTrue();
  }

  @Test
  void hintsReachTheTiles() {
    link.capable.add(player.uuid());
    ChestMenu menu = ChestMenu.builder("Menu", 1)
        .set(0, new GuiElement("toggle").setMaterial(Id.minecraft("lime_dye")).setName("On")
            .setHint(MenuHint.toggle(true)).onLeftClick((MenuClick click) -> { }).button())
        .set(1, new GuiElement("back").setMaterial(Id.minecraft("arrow")).setName("Back")
            .setHint(MenuHint.navigation("back")).button())
        .set(2, MenuButton.display(STONE).withHint(MenuHint.destructive("Delete")))
        .build();

    host.open(player, menu);

    List<PanelTile> tiles = ((ServerFrame.PanelOpen) link.frames.get(0)).panel().tiles();
    assertThat(tiles.get(0).hint()).isEqualTo(TileHint.of(HintKind.TOGGLE, "ON"));
    assertThat(tiles.get(1).hint()).isEqualTo(TileHint.of(HintKind.NAVIGATION, "back"));
    assertThat(tiles.get(2).hint()).isEqualTo(TileHint.of(HintKind.DESTRUCTIVE, "Delete"));
  }

  @Test
  void iconsCarryModelGlintAndAmount() {
    MenuIcon icon = new MenuIcon(Id.minecraft("paper"), 7, "Paper", List.of(), true, Id.of("adapt", "gui/back"), 42, null);

    PanelView view = PanelViews.view(3, 0, ChestMenu.builder("T", 1).set(0, MenuButton.display(icon)).build());

    PanelTile tile = view.tiles().get(0);
    assertThat(tile.icon()).isEqualTo(new IconRef("minecraft:paper", 42, "adapt:gui/back", null));
    assertThat(tile.amount()).isEqualTo(7);
    assertThat(tile.glint()).isTrue();
  }

  @Test
  void oversizedTextIsCutToTheFrameLimits() throws Exception {
    String huge = "§a" + "x".repeat(FrameLimits.MAX_TEXT_BYTES);
    List<String> lore = new ArrayList<>(Collections.nCopies(FrameLimits.MAX_LINES * 2, "y".repeat(4000)));
    lore.add(0, huge);
    ChestMenu.Builder builder = ChestMenu.builder(huge, 6);
    for (int slot = 0; slot < 54; slot++) {
      builder.set(slot, MenuButton.display(MenuIcon.of(Id.minecraft("stone"), huge, lore))
          .withHint(MenuHint.navigation(huge)));
    }

    PanelView view = PanelViews.view(1, 0, builder.build());
    byte[] encoded = FrameCodec.encodeServer(new ServerFrame.PanelOpen(view));

    assertThat(encoded.length).isLessThanOrEqualTo(FrameLimits.MAX_S2C_BYTES);
    assertThat(view.title()).hasSizeLessThanOrEqualTo(FrameLimits.MAX_TEXT_BYTES).startsWith("§a");
    assertThat(view.tiles()).hasSize(54);
    assertThat(view.tiles().get(0).lore()).hasSizeLessThanOrEqualTo(FrameLimits.MAX_LINES);
    assertThat(FrameCodec.decodeServer(encoded)).isEqualTo(new ServerFrame.PanelOpen(view));
  }

  @Test
  void clipNeverSplitsACharacterOrAColourCode() {
    assertThat(PanelViews.clip("ab§c", 3)).isEqualTo("ab");
    assertThat(PanelViews.clip("aéé", 4)).isEqualTo("aé");
    assertThat(PanelViews.clip("short", 100)).isEqualTo("short");
    assertThat(PanelViews.clip(null, 10)).isEmpty();
  }

  private int openedId() {
    return ((ServerFrame.PanelOpen) link.frames.get(0)).panel().sessionId();
  }

  private static ChestMenu menu(String title, MenuAction action) {
    return ChestMenu.builder(title, 1).set(4, new MenuButton(STONE, action)).build();
  }

  private static ChestMenu closing(String title, Consumer<MenuSession> onClose) {
    return ChestMenu.builder(title, 1).set(4, MenuButton.display(STONE)).onClose(onClose).build();
  }

  private static final class RecordingLink implements PanelLink {
    private final Set<UUID> capable = new HashSet<>();
    private final List<ServerFrame> frames = new ArrayList<>();
    private boolean failing;

    @Override
    public boolean capable(UUID viewer) {
      return capable.contains(viewer);
    }

    @Override
    public boolean send(UUID viewer, ServerFrame frame) {
      if (failing) {
        return false;
      }
      frames.add(frame);
      return true;
    }
  }

  /** Chest host with the replace and close semantics of the platform hosts. */
  private static final class FakeHost implements MenuHost {
    private final List<ChestMenu> opened = new ArrayList<>();
    private final Map<UUID, Chest> open = new HashMap<>();
    private final StorageView storage = new StorageView() {
      @Override
      public PlayerRef viewer() {
        return null;
      }

      @Override
      public InventoryRef inventory() {
        return null;
      }

      @Override
      public int size() {
        return 27;
      }

      @Override
      public boolean isOpen() {
        return true;
      }

      @Override
      public void close() {
      }
    };
    private Chest session;
    private boolean closedAll;

    @Override
    public MenuSession open(PlayerRef viewer, ChestMenu menu) {
      opened.add(menu);
      Chest previous = open.get(viewer.uuid());
      Chest chest = new Chest(viewer, menu);
      open.put(viewer.uuid(), chest);
      session = chest;
      if (previous != null) {
        previous.finish();
      }
      return chest;
    }

    @Override
    public MenuSession current(PlayerRef viewer) {
      return open.get(viewer.uuid());
    }

    @Override
    public void closeAll() {
      closedAll = true;
      for (Chest chest : new ArrayList<>(open.values())) {
        chest.close();
      }
    }

    @Override
    public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
      return storage;
    }

    @Override
    public StorageView currentStorage(PlayerRef viewer) {
      return storage;
    }

    private final class Chest implements MenuSession {
      private final PlayerRef viewer;
      private ChestMenu menu;
      private boolean closed;

      private Chest(PlayerRef viewer, ChestMenu menu) {
        this.viewer = viewer;
        this.menu = menu;
      }

      @Override
      public PlayerRef viewer() {
        return viewer;
      }

      @Override
      public ChestMenu menu() {
        return menu;
      }

      @Override
      public boolean isOpen() {
        return !closed && open.get(viewer.uuid()) == this;
      }

      @Override
      public void update(ChestMenu next) {
        menu = next;
      }

      @Override
      public void close() {
        if (isOpen()) {
          open.remove(viewer.uuid());
          finish();
        }
      }

      private void finish() {
        if (closed) {
          return;
        }
        closed = true;
        if (menu.onClose() != null) {
          menu.onClose().accept(this);
        }
      }
    }
  }
}
