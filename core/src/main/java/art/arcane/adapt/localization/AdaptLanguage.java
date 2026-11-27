package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigHotload;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamStore;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.ResolvedLines;
import art.arcane.volmlib.util.localization.ResolvedText;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.VolmitLocales;

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
  static final Pattern LOCALE_NAME = LanguageDirectory.LOCALE_PATTERN;
  private static final String ENGLISH = VolmitLocales.ENGLISH;
  private static final String MISSING_PREFIX = "[missing:";
  private static final Set<String> MISSING_KEYS = ConcurrentHashMap.newKeySet();
  private static final Set<String> REPORTED_PROBLEMS = ConcurrentHashMap.newKeySet();
  private static final TextStore TEXTS = new TextStore(TextBundle.builtIn(AdaptMessages.catalog()),
      AdaptLanguage::bundlesChanged);
  private static final LocalizationManager MANAGER = new LocalizationManager(
      LocalizationCandidate.english(AdaptMessages.catalog(), PluralSelector.oneOther())
  );
  private static volatile String activeLocale = ENGLISH;
  private static final Set<String> PENDING_INSTALLS = ConcurrentHashMap.newKeySet();
  private static volatile Upstream upstream;
  private static volatile Set<String> locales = Set.of();
  private static volatile Map<String, LanguageMeta> metas = Map.of();
  private static volatile PluginLanguageService selections;
  private static final Map<String, LocaleSnapshot> LOCALE_SNAPSHOTS = new ConcurrentHashMap<>();
  private static volatile LanguageSwitcherPort switcher;
  private static volatile MarkupPort colorCacheOwner;

  private AdaptLanguage() {
  }

  public static boolean initialize() {
    synchronized (SNAPSHOT_LOCK) {
      MANAGER.install(LocalizationSnapshot.create(LocalizationCandidate.english(TEXTS.catalog(), PluralSelector.oneOther())));
      activeLocale = ENGLISH;
    }
    return reload();
  }

  public static boolean reload() {
    return reloadInternal(null, null, true);
  }

  public static boolean reloadPassive() {
    return reloadInternal(null, null, false);
  }

  /**
   * Loads the configured locale when it differs from the active one. Language files hotload on their own, so a core
   * config change only matters through its language setting; re-parsing an unchanged locale (thousands of keys) on
   * the server thread stalled Folia's global region for over 5 s under load.
   */
  public static boolean reloadConfiguredLocale() {
    if (normalizeLocale(AdaptConfig.get().getLanguage()).equals(activeLocale)) {
      return false;
    }
    return reloadPassive();
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
      LOCALE_SNAPSHOTS.remove(locale);
      PluginLanguageService current = selections;
      if (current != null) {
        current.cache(locale, prepared);
      }
      return true;
    } catch (Exception failure) {
      AdaptLog.error("Could not reload Adapt language " + file + "; keeping the last valid messages.", failure);
      return false;
    }
  }

  private static boolean reloadInternal(File snapshotFile, String snapshotRaw, boolean writeGeneratedFiles) {
    if (writeGeneratedFiles) {
      try {
        Files.createDirectories(languageFolder().toPath());
      } catch (IOException failure) {
        AdaptLog.warn("Failed to create Adapt language folders: " + failure.getMessage());
        AdaptLog.error(failure);
      }
      try {
        ensureEnglish();
      } catch (Exception failure) {
        AdaptLog.warn("Failed to refresh the Adapt English language file: " + failure.getMessage());
        AdaptLog.error(failure);
      }
      refreshAvailableLocales();
    }
    String configuredLocale = AdaptConfig.get().getLanguage();
    String requestedLocale = configuredLocale == null || configuredLocale.isBlank()
        ? ENGLISH
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
    AdaptLog.info("Loaded locale " + requestedLocale + " with " + warningCount + " fallback "
        + (warningCount == 1 ? "entry" : "entries") + ".");
    return true;
  }

  public static String activeLocale() {
    return activeLocale;
  }

  /** The locale text for {@code player} resolves in: their personal choice, else the server locale. */
  public static String locale(UUID player) {
    PluginLanguageService current = selections;
    return current == null || player == null ? activeLocale : current.effectiveLocale(player);
  }

  /**
   * The messages {@code player}'s text resolves from right now. Every reload, edit or locale switch that changes those
   * messages yields a new instance, so identity tells a cached rendering apart from the current one.
   */
  public static LocalizationSnapshot snapshot(UUID player) {
    PluginLanguageService current = selections;
    return current == null || player == null ? MANAGER.snapshot() : current.snapshot(player);
  }

  public static File languageFolder() {
    return new File(dataFolder(), "languages");
  }

  private static File dataFolder() {
    Path directory = AdaptEnvironment.dataDirectory();
    if (directory == null) {
      throw new IllegalStateException("Adapt data directory is not available");
    }
    return directory.toFile();
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

  /**
   * Renders {@code key} in the viewer's language. A key no installed bundle declares renders as {@link #missing} and is
   * logged once instead of throwing; arguments that do not match a declared key still throw.
   */
  public static String textStyled(String defaultStyle, MessageKey key, MessageArgs arguments) {
    return render(snapshot(), defaultStyle, key, arguments);
  }

  /**
   * Renders {@code key} in {@code locale}, whoever is being rendered for: the language file of that locale and every
   * bundle's translations into it, then English per message. An invalid name renders in the server's language. The
   * first call for a locale reads its files; the result is kept until the next reload.
   */
  public static String text(String locale, MessageKey key, MessageArgs arguments) {
    return render(snapshot(locale), "", key, arguments);
  }

  static LocalizationSnapshot snapshot(String locale) {
    LocalizationSnapshot base = MANAGER.snapshot();
    String canonical;
    try {
      canonical = canonicalLocale(normalizeLocale(locale));
    } catch (IllegalArgumentException invalid) {
      return base;
    }
    if (LanguageDirectory.sameLocale(canonical, activeLocale)) {
      return base;
    }
    LocaleSnapshot cached = LOCALE_SNAPSHOTS.get(canonical);
    if (cached != null && cached.base() == base) {
      return cached.messages();
    }
    boolean english = ENGLISH.equals(canonical);
    if (!english && AdaptEnvironment.dataDirectory() == null) {
      return base;
    }
    try {
      LocalizationSnapshot prepared = LocalizationSnapshot.create(AdaptEnvironment.dataDirectory() == null
          ? LocalizationCandidate.english(TEXTS.catalog(), PluralSelector.oneOther())
          : loadCandidate(canonical, null, null));
      LOCALE_SNAPSHOTS.put(canonical, new LocaleSnapshot(base, prepared));
      return prepared;
    } catch (Exception failure) {
      AdaptLog.verbose(() -> "Could not load Adapt language " + canonical + ": " + failure.getMessage());
      return base;
    }
  }

  private static String render(LocalizationSnapshot snapshot, String defaultStyle, MessageKey key,
                               MessageArgs arguments) {
    MessageKey definition = definition(snapshot, key);
    if (definition instanceof TextKey textKey) {
      ResolvedText resolved = snapshot.resolve(textKey, arguments);
      return renderTemplate(resolved.template(), resolved.arguments(), AdaptConfig.get().isAutomaticGradients(), defaultStyle);
    }
    if (definition instanceof LinesKey linesKey) {
      ResolvedLines resolved = snapshot.resolve(linesKey, arguments);
      return renderTemplate(String.join("\n", resolved.lines()), resolved.arguments(), AdaptConfig.get().isAutomaticGradients(), defaultStyle);
    }
    if (definition instanceof PluralKey pluralKey) {
      ResolvedText resolved = snapshot.resolve(pluralKey, arguments);
      return renderTemplate(resolved.template(), resolved.arguments(), AdaptConfig.get().isAutomaticGradients(), defaultStyle);
    }
    return missing(key.id());
  }

  /** The raw template of {@code key} in the viewer's language, before placeholders and markup are applied. */
  public static String template(TextKey key) {
    LocalizationSnapshot snapshot = snapshot();
    MessageKey definition = definition(snapshot, key);
    return definition == null ? missing(key.id()) : ((TextValue) snapshot.value(definition)).template();
  }

  /** The installed message bundles. */
  public static TextStore texts() {
    return TEXTS;
  }

  /**
   * The keys of the built-in language files: the engine's messages and those the addon of namespace {@code adapt}
   * registers. Built-in language files are read, merged and validated against it.
   */
  public static MessageCatalog builtInCatalog() {
    return TEXTS.builtInCatalog();
  }

  /** The key {@code id} as an installed bundle declares it, or null. */
  public static MessageKey declared(String id) {
    return id == null ? null : TEXTS.catalog().key(id);
  }

  /** The key {@code id} as declared, or an undeclared stand-in that renders as {@link #missing}. */
  public static MessageKey key(String id) {
    MessageKey declared = declared(id);
    return declared != null ? declared : TextKey.of(standInId(id), "");
  }

  /** What text shows for a key nothing declares: the key itself, marked so it stands out and can be searched for. */
  public static String missing(String id) {
    if (MISSING_KEYS.add(id)) {
      AdaptLog.warn("No installed message bundle declares " + id + "; showing " + MISSING_PREFIX + id + "].");
    }
    return MISSING_PREFIX + id + "]";
  }

  private static MessageKey definition(LocalizationSnapshot snapshot, MessageKey key) {
    MessageKey definition = snapshot.catalog().key(AdaptMessages.declared(key).id());
    return definition != null && definition.shape() == key.shape() ? definition : null;
  }

  private static String standInId(String id) {
    if (id == null || id.isEmpty()) {
      return "missing";
    }
    String cleaned = id.replaceAll("[^A-Za-z0-9_.-]", "_");
    return Character.isLetterOrDigit(cleaned.charAt(0)) ? cleaned : "missing" + cleaned;
  }

  /** Renders text composed from {@link #template} parts the way {@link #text} renders a single message. */
  public static String render(String composed) {
    return renderTemplate(composed, MessageArgs.empty(), AdaptConfig.get().isAutomaticGradients(), "");
  }

  public static DirectorTextResolver directorResolver() {
    return (key, arguments) -> {
      MessageKey definition = definition(snapshot(), key);
      if (!(definition instanceof TextKey textKey)) {
        return C.stripColor(DirectorTextResolver.ENGLISH.resolve(key, arguments));
      }
      return C.stripColor(text(textKey, arguments));
    };
  }

  /** {@code locale} for every installed bundle: the built-in files, then each other bundle's overrides and translations. */
  private static LocalizationCandidate loadCandidate(String locale, File snapshotFile, String snapshotRaw) throws Exception {
    TextStore.Composition composition = TEXTS.composition();
    List<LocaleOverlay> overlays = new ArrayList<>(builtInCandidate(locale, snapshotFile, snapshotRaw).overlays());
    List<String> chain = new ArrayList<>();
    chain.add(locale);
    for (LocaleOverlay overlay : overlays) {
      if (!chain.contains(overlay.locale())) {
        chain.add(overlay.locale());
      }
    }
    for (String step : chain) {
      bundleOverlay(composition, step).ifPresent(overlays::add);
    }
    return new LocalizationCandidate(composition.catalog(), overlays, PluralSelector.oneOther());
  }

  /**
   * One overlay holding every bundle's own translations into {@code locale}: an admin file in
   * {@code languages/<namespace>/} wins over the translation a non-built-in bundle ships. A broken admin file is
   * reported and skipped without touching other bundles. The built-in language files come before this overlay.
   */
  private static Optional<LocaleOverlay> bundleOverlay(TextStore.Composition composition, String locale) {
    Map<String, MessageValue> values = new LinkedHashMap<>();
    for (TextBundle bundle : composition.bundles().values()) {
      File file = new File(new File(languageFolder(), bundle.namespace()), locale + ".toml");
      if (!TextBundle.BUILT_IN.equals(bundle.namespace()) && file.exists()) {
        try {
          LanguageToml.Read read = LanguageToml.overlay(file.getPath(), locale, readLocaleFile(file), bundle.catalog());
          read.overlay().values().forEach(values::putIfAbsent);
          for (String key : read.rejected()) {
            reportOnce(file.getPath() + " [" + key + "]: not a key of message bundle " + bundle.namespace()
                + " with a matching shape and placeholders; ignored.");
          }
        } catch (Exception broken) {
          reportOnce(file.getPath() + ": " + broken.getMessage() + "; using the bundle's own text.");
        }
      }
      bundle.translation(locale).ifPresent(translation -> translation.values().forEach(values::putIfAbsent));
    }
    if (values.isEmpty()) {
      return Optional.empty();
    }
    LocaleOverlay.Builder builder = LocaleOverlay.builder("bundles:" + locale, locale);
    values.forEach(builder::put);
    return Optional.of(builder.build());
  }

  private static void reportOnce(String problem) {
    if (REPORTED_PROBLEMS.add(problem)) {
      AdaptLog.warn("Adapt language: " + problem);
    }
  }

  /**
   * Rebuilds the active and cached snapshots against the new bundle set. A rejected rebuild keeps the previous
   * snapshot, so the changed bundle's text shows as missing until the language files load again.
   */
  private static void bundlesChanged() {
    for (TextBundle bundle : TEXTS.bundles()) {
      for (String problem : bundle.problems()) {
        reportOnce(problem);
      }
    }
    LocalizationReloadResult result;
    synchronized (SNAPSHOT_LOCK) {
      String locale = activeLocale;
      result = MANAGER.reload(() -> AdaptEnvironment.dataDirectory() == null
          ? LocalizationCandidate.english(TEXTS.catalog(), PluralSelector.oneOther())
          : loadCandidate(locale, null, null));
    }
    if (!result.applied()) {
      reportRejectedReload(activeLocale, result);
    }
    LOCALE_SNAPSHOTS.clear();
    PluginLanguageService current = selections;
    if (current != null) {
      current.invalidate();
    }
    runGlobal(AdaptLanguage::refreshConsumers);
  }

  private static LocalizationCandidate builtInCandidate(String locale, File snapshotFile, String snapshotRaw) throws Exception {
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
    return new LocalizationCandidate(builtInCatalog(), overlays, PluralSelector.oneOther());
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
          || LanguageDirectory.sameLocale(base, ENGLISH)) {
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

  /**
   * The connection to the language source for the current language folder. It merges and validates against the
   * built-in catalog, so a change of that catalog reconnects to the same source.
   */
  static Upstream upstream() {
    Path root = languageFolder().toPath().toAbsolutePath().normalize();
    Upstream current = upstream;
    if (current != null && current.serves(root, builtInCatalog())) {
      return current;
    }
    synchronized (AdaptLanguage.class) {
      Upstream held = upstream;
      if (held != null && held.serves(root, builtInCatalog())) {
        return held;
      }
      if (held != null && held.directory().root().equals(root)) {
        return connectUpstream(held.apiRoot(), held.rawRoot(), held.minimumCheckInterval());
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
    Upstream connected = openUpstream(new LanguageDirectory(languageFolder()), builtInCatalog(), apiRoot, rawRoot,
        minimumCheckInterval);
    upstream = connected;
    locales = Set.of();
    metas = Map.of();
    return connected;
  }

  private static Upstream openUpstream(LanguageDirectory directory, MessageCatalog catalog, URI apiRoot, URI rawRoot,
                                       Duration minimumCheckInterval) {
    try {
      LanguageSourceManifest manifest = LanguageSourceManifest.load(AdaptLanguage.class.getClassLoader(), MANIFEST_RESOURCE);
      GitHubLanguageSource source = new GitHubLanguageSource(manifest, "Adapt", apiRoot, rawRoot);
      LanguageUpstreamStore store = new LanguageUpstreamStore(directory, manifest);
      LanguageUpstreamSync sync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
          "Adapt",
          catalog,
          directory,
          manifest,
          source,
          store,
          () -> AdaptLanguageReference.render(catalog),
          AdaptLanguage::validateContent,
          AdaptLanguage::commitUpstream,
          AdaptLanguage::noteUpstreamWrite,
          AdaptLanguage::applyUpstream,
          LanguageLogger.get()
      ), minimumCheckInterval);
      return new Upstream(directory, manifest, sync, null, catalog, apiRoot, rawRoot, minimumCheckInterval);
    } catch (RuntimeException failure) {
      return new Upstream(directory, null, null, failure, catalog, apiRoot, rawRoot, minimumCheckInterval);
    }
  }

  public static synchronized void start() {
    if (selections != null) {
      return;
    }
    selections = new PluginLanguageService(new PluginLanguageService.Options(
        dataFolder().toPath().resolve("languages/language-preferences.properties"),
        AdaptLanguage::availableLocales,
        AdaptLanguage::activeLocale,
        MANAGER::snapshot,
        AdaptLanguage::prepareLocale,
        AdaptLanguage::selectDefault,
        LanguageLogger.get()
    ));
    LanguageSwitcherPort opened = AdaptPorts.get(LanguageSwitcherPort.class, LanguageSwitcherPort.NONE);
    opened.open(selections);
    switcher = opened;
  }

  public static synchronized void shutdown() {
    LanguageSwitcherPort currentSwitcher = switcher;
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

  /** The display name a locale's language file declares in its meta table, or null. */
  public static String displayName(String locale) {
    return metas.getOrDefault(locale, LanguageMeta.empty()).name().orElse(null);
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
    if (ENGLISH.equals(locale) || current.sync() == null || !current.contains(locale)
        || Files.exists(new File(languageFolder(), locale + ".toml").toPath(), LinkOption.NOFOLLOW_LINKS)
        || !PENDING_INSTALLS.add(locale)) {
      return;
    }
    AdaptLog.info("Installing Adapt language " + locale + " from " + sourceDescription() + "...");
    current.sync().installAsync(locale).whenComplete((content, failure) -> completeConfiguredInstall(locale, failure));
  }

  private static LocalizationSnapshot snapshot() {
    PluginLanguageService current = selections;
    return current == null ? MANAGER.snapshot() : current.snapshot();
  }

  static LocalizationSnapshot prepareLocale(String locale) throws Exception {
    return LocalizationSnapshot.create(loadCandidate(ensureLocale(locale), null, null));
  }

  /** The editor edits the built-in bundle only, so its documents hold the built-in keys and nothing else. */
  private static LocalizationSnapshot prepareEditorLocale(String locale) throws Exception {
    return LocalizationSnapshot.create(builtInCandidate(ensureLocale(locale), null, null));
  }

  private static String ensureLocale(String locale) throws Exception {
    String requiredLocale = canonicalLocale(locale);
    File file = new File(languageFolder(), requiredLocale + ".toml");
    if (!ENGLISH.equals(requiredLocale) && !file.exists()) {
      installLocale(requiredLocale, file);
      refreshAvailableLocales();
    }
    return requiredLocale;
  }

  private static void installLocale(String locale, File file) throws Exception {
    Upstream current = upstream();
    if (!current.contains(locale)) {
      String content = AdaptLanguageReference.renderCustom(builtInCatalog(), locale);
      write(file, () -> {
        AtomicFileIO.writeString(file.toPath(), content);
        return new ConfigHotloadEngine.Written<>(content, null);
      });
      return;
    }
    if (current.sync() == null) {
      throw new IOException("Language " + locale + " is not installed and the language source is " + sourceDescription());
    }
    AdaptLog.info("Installing Adapt language " + locale + " from " + sourceDescription() + "...");
    current.sync().install(locale);
    AdaptLog.info("Installed Adapt language " + locale + " to " + file.toPath().toAbsolutePath().normalize() + ".");
  }

  private static void completeConfiguredInstall(String locale, Throwable failure) {
    PENDING_INSTALLS.remove(locale);
    if (failure != null) {
      AdaptLog.error("Unable to install Adapt language " + locale + "; the previous language remains active.", failure);
      return;
    }
    AdaptLog.info("Installed Adapt language " + locale + " to "
        + new File(languageFolder(), locale + ".toml").toPath().toAbsolutePath().normalize() + ".");
    if (!LanguageDirectory.sameLocale(locale, normalizeLocale(AdaptConfig.get().getLanguage()))) {
      return;
    }
    if (!runGlobal(AdaptLanguage::activateConfiguredLocale)) {
      AdaptLog.warn("Installed Adapt language " + locale + " activates on the next reload or restart.");
    }
  }

  private static void activateConfiguredLocale() {
    if (reloadPassive()) {
      refreshConsumers();
    }
  }

  public static PluginLanguageEditor.Options editorOptions() {
    return new PluginLanguageEditor.Options(AdaptLanguage::prepareEditorLocale, AdaptLanguage::writeMessage);
  }

  private static LocalizationSnapshot writeMessage(PluginLanguageEditor.Edit edit) throws IOException {
    LocaleOverlay edited = LocaleOverlay.builder("editor", edit.locale()).put(edit.key(), edit.value()).build();
    MessageCatalog catalog = builtInCatalog();
    LocalizationValidator.validate(catalog, List.of(edited)).throwIfInvalid();
    File file = new File(languageFolder(), edit.locale() + ".toml");
    try {
      LocalizationSnapshot prepared = write(file, () -> {
        ConfigHotloadEngine.Written<LocalizationSnapshot> written = LanguageFileEditor.update(file.toPath(), raw -> {
          LocalizationSnapshot current = editorSnapshot(edit.locale(), file, raw);
          if (!current.value(catalog.require(edit.key())).equals(edit.expected())) {
            throw new IOException("Language message changed; reopen it before saving");
          }
          String updated = TomlLanguageEditor.upsert(raw, edit.key(), edit.value()).content();
          return new LanguageFileEditor.Prepared<>(updated,
              new ConfigHotloadEngine.Written<>(updated, editorSnapshot(edit.locale(), file, updated)));
        });
        synchronized (SNAPSHOT_LOCK) {
          if (edit.locale().equals(activeLocale)) {
            MANAGER.install(LocalizationSnapshot.create(loadCandidate(edit.locale(), file, written.content())));
          }
        }
        return written;
      });
      runGlobal(AdaptLanguage::refreshConsumers);
      return prepared;
    } catch (IOException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IOException("Could not save Adapt language " + edit.locale(), failure);
    }
  }

  private static LocalizationSnapshot editorSnapshot(String locale, File file, String raw) throws IOException {
    try {
      return LocalizationSnapshot.create(builtInCandidate(locale, file, raw));
    } catch (Exception failure) {
      throw new IOException("Could not validate Adapt language " + locale, failure);
    }
  }

  static void selectDefault(String locale, LocalizationSnapshot prepared) throws Exception {
    write(AdaptEnvironment.dataFile("adapt.toml"), () -> {
      String raw = AdaptConfig.selectLanguage(locale);
      synchronized (SNAPSHOT_LOCK) {
        MANAGER.install(prepared);
        activeLocale = locale;
      }
      return new ConfigHotloadEngine.Written<>(raw, null);
    });
    runGlobal(AdaptLanguage::refreshConsumers);
  }

  private static <T> T write(File file, ConfigHotloadEngine.FileWrite<T> writer) throws Exception {
    return ConfigHotload.writeTracked(file, writer);
  }

  private static void refreshConsumers() {
    ConfigHotload.refreshLocalization();
  }

  private static boolean runGlobal(Runnable task) {
    AdaptScheduler scheduler = Tasks.scheduler();
    return scheduler != null && scheduler.runGlobal(task);
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
      String source = locale.equals(ENGLISH)
          ? "the built-in catalog"
          : sourceDescription() + " (" + revision + ")";
      AdaptLog.info("Updated Adapt language " + locale + " from " + source + ": " + result.summary() + ".");
    }
    if (report.coolingDown()) {
      AdaptLog.info("Adapt language source " + sourceDescription() + " was checked recently; the next check is allowed in "
          + report.cooldownRemaining() + ".");
    } else if (report.failure().isEmpty() && report.changedLocales().isEmpty() && report.failures().isEmpty()
        && report.revision().isPresent()) {
      AdaptLog.info("Adapt languages match " + sourceDescription() + " (" + revision + ").");
    }
    return report;
  }

  private static void ensureEnglish() throws Exception {
    LanguageUpstreamSync sync = upstream().sync();
    if (sync == null) {
      AdaptLanguageReference.write();
      return;
    }
    sync.mergeEnglish().filter(LanguageCatalogMerge.MergeResult::changed).ifPresent(result -> AdaptLog.info(
        "Updated Adapt language " + ENGLISH + " from the built-in catalog: " + result.summary() + "."));
  }

  private static <T> T commitUpstream(LanguageUpstreamSync.CommitAction<T> action) throws IOException {
    PluginLanguageService current = selections;
    if (current == null) {
      return action.apply();
    }
    return current.commitUpdate(action::apply);
  }

  private static void noteUpstreamWrite(Path file, String content) {
    ConfigHotload.noteTrackedWrite(file.toFile(), content);
  }

  private static void applyUpstream(String locale, String content, LanguageCatalogMerge.MergeResult result)
      throws Exception {
    File file = new File(languageFolder(), locale + ".toml");
    LocalizationSnapshot prepared = LocalizationSnapshot.create(loadCandidate(locale, file, content));
    LOCALE_SNAPSHOTS.remove(locale);
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
      runGlobal(AdaptLanguage::refreshConsumers);
    }
  }

  static void validateContent(String locale, String raw) {
    LocalizationSnapshot.create(new LocalizationCandidate(builtInCatalog(),
        List.of(parseOverlay("upstream:" + locale, locale, raw)), PluralSelector.oneOther()));
  }

  private static void refreshAvailableLocales() {
    Upstream current = upstream();
    List<String> installed;
    try {
      installed = current.directory().scan();
    } catch (IOException failure) {
      AdaptLog.warn("Could not list Adapt language files: " + failure.getMessage());
      installed = List.of();
    }
    Set<String> choices = new LinkedHashSet<>();
    choices.add(ENGLISH);
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
        AdaptLog.verbose("Could not read language metadata for " + locale + ": " + invalid.getMessage());
      }
    }
    List<String> sorted = new ArrayList<>(choices);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    metas = Map.copyOf(discoveredMetas);
    locales = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
  }

  private static String canonicalLocale(String locale) {
    String requiredLocale = LanguageDirectory.requireLocale(locale);
    if (ENGLISH.equalsIgnoreCase(requiredLocale)) {
      return ENGLISH;
    }
    LanguageSourceManifest manifest = upstream().manifest();
    if (manifest != null && manifest.contains(requiredLocale)) {
      return manifest.canonical(requiredLocale);
    }
    return requiredLocale;
  }

  static LocaleOverlay parseOverlay(String source, String locale, String raw) {
    return LanguageToml.overlay(source, locale, raw, builtInCatalog()).overlay();
  }

  static String renderTemplate(String template, MessageArgs arguments) {
    return renderTemplate(template, arguments, AdaptConfig.get().isAutomaticGradients());
  }

  static String renderTemplate(String template, MessageArgs arguments, boolean automaticGradients) {
    return renderTemplate(template, arguments, automaticGradients, "");
  }

  static String renderTemplate(String template, MessageArgs arguments, boolean automaticGradients, String defaultStyle) {
    MarkupPort markup = MarkupPort.current();
    String normalized = markup.normalize(template);
    boolean explicitColor = hasExplicitColor(markup, normalized);
    String prepared = explicitColor ? normalized : markup.normalize(defaultStyle) + normalized;
    List<RenderedArgument> replacements = new ArrayList<>(arguments.size());
    int index = 0;
    for (MessageArgument argument : arguments.arguments().values()) {
      String token = "\uE000" + index + "\uE001";
      prepared = prepared.replace("{" + argument.name() + "}", token);
      String value = String.valueOf(argument.value());
      String replacement;
      if (argument.kind() != MessageArgumentKind.TRUSTED) {
        replacement = markup.escapeTags(escapeUntrusted(value));
      } else if (explicitColor) {
        replacement = markup.escapeTags(markup.plain(value));
      } else {
        replacement = markup.normalize(renderMarkup(markup, value, automaticGradients));
      }
      replacements.add(new RenderedArgument(token, replacement));
      index++;
    }
    String rendered = markup.renderMiniMessage(applyReplacements(prepared, replacements));
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

  private static boolean hasExplicitColor(MarkupPort markup, String normalized) {
    if (normalized.indexOf('<') < 0) {
      return false;
    }
    if (normalized.length() > MAX_COLOR_CACHE_TEMPLATE_LENGTH) {
      return markup.hasColorTags(normalized);
    }
    if (colorCacheOwner != markup) {
      EXPLICIT_COLORS.clear();
      colorCacheOwner = markup;
    }
    Boolean cached = EXPLICIT_COLORS.get(normalized);
    if (cached != null) {
      return cached;
    }
    if (EXPLICIT_COLORS.size() >= MAX_COLOR_CACHE_ENTRIES) {
      EXPLICIT_COLORS.clear();
    }
    return EXPLICIT_COLORS.computeIfAbsent(normalized, markup::hasColorTags);
  }

  private static String renderMarkup(MarkupPort markup, String value, boolean automaticGradients) {
    String rendered = C.translateAlternateColorCodes('&', value == null ? "" : value);
    if (automaticGradients) {
      rendered = C.aura(rendered, -20, 7, 8, 0.36);
    }
    return markup.legacy(rendered);
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
    String value = locale == null || locale.isBlank() ? ENGLISH : locale.trim();
    if (!LOCALE_NAME.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid locale name: " + value);
    }
    return value;
  }

  private static void reportRejectedReload(String locale, LocalizationReloadResult result) {
    AdaptLog.error("Rejected locale reload for " + locale + "; continuing with " + activeLocale + ".");
    List<LocalizationIssue> issues = result.validation().errors();
    for (int index = 0; index < Math.min(issues.size(), MAX_REPORTED_ISSUES); index++) {
      LocalizationIssue issue = issues.get(index);
      AdaptLog.error(issue.source() + " [" + issue.key() + "]: " + issue.detail());
    }
    if (issues.size() > MAX_REPORTED_ISSUES) {
      AdaptLog.error((issues.size() - MAX_REPORTED_ISSUES) + " additional locale errors were omitted.");
    }
    if (result.failure() != null) {
      AdaptLog.error("Locale reload failed for " + locale, result.failure());
    }
  }

  private record RenderedArgument(String token, String value) {
  }

  record Upstream(LanguageDirectory directory, LanguageSourceManifest manifest, LanguageUpstreamSync sync,
                  RuntimeException failure, MessageCatalog catalog, URI apiRoot, URI rawRoot,
                  Duration minimumCheckInterval) {
    boolean serves(Path root, MessageCatalog builtIn) {
      return directory.root().equals(root) && catalog == builtIn;
    }

    boolean contains(String locale) {
      return manifest != null && manifest.contains(locale);
    }

    void close() {
      if (sync != null) {
        sync.close();
      }
    }
  }

  /** A locale's messages and the server-language snapshot they were built beside; a newer one means a reload. */
  private record LocaleSnapshot(LocalizationSnapshot base, LocalizationSnapshot messages) {
  }
}
