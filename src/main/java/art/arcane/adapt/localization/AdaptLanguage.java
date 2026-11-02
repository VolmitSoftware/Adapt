package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.adapt.service.HotloadSVC;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamStore;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.command.CommandSender;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.ResolvedLines;
import art.arcane.volmlib.util.localization.ResolvedText;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class AdaptLanguage {
  public static final String MANIFEST_RESOURCE = "adapt-language-source.properties";
  private static final Object SNAPSHOT_LOCK = new Object();
  private static final long MAX_LOCALE_BYTES = LanguageDirectory.MAXIMUM_BYTES;
  private static final int MAX_REPORTED_ISSUES = 12;
  private static final int MAXIMUM_BASE_DEPTH = 4;
  private static final int MAX_COLOR_CACHE_ENTRIES = 2048;
  private static final int MAX_COLOR_CACHE_TEMPLATE_LENGTH = 4096;
  private static final Map<String, Boolean> EXPLICIT_COLORS = new ConcurrentHashMap<>();
  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final TagResolver COLOR_TAGS = TagResolver.resolver(StandardTags.color(), StandardTags.reset(),
      StandardTags.gradient(), StandardTags.rainbow(), StandardTags.transition(), StandardTags.pride());
  static final Pattern LOCALE_NAME = LanguageDirectory.LOCALE_PATTERN;
  private static final MessageCatalog CATALOG = AdaptMessages.catalog();
  private static final LocalizationManager MANAGER = new LocalizationManager(
      LocalizationCandidate.english(CATALOG, PluralSelector.oneOther())
  );
  private static volatile String activeLocale = CATALOG.englishLocale();
  private static final Set<String> PENDING_INSTALLS = ConcurrentHashMap.newKeySet();
  private static volatile Upstream upstream;
  private static volatile Set<String> locales = Set.of();
  private static volatile Map<String, LanguageMeta> metas = Map.of();
  private static volatile PluginLanguageService selections;
  private static volatile BukkitLanguageSwitcher switcher;

  private AdaptLanguage() {
  }

  public static boolean initialize() {
    synchronized (SNAPSHOT_LOCK) {
      MANAGER.install(LocalizationSnapshot.create(LocalizationCandidate.english(CATALOG, PluralSelector.oneOther())));
      activeLocale = CATALOG.englishLocale();
    }
    return reload();
  }

  public static boolean reload() {
    return reloadInternal(null, null, true);
  }

  public static boolean reloadPassive() {
    return reloadInternal(null, null, false);
  }

  public static boolean reloadLanguageSnapshot(File file, String raw) {
    if (!isLanguageFile(file)) {
      return false;
    }
    try {
      String locale = normalizeLocale(file.getName().substring(0, file.getName().length() - ".toml".length()));
      String configuredLocale = normalizeLocale(AdaptConfig.get().getLanguage());
      refreshAvailableLocales();
      if (configuredLocale.equals(locale)) {
        return reloadInternal(file, raw, false);
      }
      LocalizationSnapshot prepared = LocalizationSnapshot.create(loadCandidate(locale, file, raw));
      PluginLanguageService current = selections;
      if (current != null) {
        current.cache(locale, prepared);
      }
      return true;
    } catch (Exception failure) {
      Adapt.error("Could not reload Adapt language " + file + "; keeping the last valid messages.", failure);
      return false;
    }
  }

  private static boolean reloadInternal(File snapshotFile, String snapshotRaw, boolean writeGeneratedFiles) {
    if (writeGeneratedFiles) {
      try {
        Files.createDirectories(languageFolder().toPath());
      } catch (IOException failure) {
        Adapt.warn("Failed to create Adapt language folders: " + failure.getMessage());
        Adapt.error(failure);
      }
      try {
        ensureEnglish();
      } catch (Exception failure) {
        Adapt.warn("Failed to refresh the Adapt English language file: " + failure.getMessage());
        Adapt.error(failure);
      }
      refreshAvailableLocales();
    }
    String configuredLocale = AdaptConfig.get().getLanguage();
    String requestedLocale = configuredLocale == null || configuredLocale.isBlank()
        ? CATALOG.englishLocale()
        : configuredLocale.trim();
    LocalizationReloadResult result;
    synchronized (SNAPSHOT_LOCK) {
      result = MANAGER.reload(() -> loadCandidate(normalizeLocale(configuredLocale), snapshotFile, snapshotRaw));
      if (result.applied()) {
        activeLocale = normalizeLocale(configuredLocale);
      }
    }
    if (!result.applied()) {
      reportRejectedReload(requestedLocale, result);
      return false;
    }

    PluginLanguageService current = selections;
    if (current != null) {
      current.cache(activeLocale, MANAGER.snapshot());
    }
    int warningCount = result.validation().warnings().size();
    Adapt.info("Loaded locale " + requestedLocale + " with " + warningCount + " fallback "
        + (warningCount == 1 ? "entry" : "entries") + ".");
    return true;
  }

  public static String activeLocale() {
    return activeLocale;
  }

  public static File languageFolder() {
    return new File(Adapt.instance.getDataFolder(), "languages");
  }

  public static boolean isLanguageFile(File file) {
    if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".toml")) {
      return false;
    }
    File parent = file.getParentFile();
    String locale = file.getName().substring(0, file.getName().length() - ".toml".length());
    return parent != null && parent.getAbsoluteFile().equals(languageFolder().getAbsoluteFile())
        && LOCALE_NAME.matcher(locale).matches();
  }

  public static String text(MessageKey key) {
    return text(key, MessageArgs.empty());
  }

  public static String text(MessageKey key, MessageArgument... arguments) {
    MessageArgs.Builder builder = MessageArgs.builder();
    for (MessageArgument argument : arguments) {
      builder.add(argument);
    }
    return text(key, builder.build());
  }

  public static String text(MessageKey key, MessageArgs arguments) {
    return textStyled("", key, arguments);
  }

  public static String textStyled(String defaultStyle, MessageKey key, MessageArgument... arguments) {
    MessageArgs.Builder builder = MessageArgs.builder();
    for (MessageArgument argument : arguments) {
      builder.add(argument);
    }
    return textStyled(defaultStyle, key, builder.build());
  }

  public static String textStyled(String defaultStyle, MessageKey key, MessageArgs arguments) {
    if (key instanceof TextKey textKey) {
      ResolvedText resolved = snapshot().resolve(textKey, arguments);
      return renderTemplate(resolved.template(), resolved.arguments(), AdaptConfig.get().isAutomaticGradients(), defaultStyle);
    }
    if (key instanceof LinesKey linesKey) {
      ResolvedLines resolved = snapshot().resolve(linesKey, arguments);
      return renderTemplate(String.join("\n", resolved.lines()), resolved.arguments(), AdaptConfig.get().isAutomaticGradients(), defaultStyle);
    }
    if (key instanceof PluralKey pluralKey) {
      ResolvedText resolved = snapshot().resolve(pluralKey, arguments);
      return renderTemplate(resolved.template(), resolved.arguments(), AdaptConfig.get().isAutomaticGradients(), defaultStyle);
    }
    throw new IllegalArgumentException("Unsupported message key: " + key.id());
  }

  public static DirectorTextResolver directorResolver() {
    return (key, arguments) -> {
      MessageKey definition = CATALOG.key(key.id());
      if (!(definition instanceof TextKey textKey)) {
        return C.stripColor(DirectorTextResolver.ENGLISH.resolve(key, arguments));
      }
      return C.stripColor(text(textKey, arguments));
    };
  }

  private static LocalizationCandidate loadCandidate(String locale, File snapshotFile, String snapshotRaw) throws Exception {
    List<LocaleOverlay> overlays = new ArrayList<>();
    File file = new File(languageFolder(), locale + ".toml");
    String raw = null;
    if (snapshotFile != null && file.getAbsoluteFile().equals(snapshotFile.getAbsoluteFile())) {
      if (snapshotRaw != null) {
        raw = snapshotRaw;
      } else if (!Files.notExists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Language file could not be read: " + file);
      }
    } else if (file.exists()) {
      raw = readLocaleFile(file);
    }
    if (raw != null) {
      overlays.add(parseOverlay(file.getPath(), locale, raw));
      overlays.addAll(baseOverlays(locale, raw));
    }
    return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
  }

  private static String readLocaleFile(File file) throws IOException {
    if (!file.isFile()) {
      throw new IllegalArgumentException("Locale file is not a regular file: " + file.getPath());
    }
    if (file.length() > MAX_LOCALE_BYTES) {
      throw new IllegalArgumentException("Locale file is too large: " + file.getPath());
    }
    return Files.readString(file.toPath());
  }

  private static List<LocaleOverlay> baseOverlays(String locale, String raw) throws IOException {
    List<LocaleOverlay> overlays = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    visited.add(locale.replace('-', '_').toLowerCase(Locale.ROOT));
    LanguageMeta meta = metaFromContent(locale, raw);
    int depth = 0;
    while (meta.base().isPresent() && depth < MAXIMUM_BASE_DEPTH) {
      String base = canonicalLocale(meta.base().get());
      if (!visited.add(base.replace('-', '_').toLowerCase(Locale.ROOT))
          || LanguageDirectory.sameLocale(base, CATALOG.englishLocale())) {
        break;
      }
      File baseFile = new File(languageFolder(), base + ".toml");
      if (!baseFile.isFile()) {
        break;
      }
      String baseRaw = readLocaleFile(baseFile);
      overlays.add(parseOverlay(baseFile.getPath(), base, baseRaw));
      meta = metaFromContent(base, baseRaw);
      depth++;
    }
    return overlays;
  }

  private static LanguageMeta metaFromContent(String locale, String raw) {
    try {
      return parseMeta(raw).withoutSelfBase(locale);
    } catch (IOException invalid) {
      return LanguageMeta.empty();
    }
  }

  private static LanguageMeta parseMeta(String raw) throws IOException {
    StringBuilder table = new StringBuilder();
    boolean inMeta = false;
    for (String line : raw.split("\\R", -1)) {
      String trimmed = line.strip();
      if (trimmed.startsWith("[")) {
        if (inMeta) {
          break;
        }
        inMeta = trimmed.equals("[meta]");
      }
      if (inMeta) {
        table.append(line).append('\n');
      }
    }
    if (table.isEmpty()) {
      return LanguageMeta.empty();
    }
    return LanguageMeta.parse(table.toString());
  }

  static Upstream upstream() {
    Path root = languageFolder().toPath().toAbsolutePath().normalize();
    Upstream current = upstream;
    if (current != null && current.directory().root().equals(root)) {
      return current;
    }
    synchronized (AdaptLanguage.class) {
      if (upstream != null && upstream.directory().root().equals(root)) {
        return upstream;
      }
      return connectUpstream(GitHubLanguageSource.API_ROOT, GitHubLanguageSource.RAW_ROOT,
          LanguageUpstreamSync.MINIMUM_CHECK_INTERVAL);
    }
  }

  static synchronized Upstream connectUpstream(URI apiRoot, URI rawRoot, Duration minimumCheckInterval) {
    Upstream previous = upstream;
    if (previous != null) {
      previous.close();
    }
    Upstream connected = openUpstream(new LanguageDirectory(languageFolder()), apiRoot, rawRoot, minimumCheckInterval);
    upstream = connected;
    locales = Set.of();
    metas = Map.of();
    return connected;
  }

  private static Upstream openUpstream(LanguageDirectory directory, URI apiRoot, URI rawRoot,
                                       Duration minimumCheckInterval) {
    try {
      LanguageSourceManifest manifest = LanguageSourceManifest.load(AdaptLanguage.class.getClassLoader(), MANIFEST_RESOURCE);
      GitHubLanguageSource source = new GitHubLanguageSource(manifest, "Adapt", apiRoot, rawRoot);
      LanguageUpstreamStore store = new LanguageUpstreamStore(directory, manifest);
      LanguageUpstreamSync sync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
          "Adapt",
          CATALOG,
          directory,
          manifest,
          source,
          store,
          () -> AdaptLanguageReference.render(CATALOG),
          AdaptLanguage::validateContent,
          AdaptLanguage::commitUpstream,
          AdaptLanguage::noteUpstreamWrite,
          AdaptLanguage::applyUpstream,
          Adapt.instance.getLogger()
      ), minimumCheckInterval);
      return new Upstream(directory, manifest, sync, null);
    } catch (RuntimeException failure) {
      return new Upstream(directory, null, null, failure);
    }
  }

  public static synchronized void start() {
    if (selections != null) {
      return;
    }
    selections = new PluginLanguageService(new PluginLanguageService.Options(
        Adapt.instance.getDataFolder().toPath().resolve("languages/language-preferences.properties"),
        AdaptLanguage::availableLocales,
        AdaptLanguage::activeLocale,
        MANAGER::snapshot,
        AdaptLanguage::prepareLocale,
        AdaptLanguage::selectDefault,
        Adapt.instance.getLogger()
    ));
    switcher = BukkitLanguageSwitcher.register(Adapt.instance, selections,
        new BukkitLanguageSwitcher.Options("adapt", "adapt.configurator",
            DirectorMiniMenu.Theme.adaptRed(), directorResolver(), editorOptions(), null,
            updateAction(), displayNames()), AdaptLanguageEditor.presentation());
  }

  public static synchronized void shutdown() {
    BukkitLanguageSwitcher currentSwitcher = switcher;
    switcher = null;
    if (currentSwitcher != null) {
      currentSwitcher.close();
    }
    PluginLanguageService currentSelections = selections;
    selections = null;
    if (currentSelections != null) {
      currentSelections.close();
    }
    Upstream currentUpstream = upstream;
    upstream = null;
    locales = Set.of();
    metas = Map.of();
    PENDING_INSTALLS.clear();
    if (currentUpstream != null) {
      currentUpstream.close();
    }
  }

  public static BukkitLanguageSwitcher.LanguageUpdateAction updateAction() {
    return AdaptLanguage::checkUpstream;
  }

  public static BukkitLanguageSwitcher.LocaleDisplayNames displayNames() {
    return locale -> metas.getOrDefault(locale, LanguageMeta.empty()).name().orElse(null);
  }

  public static CompletableFuture<LanguageUpstreamSync.SyncReport> startUpstreamCheck() {
    return checkUpstream();
  }

  public static Optional<Throwable> sourceFailure() {
    return Optional.ofNullable(upstream().failure());
  }

  public static String sourceDescription() {
    LanguageSourceManifest manifest = upstream().manifest();
    return manifest == null ? "unavailable" : manifest.repository() + "@" + manifest.branch();
  }

  public static Optional<LanguageUpstreamSync.Status> upstreamStatus() {
    LanguageUpstreamSync sync = upstream().sync();
    return sync == null ? Optional.empty() : Optional.of(sync.status());
  }

  public static String sourceState() {
    Upstream current = upstream();
    if (current.failure() != null) {
      return "unavailable (" + current.failure().getClass().getSimpleName() + ")";
    }
    LanguageUpstreamSync.Status status = current.sync().status();
    StringBuilder state = new StringBuilder(sourceDescription()).append(", ");
    state.append(status.revision().map(sha -> "revision " + sha.substring(0, 7)).orElse("not checked"));
    if (status.checkedMillis() > 0L) {
      state.append(", checked ").append(Instant.ofEpochMilli(status.checkedMillis()));
    }
    status.lastError().ifPresent(error -> state.append(", last error: ").append(error));
    return state.toString();
  }

  public static boolean isRepositoryLocale(String locale) {
    return upstream().contains(locale);
  }

  public static String localeDisplayName(String locale) {
    String normalized = locale == null ? "" : locale.trim();
    return metas.getOrDefault(normalized, LanguageMeta.empty()).name()
        .or(() -> VolmitLocales.displayName(normalized))
        .orElse(normalized);
  }

  public static void language(CommandSender sender, String[] arguments) {
    BukkitLanguageSwitcher current = switcher;
    if (current != null) {
      current.command(sender, arguments);
    }
  }

  public static List<String> completeLanguage(CommandSender sender, String[] arguments) {
    BukkitLanguageSwitcher current = switcher;
    return current == null ? List.of() : current.complete(sender, arguments);
  }

  public static Set<String> availableLocales() {
    Set<String> current = locales;
    if (current.isEmpty()) {
      refreshAvailableLocales();
      current = locales;
    }
    return current;
  }

  public static String text(UUID player, MessageKey key, MessageArgs arguments) {
    return LanguageAudience.call(player, () -> text(key, arguments));
  }

  public static void requestConfiguredLocale() {
    String locale = canonicalLocale(normalizeLocale(AdaptConfig.get().getLanguage()));
    Upstream current = upstream();
    if (CATALOG.englishLocale().equals(locale) || current.sync() == null || !current.contains(locale)
        || Files.exists(new File(languageFolder(), locale + ".toml").toPath(), LinkOption.NOFOLLOW_LINKS)
        || !PENDING_INSTALLS.add(locale)) {
      return;
    }
    Adapt.info("Installing Adapt language " + locale + " from " + sourceDescription() + "...");
    current.sync().installAsync(locale).whenComplete((content, failure) -> completeConfiguredInstall(locale, failure));
  }

  private static LocalizationSnapshot snapshot() {
    PluginLanguageService current = selections;
    return current == null ? MANAGER.snapshot() : current.snapshot();
  }

  private static LocalizationSnapshot prepareLocale(String locale) throws Exception {
    String requiredLocale = canonicalLocale(locale);
    File file = new File(languageFolder(), requiredLocale + ".toml");
    if (!CATALOG.englishLocale().equals(requiredLocale) && !file.exists()) {
      installLocale(requiredLocale, file);
      refreshAvailableLocales();
    }
    return LocalizationSnapshot.create(loadCandidate(requiredLocale, null, null));
  }

  private static void installLocale(String locale, File file) throws Exception {
    Upstream current = upstream();
    if (!current.contains(locale)) {
      String content = AdaptLanguageReference.renderCustom(CATALOG, locale);
      write(file, () -> {
        AtomicFileIO.writeString(file.toPath(), content);
        return new ConfigHotloadEngine.Written<>(content, null);
      });
      return;
    }
    if (current.sync() == null) {
      throw new IOException("Language " + locale + " is not installed and the language source is " + sourceDescription());
    }
    Adapt.info("Installing Adapt language " + locale + " from " + sourceDescription() + "...");
    current.sync().install(locale);
    Adapt.info("Installed Adapt language " + locale + " to " + file.toPath().toAbsolutePath().normalize() + ".");
  }

  private static void completeConfiguredInstall(String locale, Throwable failure) {
    PENDING_INSTALLS.remove(locale);
    if (failure != null) {
      Adapt.error("Unable to install Adapt language " + locale + "; the previous language remains active.", failure);
      return;
    }
    Adapt.info("Installed Adapt language " + locale + " to "
        + new File(languageFolder(), locale + ".toml").toPath().toAbsolutePath().normalize() + ".");
    if (!LanguageDirectory.sameLocale(locale, normalizeLocale(AdaptConfig.get().getLanguage()))) {
      return;
    }
    if (!SchedulerUtils.runGlobal(Adapt.instance, AdaptLanguage::activateConfiguredLocale)) {
      Adapt.warn("Installed Adapt language " + locale + " activates on the next reload or restart.");
    }
  }

  private static void activateConfiguredLocale() {
    if (reloadPassive()) {
      refreshConsumers();
    }
  }

  public static PluginLanguageEditor.Options editorOptions() {
    return new PluginLanguageEditor.Options(AdaptLanguage::prepareLocale, AdaptLanguage::writeMessage);
  }

  private static LocalizationSnapshot writeMessage(PluginLanguageEditor.Edit edit) throws IOException {
    LocaleOverlay edited = LocaleOverlay.builder("editor", edit.locale()).put(edit.key(), edit.value()).build();
    LocalizationValidator.validate(CATALOG, List.of(edited)).throwIfInvalid();
    File file = new File(languageFolder(), edit.locale() + ".toml");
    try {
      LocalizationSnapshot prepared = write(file, () -> {
        ConfigHotloadEngine.Written<LocalizationSnapshot> written = LanguageFileEditor.update(file.toPath(), raw -> {
          LocalizationSnapshot current = editorSnapshot(edit.locale(), file, raw);
          if (!current.value(CATALOG.require(edit.key())).equals(edit.expected())) {
            throw new IOException("Language message changed; reopen it before saving");
          }
          String updated = TomlLanguageEditor.upsert(raw, edit.key(), edit.value()).content();
          return new LanguageFileEditor.Prepared<>(updated,
              new ConfigHotloadEngine.Written<>(updated, editorSnapshot(edit.locale(), file, updated)));
        });
        synchronized (SNAPSHOT_LOCK) {
          if (edit.locale().equals(activeLocale)) {
            MANAGER.install(written.value());
          }
        }
        return written;
      });
      SchedulerUtils.runGlobal(Adapt.instance, AdaptLanguage::refreshConsumers);
      return prepared;
    } catch (IOException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IOException("Could not save Adapt language " + edit.locale(), failure);
    }
  }

  private static LocalizationSnapshot editorSnapshot(String locale, File file, String raw) throws IOException {
    try {
      return LocalizationSnapshot.create(loadCandidate(locale, file, raw));
    } catch (Exception failure) {
      throw new IOException("Could not validate Adapt language " + locale, failure);
    }
  }

  static void selectDefault(String locale, LocalizationSnapshot prepared) throws Exception {
    write(Adapt.instance.getDataFile("adapt.toml"), () -> {
      String raw = AdaptConfig.selectLanguage(locale);
      synchronized (SNAPSHOT_LOCK) {
        MANAGER.install(prepared);
        activeLocale = locale;
      }
      return new ConfigHotloadEngine.Written<>(raw, null);
    });
    SchedulerUtils.runGlobal(Adapt.instance, AdaptLanguage::refreshConsumers);
  }

  private static <T> T write(File file, ConfigHotloadEngine.FileWrite<T> writer) throws Exception {
    HotloadSVC hotload = Adapt.service(HotloadSVC.class);
    return hotload == null ? writer.write().value() : hotload.write(file, writer);
  }

  private static void refreshConsumers() {
    HotloadSVC hotload = Adapt.service(HotloadSVC.class);
    if (hotload != null) {
      hotload.refreshLocalizationConsumers();
    }
  }

  private static CompletableFuture<LanguageUpstreamSync.SyncReport> checkUpstream() {
    LanguageUpstreamSync sync = upstream().sync();
    if (sync == null) {
      return CompletableFuture.completedFuture(LanguageUpstreamSync.SyncReport.failed(
          sourceDescription(), System.currentTimeMillis()));
    }
    return sync.checkAsync().thenApply(AdaptLanguage::logUpstreamReport);
  }

  private static LanguageUpstreamSync.SyncReport logUpstreamReport(LanguageUpstreamSync.SyncReport report) {
    String revision = report.revision().map(sha -> sha.substring(0, 7)).orElse("unknown");
    for (String locale : report.changedLocales()) {
      LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
      String source = locale.equals(CATALOG.englishLocale())
          ? "the built-in catalog"
          : sourceDescription() + " (" + revision + ")";
      Adapt.info("Updated Adapt language " + locale + " from " + source + ": " + result.summary() + ".");
    }
    if (report.coolingDown()) {
      Adapt.info("Adapt language source " + sourceDescription() + " was checked recently; the next check is allowed in "
          + report.cooldownRemaining() + ".");
    } else if (report.failure().isEmpty() && report.changedLocales().isEmpty() && report.failures().isEmpty()
        && report.revision().isPresent()) {
      Adapt.info("Adapt languages match " + sourceDescription() + " (" + revision + ").");
    }
    return report;
  }

  private static void ensureEnglish() throws Exception {
    LanguageUpstreamSync sync = upstream().sync();
    if (sync == null) {
      AdaptLanguageReference.write();
      return;
    }
    sync.mergeEnglish().filter(LanguageCatalogMerge.MergeResult::changed).ifPresent(result -> Adapt.info(
        "Updated Adapt language " + CATALOG.englishLocale() + " from the built-in catalog: " + result.summary() + "."));
  }

  private static <T> T commitUpstream(LanguageUpstreamSync.CommitAction<T> action) throws IOException {
    PluginLanguageService current = selections;
    if (current == null) {
      return action.apply();
    }
    return current.commitUpdate(action::apply);
  }

  private static void noteUpstreamWrite(Path file, String content) {
    HotloadSVC hotload = Adapt.service(HotloadSVC.class);
    if (hotload != null) {
      hotload.noteSelfWrite(file.toFile(), content);
    }
  }

  private static void applyUpstream(String locale, String content, LanguageCatalogMerge.MergeResult result)
      throws Exception {
    File file = new File(languageFolder(), locale + ".toml");
    LocalizationSnapshot prepared = LocalizationSnapshot.create(loadCandidate(locale, file, content));
    refreshAvailableLocales();
    PluginLanguageService current = selections;
    if (current != null) {
      current.cache(locale, prepared);
    }
    synchronized (SNAPSHOT_LOCK) {
      if (LanguageDirectory.sameLocale(locale, activeLocale)) {
        MANAGER.install(prepared);
      }
    }
    if (current != null) {
      SchedulerUtils.runGlobal(Adapt.instance, AdaptLanguage::refreshConsumers);
    }
  }

  static void validateContent(String locale, String raw) {
    LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
        List.of(parseOverlay("upstream:" + locale, locale, raw)), PluralSelector.oneOther()));
  }

  private static void refreshAvailableLocales() {
    Upstream current = upstream();
    List<String> installed;
    try {
      installed = current.directory().scan();
    } catch (IOException failure) {
      Adapt.warn("Could not list Adapt language files: " + failure.getMessage());
      installed = List.of();
    }
    Set<String> choices = new LinkedHashSet<>();
    choices.add(CATALOG.englishLocale());
    if (current.manifest() != null) {
      choices.addAll(current.manifest().locales());
    }
    choices.addAll(installed);
    Map<String, LanguageMeta> discoveredMetas = new LinkedHashMap<>();
    for (String locale : installed) {
      try {
        LanguageMeta meta = parseMeta(LanguageDirectory.readContent(current.directory().languageFile(locale))).withoutSelfBase(locale);
        if (!meta.isEmpty()) {
          discoveredMetas.put(locale, meta);
        }
      } catch (IOException invalid) {
        Adapt.verbose("Could not read language metadata for " + locale + ": " + invalid.getMessage());
      }
    }
    List<String> sorted = new ArrayList<>(choices);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    metas = Map.copyOf(discoveredMetas);
    locales = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
  }

  private static String canonicalLocale(String locale) {
    String requiredLocale = LanguageDirectory.requireLocale(locale);
    if (CATALOG.englishLocale().equalsIgnoreCase(requiredLocale)) {
      return CATALOG.englishLocale();
    }
    LanguageSourceManifest manifest = upstream().manifest();
    if (manifest != null && manifest.contains(requiredLocale)) {
      return manifest.canonical(requiredLocale);
    }
    return requiredLocale;
  }

  static LocaleOverlay parseOverlay(String source, String locale, String raw) {
    LocaleOverlay.Builder builder = LocaleOverlay.builder(source, locale);
    if (raw == null || raw.isBlank()) {
      return builder.build();
    }
    JsonElement parsed;
    try {
      parsed = TomlCodec.toJsonElement(raw);
    } catch (IOException invalid) {
      throw new IllegalArgumentException("Locale source is not valid TOML: " + source, invalid);
    }
    if (parsed == null || !parsed.isJsonObject()) {
      throw new IllegalArgumentException("Locale source is not valid TOML: " + source);
    }
    appendOverlay(builder, parsed.getAsJsonObject(), "");
    return LocalizationValidator.validValues(CATALOG, builder.build());
  }

  private static void appendOverlay(LocaleOverlay.Builder builder, JsonObject object, String prefix) {
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
      JsonElement value = entry.getValue();
      if (value == null || value.isJsonNull()) {
        continue;
      }
      try {
        if (value.isJsonObject() && CATALOG.key(key) instanceof PluralKey) {
          builder.plural(key, readPlural(key, value.getAsJsonObject()));
        } else if (value.isJsonObject()) {
          appendOverlay(builder, value.getAsJsonObject(), key);
        } else if (value.isJsonArray()) {
          builder.lines(key, readLines(key, value.getAsJsonArray()));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
          builder.text(key, value.getAsString());
        }
      } catch (IllegalArgumentException invalid) {
        continue;
      }
    }
  }

  private static List<String> readLines(String key, JsonArray array) {
    List<String> lines = new ArrayList<>(array.size());
    for (JsonElement value : array) {
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Locale line must be text: " + key);
      }
      lines.add(value.getAsString());
    }
    return lines;
  }

  private static Map<String, String> readPlural(String key, JsonObject object) {
    Map<String, String> forms = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      JsonElement value = entry.getValue();
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Locale plural form must be text: " + key + "." + entry.getKey());
      }
      forms.put(entry.getKey(), value.getAsString());
    }
    return forms;
  }

  static String renderTemplate(String template, MessageArgs arguments) {
    return renderTemplate(template, arguments, AdaptConfig.get().isAutomaticGradients());
  }

  static String renderTemplate(String template, MessageArgs arguments, boolean automaticGradients) {
    return renderTemplate(template, arguments, automaticGradients, "");
  }

  static String renderTemplate(String template, MessageArgs arguments, boolean automaticGradients, String defaultStyle) {
    String normalized = ComponentText.normalizeMarkup(template);
    boolean explicitColor = hasExplicitColor(normalized);
    String prepared = explicitColor ? normalized : ComponentText.normalizeMarkup(defaultStyle) + normalized;
    List<RenderedArgument> replacements = new ArrayList<>(arguments.size());
    int index = 0;
    for (MessageArgument argument : arguments.arguments().values()) {
      String token = "\uE000" + index + "\uE001";
      prepared = prepared.replace("{" + argument.name() + "}", token);
      String value = String.valueOf(argument.value());
      String replacement;
      if (argument.kind() != MessageArgumentKind.TRUSTED) {
        replacement = MINI_MESSAGE.escapeTags(escapeUntrusted(value));
      } else if (explicitColor) {
        replacement = MINI_MESSAGE.escapeTags(ComponentText.markup(value).plain());
      } else {
        replacement = ComponentText.normalizeMarkup(renderMarkup(value, automaticGradients));
      }
      replacements.add(new RenderedArgument(token, replacement));
      index++;
    }
    String rendered = ComponentText.component(MINI_MESSAGE.deserialize(applyReplacements(prepared, replacements))).legacy();
    return explicitColor ? C.RESET + rendered : rendered;
  }

  private static String applyReplacements(String template, List<RenderedArgument> replacements) {
    StringBuilder output = new StringBuilder(template.length());
    int cursor = 0;
    while (cursor < template.length()) {
      RenderedArgument match = null;
      for (RenderedArgument replacement : replacements) {
        if (template.startsWith(replacement.token(), cursor)) {
          match = replacement;
          break;
        }
      }
      if (match == null) {
        output.append(template.charAt(cursor++));
      } else {
        output.append(match.value());
        cursor += match.token().length();
      }
    }
    return output.toString();
  }

  private static boolean hasExplicitColor(String normalized) {
    if (normalized.indexOf('<') < 0) {
      return false;
    }
    if (normalized.length() > MAX_COLOR_CACHE_TEMPLATE_LENGTH) {
      return detectExplicitColor(normalized);
    }
    Boolean cached = EXPLICIT_COLORS.get(normalized);
    if (cached != null) {
      return cached;
    }
    if (EXPLICIT_COLORS.size() >= MAX_COLOR_CACHE_ENTRIES) {
      EXPLICIT_COLORS.clear();
    }
    return EXPLICIT_COLORS.computeIfAbsent(normalized, AdaptLanguage::detectExplicitColor);
  }

  private static boolean detectExplicitColor(String normalized) {
    ColorPresence colors = new ColorPresence();
    MINI_MESSAGE.deserializeToTree(normalized, colors);
    return colors.present;
  }

  private static String renderMarkup(String value, boolean automaticGradients) {
    String rendered = C.translateAlternateColorCodes('&', value == null ? "" : value);
    if (automaticGradients) {
      rendered = C.aura(rendered, -20, 7, 8, 0.36);
    }
    return ComponentText.markup(rendered).legacy();
  }

  static String escapeUntrusted(String value) {
    return C.stripColor(value)
        .replace("&", "＆")
        .replace("<", "＜")
        .replace(">", "＞")
        .replace("[", "［")
        .replace("]", "］");
  }

  static String normalizeLocale(String locale) {
    String value = locale == null || locale.isBlank() ? CATALOG.englishLocale() : locale.trim();
    if (!LOCALE_NAME.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid locale name: " + value);
    }
    return value;
  }

  private static void reportRejectedReload(String locale, LocalizationReloadResult result) {
    Adapt.error("Rejected locale reload for " + locale + "; continuing with " + activeLocale + ".");
    List<LocalizationIssue> issues = result.validation().errors();
    for (int index = 0; index < Math.min(issues.size(), MAX_REPORTED_ISSUES); index++) {
      LocalizationIssue issue = issues.get(index);
      Adapt.error(issue.source() + " [" + issue.key() + "]: " + issue.detail());
    }
    if (issues.size() > MAX_REPORTED_ISSUES) {
      Adapt.error((issues.size() - MAX_REPORTED_ISSUES) + " additional locale errors were omitted.");
    }
    if (result.failure() != null) {
      Adapt.error("Locale reload failed for " + locale, result.failure());
    }
  }

  private static final class ColorPresence implements TagResolver {
    private boolean present;

    @Override
    public Tag resolve(String name, ArgumentQueue arguments, Context context) {
      Tag tag = COLOR_TAGS.resolve(name, arguments, context);
      if (tag != null) {
        present = true;
      }
      return tag;
    }

    @Override
    public boolean has(String name) {
      return COLOR_TAGS.has(name);
    }
  }

  private record RenderedArgument(String token, String value) {
  }

  record Upstream(LanguageDirectory directory, LanguageSourceManifest manifest, LanguageUpstreamSync sync,
                  RuntimeException failure) {
    boolean contains(String locale) {
      return manifest != null && manifest.contains(locale);
    }

    void close() {
      if (sync != null) {
        sync.close();
      }
    }
  }
}
