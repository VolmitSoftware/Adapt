package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.command.CommandHelpPort;
import art.arcane.adapt.command.CommandMenu;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptLanguageSwitcherTest extends AdaptTestBase {
  private final List<CommandMenu> menus = new ArrayList<>();
  private final List<String> serverSelections = new CopyOnWriteArrayList<>();
  private final List<String> replies = new CopyOnWriteArrayList<>();
  private PluginLanguageService selections;
  private AdaptLanguageSwitcher switcher;
  private Path preferences;
  private UUID playerId;
  private CommandSource player;

  @BeforeEach
  void openSwitcher() {
    preferences = dataFolder.toPath().resolve("languages/language-preferences.properties");
    LocalizationSnapshot snapshot = mock(LocalizationSnapshot.class);
    selections = new PluginLanguageService(new PluginLanguageService.Options(preferences, () -> List.of("de_DE", "fr_FR"),
        () -> "en_US", () -> snapshot, (String locale) -> snapshot,
        (String locale, LocalizationSnapshot prepared) -> serverSelections.add(locale), Logger.getAnonymousLogger()));
    switcher = new AdaptLanguageSwitcher(() -> CompletableFuture.failedFuture(new IOException("offline")));
    switcher.open(selections);
    AdaptPorts.install(CommandHelpPort.class, new CommandHelpPort() {
      @Override
      public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments, Predicate<DirectorRuntimeNode> visible) {
        return false;
      }

      @Override
      public void menu(CommandSource source, CommandMenu menu) {
        menus.add(menu);
      }

      @Override
      public String version(String plugin, String version) {
        return plugin;
      }
    });
    playerId = UUID.randomUUID();
    PlayerRef ref = mock(PlayerRef.class);
    when(ref.uuid()).thenReturn(playerId);
    player = source(Optional.of(ref));
    when(player.hasPermission(anyString())).thenReturn(true);
    when(player.hasPermission(AdaptLanguageSwitcher.ADMIN_PERMISSION)).thenReturn(false);
    when(player.hasPermission(AdaptLanguageSwitcher.PLUGIN_ADMIN_PERMISSION)).thenReturn(false);
  }

  @AfterEach
  void closeSwitcher() {
    switcher.close();
    selections.close();
    AdaptPorts.uninstall(CommandHelpPort.class);
  }

  @Test
  void personalLanguageIsStoredInThePlayerPreferences() throws IOException {
    switcher.command(player, new String[]{"self", "de_DE"});

    await(() -> selections.playerLocale(playerId).isPresent() && replies.size() == 2);
    assertThat(selections.effectiveLocale(playerId)).isEqualTo("de_DE");
    assertThat(Files.readString(preferences, StandardCharsets.UTF_8)).contains(playerId + "=de_DE");
    assertThat(replies.getLast()).contains("de_DE");
  }

  @Test
  void resetReturnsThePlayerToTheServerDefault() throws IOException {
    switcher.command(player, new String[]{"self", "fr_FR"});
    await(() -> selections.playerLocale(playerId).isPresent());

    switcher.command(player, new String[]{"self", "reset"});

    await(() -> selections.playerLocale(playerId).isEmpty());
    assertThat(selections.effectiveLocale(playerId)).isEqualTo("en_US");
    assertThat(Files.readString(preferences, StandardCharsets.UTF_8)).doesNotContain(playerId.toString());
  }

  @Test
  void theConsoleHasNoPersonalLanguage() {
    CommandSource console = source(Optional.empty());
    when(console.hasPermission(anyString())).thenReturn(true);

    switcher.command(console, new String[]{"self", "de_DE"});

    assertThat(replies).hasSize(1);
    assertThat(serverSelections).isEmpty();
  }

  @Test
  void unknownLanguagesAreRejected() {
    switcher.command(player, new String[]{"self", "xx_YY"});

    assertThat(replies).hasSize(1).first().asString().contains("xx_YY");
    assertThat(selections.playerLocale(playerId)).isEmpty();
  }

  @Test
  void serverLanguageNeedsTheAdminPermission() {
    switcher.command(player, new String[]{"server", "de_DE"});

    assertThat(replies).hasSize(1);
    assertThat(serverSelections).isEmpty();
  }

  @Test
  void anAdminChangesTheServerDefault() {
    when(player.hasPermission(AdaptLanguageSwitcher.PLUGIN_ADMIN_PERMISSION)).thenReturn(true);

    switcher.command(player, new String[]{"server", "fr_FR"});

    await(() -> !serverSelections.isEmpty());
    assertThat(serverSelections).containsExactly("fr_FR");
  }

  @Test
  void homeAndLocalePagesGoThroughTheMenuPort() {
    switcher.command(player, new String[0]);
    switcher.command(player, new String[]{"self"});

    assertThat(menus).hasSize(2);
    assertThat(menus.get(0).title()).isEqualTo("/adapt language");
    assertThat(menus.get(0).entries()).hasSize(2);
    assertThat(menus.get(1).command()).isEqualTo("/adapt language self");
    assertThat(menus.get(1).entries()).anyMatch(line -> line.contains("/adapt language self de_DE"))
        .anyMatch(line -> line.contains("/adapt language self fr_FR"));
  }

  @Test
  void completionOffersScopesAndLocales() {
    assertThat(switcher.complete(player, new String[]{""})).containsExactly("self");
    assertThat(switcher.complete(player, new String[]{"self", "d"})).containsExactly("de_DE");
    assertThat(switcher.complete(player, new String[]{"self", "r"})).containsExactly("reset");
    when(player.hasPermission(AdaptLanguageSwitcher.ADMIN_PERMISSION)).thenReturn(true);
    assertThat(switcher.complete(player, new String[]{""})).containsExactly("self", "server", "update");
  }

  @Test
  void aClosedSwitcherOnlyReportsThatItIsStopping() {
    switcher.close();

    switcher.command(player, new String[]{"self", "de_DE"});

    assertThat(replies).hasSize(1);
    assertThat(switcher.complete(player, new String[]{""})).isEmpty();
  }

  private CommandSource source(Optional<PlayerRef> ref) {
    CommandSource source = mock(CommandSource.class);
    when(source.player()).thenReturn(ref);
    doAnswer(call -> replies.add(call.getArgument(0))).when(source).sendMarkup(anyString());
    return source;
  }

  private static void await(BooleanSupplier condition) {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("condition not met within 5s");
      }
      Thread.onSpinWait();
    }
  }
}
