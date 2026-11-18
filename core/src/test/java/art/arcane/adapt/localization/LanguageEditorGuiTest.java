package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.command.CommandHelpPort;
import art.arcane.adapt.command.CommandMenu;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.testkit.ManualScheduler;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralValue;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageEditorGuiTest extends AdaptTestBase {
  private static final TextKey FIRST = TextKey.of("command.first", "First {player}");
  private static final TextKey SECOND = TextKey.of("command.second", "Second");
  private static final TextKey MENU = TextKey.of("gui.title", "Menu");
  private static final MessageCatalog CATALOG = MessageCatalog.of("en_US", FIRST, SECOND, MENU);

  private final ManualScheduler scheduler = new ManualScheduler();
  private final Menus host = new Menus();
  private final List<String> chat = new CopyOnWriteArrayList<>();
  private final List<CommandMenu> chatMenus = new CopyOnWriteArrayList<>();
  private final List<PluginLanguageEditor.Edit> edits = new CopyOnWriteArrayList<>();
  private final Map<String, MessageValue> stored = new ConcurrentHashMap<>();
  private PluginLanguageService selections;
  private AdaptLanguageSwitcher switcher;
  private PlayerRef player;
  private boolean admin = true;

  @BeforeEach
  void openEditor() {
    AdaptPlatform platform = new AdaptPlatform(mock(PlatformHost.class), scheduler, mock(FxHost.class),
        mock(ViewerHost.class), host, mock(PermissionHost.class), mock(ProtectionHost.class), mock(RecipeHost.class),
        mock(AdvancementHost.class));
    TestCores.setField(core, "platform", platform);
    for (MessageKey key : CATALOG.keys()) {
      stored.put(key.id(), key.englishValue());
    }
    LocalizationSnapshot snapshot = snapshot();
    selections = new PluginLanguageService(new PluginLanguageService.Options(
        dataFolder.toPath().resolve("languages/language-preferences.properties"), () -> List.of("de_DE"),
        () -> "en_US", () -> snapshot, (String locale) -> snapshot, (String locale, LocalizationSnapshot prepared) -> {
    }, Logger.getAnonymousLogger()));
    switcher = new AdaptLanguageSwitcher(() -> CompletableFuture.completedFuture(null),
        new PluginLanguageEditor.Options((String locale) -> snapshot(), (PluginLanguageEditor.Edit edit) -> {
          edits.add(edit);
          stored.put(edit.key(), edit.value());
          return snapshot();
        }));
    switcher.open(selections);
    AdaptPorts.install(CommandHelpPort.class, new CommandHelpPort() {
      @Override
      public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments) {
        return false;
      }

      @Override
      public void menu(CommandSource source, CommandMenu menu) {
        chatMenus.add(menu);
      }

      @Override
      public String version(String plugin, String version) {
        return plugin;
      }
    });
    player = mock(PlayerRef.class);
    UUID id = UUID.randomUUID();
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.isValid()).thenReturn(true);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.hasPermission(anyString())).thenAnswer(call -> admin);
    lenient().doAnswer(call -> {
      host.closeOpen();
      return null;
    }).when(player).closeInventory();
    lenient().doAnswer(call -> chat.add(call.getArgument(0))).when(player).sendMessage(anyString());
  }

  @AfterEach
  void closeEditor() {
    switcher.close();
    selections.close();
    AdaptPorts.uninstall(CommandHelpPort.class);
  }

  @Test
  void anAdminEditsAMessageThroughTheMenusAndChat() {
    switcher.command(source(), new String[]{"server", "edit"});

    assertThat(host.open.menu().title()).contains("Languages");
    click(0);
    settle(() -> host.open != null && host.open.menu().title().contains("de_DE"));
    assertThat(host.open.menu().button(0).icon().name()).contains("Command");
    assertThat(host.open.menu().button(1).icon().name()).contains("GUI");

    click(0);
    assertThat(host.open.menu().title()).contains("Command");
    assertThat(host.open.menu().button(0).icon().name()).contains("command.first");
    assertThat(host.open.menu().button(0).icon().lore()).anyMatch(line -> line.contains("{player}"));

    click(0);
    assertThat(host.open).isNull();
    assertThat(chatMenus).isNotEmpty();
    assertThat(String.join("\n", chatMenus.getLast().entries())).contains("command.first");

    PlayerChatEvent typed = new PlayerChatEvent(player, "Hello\\n{player}", false);
    new LanguageEditorGui.Input().onChat(typed);

    assertThat(typed.isCancelled()).isTrue();
    settle(() -> host.open != null);
    assertThat(edits).hasSize(1);
    assertThat(edits.getFirst().locale()).isEqualTo("de_DE");
    assertThat(edits.getFirst().key()).isEqualTo("command.first");
    assertThat(edits.getFirst().value()).isEqualTo(new TextValue("Hello\n{player}"));
    assertThat(host.open.menu().title()).contains("Command");
  }

  @Test
  void cancelReturnsToTheMessagesWithoutSaving() {
    switcher.command(source(), new String[]{"server", "edit", "de_DE"});
    settle(() -> host.open != null);
    click(0);
    click(0);

    new LanguageEditorGui.Input().onChat(new PlayerChatEvent(player, "cancel", false));

    assertThat(edits).isEmpty();
    assertThat(host.open.menu().title()).contains("Command");
  }

  @Test
  void searchFiltersKeysAndValues() {
    switcher.command(source(), new String[]{"server", "edit", "de_DE"});
    settle(() -> host.open != null);

    click(ChestMenu.slot(5, 4));
    new LanguageEditorGui.Input().onChat(new PlayerChatEvent(player, "menu", false));

    assertThat(host.open.menu().title()).contains("Search");
    assertThat(host.open.menu().button(0).icon().name()).contains("gui.title");
    assertThat(host.open.menu().button(1).icon().name()).doesNotContain("command.");
  }

  @Test
  void chatWithoutAPromptIsLeftAlone() {
    PlayerChatEvent typed = new PlayerChatEvent(player, "hello", false);

    new LanguageEditorGui.Input().onChat(typed);

    assertThat(typed.isCancelled()).isFalse();
  }

  @Test
  void theConsoleAndNonAdminsCannotOpenTheEditor() {
    List<String> replies = new ArrayList<>();
    CommandSource console = mock(CommandSource.class);
    when(console.player()).thenReturn(Optional.empty());
    when(console.hasPermission(anyString())).thenReturn(true);
    doAnswer(call -> replies.add(call.getArgument(0))).when(console).sendMarkup(anyString());

    switcher.command(console, new String[]{"server", "edit"});
    admin = false;
    switcher.command(source(), new String[]{"server", "edit"});

    assertThat(replies).hasSize(1).first().asString().contains("in game");
    assertThat(host.open).isNull();
  }

  @Test
  void adminsSeeTheEditorInTheMenuAndCompletion() {
    switcher.command(source(), new String[0]);

    assertThat(chatMenus.getLast().entries()).anyMatch(line -> line.contains("/adapt language server edit"));
    assertThat(switcher.complete(source(), new String[]{"server", "ed"})).containsExactly("edit");
    assertThat(switcher.complete(source(), new String[]{"server", "edit", "d"})).containsExactly("de_DE");
  }

  @Test
  void valuesSplitIntoLinesAndPluralForms() {
    LinesValue lines = new LinesValue(List.of("a", "b"));
    Map<String, String> forms = new LinkedHashMap<>();
    forms.put("other", "{count} items");
    forms.put("one", "one item");
    PluralValue plural = new PluralValue(forms);

    assertThat(LanguageEditorGui.parts(lines)).containsExactly("0", "1");
    assertThat(LanguageEditorGui.parts(plural)).containsExactly("one", "other");
    assertThat(LanguageEditorGui.replacement(lines, "1", "c")).isEqualTo(new LinesValue(List.of("a", "c")));
    assertThat(LanguageEditorGui.rawValue(LanguageEditorGui.replacement(plural, "one", "single"), "one")).isEqualTo("single");
    assertThat(LanguageEditorGui.rawValue(plural, "few")).isEqualTo("{count} items");
    assertThat(LanguageEditorGui.decodeInput("a\\nb\\\\n")).isEqualTo("a\nb\\n");
  }

  @Test
  void groupsAndPagesFollowTheBukkitEditor() {
    assertThat(LanguageEditorGui.group("command.first")).isEqualTo("command");
    assertThat(LanguageEditorGui.groupName("gui")).isEqualTo("GUI");
    assertThat(LanguageEditorGui.groupName("skill_names")).isEqualTo("Skill names");
    LanguageEditorGui.Page last = LanguageEditorGui.Page.of(80, 9, LanguageEditorGui.PAGE_SIZE);
    assertThat(last.page()).isEqualTo(3);
    assertThat(last.start()).isEqualTo(72);
    assertThat(last.end()).isEqualTo(80);
    assertThat(last.hasPrevious()).isTrue();
    assertThat(last.hasNext()).isFalse();
    assertThat(LanguageEditorGui.Page.of(0, 1, LanguageEditorGui.PAGE_SIZE).hasNext()).isFalse();
  }

  private LocalizationSnapshot snapshot() {
    LocalizationSnapshot snapshot = mock(LocalizationSnapshot.class);
    lenient().when(snapshot.catalog()).thenReturn(CATALOG);
    lenient().when(snapshot.value(any(MessageKey.class))).thenAnswer(call -> stored.get(((MessageKey) call.getArgument(0)).id()));
    return snapshot;
  }

  private CommandSource source() {
    CommandSource source = mock(CommandSource.class);
    lenient().when(source.player()).thenReturn(Optional.of(player));
    lenient().when(source.hasPermission(anyString())).thenAnswer(call -> admin);
    return source;
  }

  private void click(int slot) {
    Session session = host.open;
    MenuButton button = session.menu().button(slot);
    button.action().click(new MenuClick(player, session, slot, ClickKind.LEFT));
  }

  private void settle(BooleanSupplier condition) {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("condition not met within 5s");
      }
      scheduler.advance(1);
      Thread.onSpinWait();
    }
  }

  private final class Menus implements MenuHost {
    private volatile Session open;

    @Override
    public MenuSession open(PlayerRef viewer, ChestMenu menu) {
      closeOpen();
      open = new Session(viewer, menu);
      return open;
    }

    @Override
    public MenuSession current(PlayerRef viewer) {
      return open;
    }

    @Override
    public void closeAll() {
      closeOpen();
    }

    @Override
    public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
      return null;
    }

    @Override
    public StorageView currentStorage(PlayerRef viewer) {
      return null;
    }

    private void closeOpen() {
      Session previous = open;
      open = null;
      if (previous != null) {
        previous.close();
      }
    }
  }

  private final class Session implements MenuSession {
    private final PlayerRef viewer;
    private ChestMenu menu;
    private boolean live = true;

    private Session(PlayerRef viewer, ChestMenu menu) {
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
      return live;
    }

    @Override
    public void update(ChestMenu next) {
      menu = next;
    }

    @Override
    public void close() {
      if (!live) {
        return;
      }
      live = false;
      if (host.open == this) {
        host.open = null;
      }
      if (menu.onClose() != null) {
        menu.onClose().accept(this);
      }
    }
  }
}
