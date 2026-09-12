package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.service.HotloadSVC;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextValue;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AdaptLanguageSelectionTest extends AdaptTestBase {
  private AdaptConfig previousConfig;
  private ConfigHotloadEngine engine;
  private HttpServer server;
  private URLClassLoader resources;
  private PluginLanguageService languages;
  private HotloadSVC hotload;
  private final AtomicReference<LocalizationSnapshot> selected = new AtomicReference<>();
  private final AtomicInteger downloadRequests = new AtomicInteger();
  private int hotloadBatches;

  @BeforeEach
  void prepareSelectionRuntime() throws Exception {
    previousConfig = (AdaptConfig) field(AdaptConfig.class, "config").get(null);
    field(AdaptConfig.class, "config").set(null, new AdaptConfig());
    hotload = new HotloadSVC();
    KMap<Class<? extends AdaptService>, AdaptService> services = new KMap<>();
    services.put(HotloadSVC.class, hotload);
    field(Adapt.class, "services").set(plugin, services);
    AdaptLanguage.initialize();
    AdaptConfig.selectLanguage("en_US");
    File configuration = new File(dataFolder, "adapt.toml");
    field(HotloadSVC.class, "adaptConfigFile").set(hotload, configuration);
    field(HotloadSVC.class, "localeLanguageFolder").set(hotload, AdaptLanguage.languageFolder());
    engine = (ConfigHotloadEngine) field(HotloadSVC.class, "hotloadEngine").get(hotload);
    engine.configure(100L, 100L, List.of(configuration), List.of(AdaptLanguage.languageFolder()));
    selected.set(LocalizationSnapshot.create(LocalizationCandidate.english(AdaptMessages.catalog(), PluralSelector.oneOther())));
    languages = new PluginLanguageService(new PluginLanguageService.Options(
        dataFolder.toPath().resolve("languages/language-preferences.properties"),
        AdaptLanguage::availableLocales, AdaptLanguage::activeLocale, selected::get,
        AdaptLanguage.editorOptions().loader()::load, (locale, snapshot) -> {
          AdaptLanguage.selectDefault(locale, snapshot);
          selected.set(snapshot);
        }, plugin.getLogger()));
  }

  @AfterEach
  void closeSelectionRuntime() throws Exception {
    if (languages != null) {
      languages.close();
    }
    AdaptLanguage.shutdown();
    if (server != null) {
      server.stop(0);
    }
    if (resources != null) {
      resources.close();
    }
    if (engine != null) {
      engine.clear();
    }
    field(AdaptConfig.class, "config").set(null, previousConfig);
  }

  @Test
  void oldPartialFinnishDownloadStaysSelectedWithoutSelfWriteAnnouncementsOrQueuedEnglishRollback() throws Exception {
    installSource(200, "[runtime]\nno_description_provided = \"Ei kuvausta\"\n"
        + "[command.runtime]\nmissing_permission = \"Vanha teksti ilman muuttujaa\"\nplayer_only = 42\n");
    File configuration = new File(dataFolder, "adapt.toml");
    ConfigHotloadEngine.StableContentSnapshot queuedEnglish = new ConfigHotloadEngine.StableContentSnapshot(
        configuration, "captured-before-selection", Files.readString(configuration.toPath()), 0L);

    languages.selectDefault("fi_FI").get(5L, TimeUnit.SECONDS);

    assertThat(AdaptLanguage.activeLocale()).isEqualTo("fi_FI");
    assertThat(AdaptConfig.get().getLanguage()).isEqualTo("fi_FI");
    assertThat(Files.readString(configuration.toPath())).contains("language = \"fi_FI\"");
    assertThat(selected.get().value(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo(new TextValue("Ei kuvausta"));
    assertThat(selected.get().value(CommandRuntimeMessages.MISSING_PERMISSION))
        .isEqualTo(CommandRuntimeMessages.MISSING_PERMISSION.englishValue());
    assertThat(selected.get().value(CommandRuntimeMessages.PLAYER_ONLY))
        .isEqualTo(CommandRuntimeMessages.PLAYER_ONLY.englishValue());
    AtomicInteger notices = new AtomicInteger();
    assertThat(engine.processSnapshotChange(queuedEnglish,
        snapshot -> AdaptConfig.reloadSnapshot(snapshot.normalizedContent(), snapshot.file()),
        delta -> notices.incrementAndGet())).isFalse();
    assertThat(engine.processFileChange(new File(AdaptLanguage.languageFolder(), "fi_FI.toml"),
        file -> true, delta -> notices.incrementAndGet())).isFalse();
    assertThat(engine.processFileChange(configuration, file -> true, delta -> notices.incrementAndGet())).isFalse();
    assertThat(notices).hasValue(0);
    assertThat(AdaptConfig.get().getLanguage()).isEqualTo("fi_FI");
  }

  @Test
  void syntacticallyUnreadableDownloadSelectsEnglishWithoutPublishingBrokenFile() throws Exception {
    installSource(200, "[runtime\nno_description_provided = \"Broken\"\n");

    languages.selectDefault("fi_FI").get(5L, TimeUnit.SECONDS);

    assertEnglishFallback();
  }

  @Test
  void unavailableDownloadSelectsEnglishWithoutPublishingResponseBody() throws Exception {
    installSource(503, "Unavailable");

    languages.selectDefault("fi_FI").get(5L, TimeUnit.SECONDS);

    assertEnglishFallback();
  }

  @Test
  void editorSaveIsAcknowledgedWithoutASecondHotloadNotification() throws Exception {
    AdaptLanguage.editorOptions().writer().write(new PluginLanguageEditor.Edit("en_US",
        RuntimeMessages.NO_DESCRIPTION_PROVIDED.id(), RuntimeMessages.NO_DESCRIPTION_PROVIDED.englishValue(),
        new TextValue("Customized English")));
    AtomicInteger notices = new AtomicInteger();

    assertThat(engine.processFileChange(new File(AdaptLanguage.languageFolder(), "en_US.toml"),
        file -> true, delta -> notices.incrementAndGet())).isFalse();
    assertThat(notices).hasValue(0);
    assertThat(AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo("Customized English");
  }

  @Test
  void personalFileHotloadRetainsMalformedTranslationsAndFallsBackOnDeletionUntilRestored() throws Exception {
    installSource(503, "Unavailable");
    AdaptServer adaptServer = mock(AdaptServer.class);
    when(plugin.getAdaptServer()).thenReturn(adaptServer);
    when(adaptServer.getSkillRegistry()).thenReturn(mock(SkillRegistry.class));
    LocalizationManager manager = (LocalizationManager) field(AdaptLanguage.class, "MANAGER").get(null);
    languages.close();
    languages = new PluginLanguageService(new PluginLanguageService.Options(
        dataFolder.toPath().resolve("languages/language-preferences.properties"),
        AdaptLanguage::availableLocales, AdaptLanguage::activeLocale, manager::snapshot,
        AdaptLanguage.editorOptions().loader()::load, (locale, snapshot) -> {
          throw new AssertionError("File hotloads cannot change the default language");
        }, plugin.getLogger()));
    field(AdaptLanguage.class, "selections").set(null, languages);
    Path personal = dataFolder.toPath().resolve("languages/fi_FI.toml");
    Files.writeString(personal, "[runtime]\nno_description_provided = 'INITIAL'\n");
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "fi_FI").get(5L, TimeUnit.SECONDS);
    Path preferences = dataFolder.toPath().resolve("languages/language-preferences.properties");
    String savedPreferences = Files.readString(preferences);
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      Files.writeString(personal, "[runtime]\nno_description_provided = 'LIVE'\n");
      pollUntil(() -> personalValue(player).equals(new TextValue("LIVE")));

      int previousBatches = hotloadBatches;
      Files.writeString(personal, "[runtime\n");
      pollUntil(() -> hotloadBatches > previousBatches);
      assertThat(personalValue(player)).isEqualTo(new TextValue("LIVE"));

      Path english = AdaptLanguage.languageFolder().toPath().resolve("en_US.toml");
      Files.writeString(english, "[runtime]\nno_description_provided = 'SERVER'\n"
          + "[command.runtime]\nplayer_only = 'SERVER ONLY'\n");
      pollUntil(() -> AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED).equals("SERVER"));
      assertThat(personalValue(player)).isEqualTo(new TextValue("LIVE"));
      assertThat(Files.readString(personal)).isEqualTo("[runtime\n");

      String partial = "[runtime]\nno_description_provided = 'PARTIAL'\n";
      Files.writeString(personal, partial);
      pollUntil(() -> personalValue(player).equals(new TextValue("PARTIAL")));
      assertThat(languages.snapshot(player).value(CommandRuntimeMessages.PLAYER_ONLY))
          .isEqualTo(CommandRuntimeMessages.PLAYER_ONLY.englishValue());

      Files.delete(personal);
      pollUntil(() -> personalValue(player).equals(RuntimeMessages.NO_DESCRIPTION_PROVIDED.englishValue()));
      assertThat(personal).doesNotExist();
      assertThat(languages.playerLocale(player)).contains("fi_FI");
      assertThat(Files.readString(preferences)).isEqualTo(savedPreferences);
      assertThat(AdaptLanguage.activeLocale()).isEqualTo("en_US");
      assertThat(AdaptConfig.get().getLanguage()).isEqualTo("en_US");
      assertThat(AdaptLanguage.reload()).isTrue();
      assertThat(personalValue(player)).isEqualTo(RuntimeMessages.NO_DESCRIPTION_PROVIDED.englishValue());
      assertThat(personal).doesNotExist();

      Files.writeString(personal, partial);
      pollUntil(() -> personalValue(player).equals(new TextValue("PARTIAL")));
      assertThat(Files.readString(preferences)).isEqualTo(savedPreferences);
      assertThat(downloadRequests).hasValue(0);
    }
  }

  private TextValue personalValue(UUID player) {
    return (TextValue) languages.snapshot(player).value(RuntimeMessages.NO_DESCRIPTION_PROVIDED);
  }

  private void pollUntil(BooleanSupplier condition) throws Exception {
    Method apply = HotloadSVC.class.getDeclaredMethod("applyConfigChanges", long.class, List.class);
    apply.setAccessible(true);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      List<ConfigHotloadEngine.StableContentSnapshot> snapshots = List.copyOf(engine.pollTouchedSnapshots());
      if (!snapshots.isEmpty()) {
        apply.invoke(hotload, 0L, snapshots);
        hotloadBatches++;
      }
      Thread.sleep(25L);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  private void assertEnglishFallback() throws Exception {
    assertThat(AdaptLanguage.activeLocale()).isEqualTo("en_US");
    assertThat(AdaptConfig.get().getLanguage()).isEqualTo("en_US");
    assertThat(Files.readString(dataFolder.toPath().resolve("adapt.toml"))).contains("language = \"en_US\"");
    assertThat(dataFolder.toPath().resolve("languages/fi_FI.toml")).doesNotExist();
    assertThat(selected.get().value(RuntimeMessages.NO_DESCRIPTION_PROVIDED))
        .isEqualTo(RuntimeMessages.NO_DESCRIPTION_PROVIDED.englishValue());
  }

  private void installSource(int status, String content) throws Exception {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      downloadRequests.incrementAndGet();
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    Path source = Files.createDirectories(dataFolder.toPath().resolve("source"));
    Files.writeString(source.resolve("source.properties"), "revision=master\nlocales=fi_FI\n");
    resources = new URLClassLoader(new URL[]{source.toUri().toURL()}, null);
    RemoteLanguageCatalog remote = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
        "Adapt", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
        "languages", ".toml", "source.properties", resources));
    field(AdaptLanguage.class, "remote").set(null, remote);
    field(AdaptLanguage.class, "remoteRoot").set(null, dataFolder.toPath().toAbsolutePath().normalize());
  }

  private Field field(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
