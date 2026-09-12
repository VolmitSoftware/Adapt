package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.adapt.service.HotloadSVC;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class AdaptLanguage {
  private static final Object SNAPSHOT_LOCK = new Object();
  private static final long MAX_LOCALE_BYTES = 2L * 1024L * 1024L;
  private static final int MAX_REPORTED_ISSUES = 12;
  static final Pattern LOCALE_NAME = Pattern.compile("[A-Za-z0-9_-]+");
  private static final MessageCatalog CATALOG = AdaptMessages.catalog();
  private static final LocalizationManager MANAGER = new LocalizationManager(
      LocalizationCandidate.english(CATALOG, PluralSelector.oneOther())
  );
  private static volatile String activeLocale = CATALOG.englishLocale();
  private static volatile RemoteLanguageCatalog remote;
  private static volatile Path remoteRoot;
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
      AdaptLanguageReference.write();
      try {
        Files.createDirectories(languageFolder().toPath());
      } catch (IOException failure) {
        Adapt.warn("Failed to create Adapt language folders: " + failure.getMessage());
        Adapt.error(failure);
      }
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
    if (key instanceof TextKey textKey) {
      return render(snapshot().resolve(textKey, arguments));
    }
    if (key instanceof LinesKey linesKey) {
      return render(snapshot().resolve(linesKey, arguments));
    }
    if (key instanceof PluralKey pluralKey) {
      return render(snapshot().resolve(pluralKey, arguments));
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
    if (snapshotFile != null && file.getAbsoluteFile().equals(snapshotFile.getAbsoluteFile())) {
      if (snapshotRaw != null) {
        overlays.add(parseOverlay(snapshotFile.getPath(), locale, snapshotRaw));
      } else if (!Files.notExists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Language file could not be read: " + file);
      }
    } else if (file.exists()) {
      overlays.add(loadFileOverlay(file, locale));
    }
    return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
  }

  private static LocaleOverlay loadFileOverlay(File file, String locale) throws Exception {
    if (!file.isFile()) {
      throw new IllegalArgumentException("Locale file is not a regular file: " + file.getPath());
    }
    if (file.length() > MAX_LOCALE_BYTES) {
      throw new IllegalArgumentException("Locale file is too large: " + file.getPath());
    }
    return parseOverlay(file.getPath(), locale, Files.readString(file.toPath()));
  }

  static RemoteLanguageCatalog remote() {
    Path root = Adapt.instance.getDataFolder().toPath().toAbsolutePath().normalize();
    RemoteLanguageCatalog current = remote;
    if (current != null && root.equals(remoteRoot)) {
      return current;
    }
    synchronized (AdaptLanguage.class) {
      if (remote != null && root.equals(remoteRoot)) {
        return remote;
      }
      if (remote != null) {
        remote.close();
      }
      remote = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
          "Adapt",
          URI.create("https://raw.githubusercontent.com/VolmitSoftware/Adapt/"),
          "src/main/resources",
          ".toml",
          "adapt-language-source.properties",
          AdaptLanguage.class.getClassLoader()
      ));
      remoteRoot = root;
      return remote;
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
            DirectorMiniMenu.Theme.adaptRed(), directorResolver(), editorOptions()), AdaptLanguageEditor.presentation());
    requestConfiguredLocale();
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
    RemoteLanguageCatalog currentRemote = remote;
    remote = null;
    remoteRoot = null;
    if (currentRemote != null) {
      currentRemote.close();
    }
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
    Set<String> locales = new LinkedHashSet<>();
    locales.add(CATALOG.englishLocale());
    locales.addAll(remote().availableLocales());
    return Set.copyOf(locales);
  }

  public static String text(UUID player, MessageKey key, MessageArgs arguments) {
    return LanguageAudience.call(player, () -> text(key, arguments));
  }

  public static void requestConfiguredLocale() {
    String locale = normalizeLocale(AdaptConfig.get().getLanguage());
    PluginLanguageService current = selections;
    if (CATALOG.englishLocale().equals(locale) || current == null
        || Files.exists(new File(languageFolder(), locale + ".toml").toPath(), LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    current.selectDefault(locale).exceptionally(failure -> {
      Adapt.error("Failed to prepare Adapt locale " + locale + ".", failure);
      return null;
    });
  }

  private static LocalizationSnapshot snapshot() {
    PluginLanguageService current = selections;
    return current == null ? MANAGER.snapshot() : current.snapshot();
  }

  private static LocalizationSnapshot prepareLocale(String locale) throws Exception {
    if (!CATALOG.englishLocale().equals(locale) && remote().availableLocales().contains(locale)) {
      File file = new File(languageFolder(), locale + ".toml");
      if (file.exists()) {
        remote().readOrInstall(locale, file.toPath(), AdaptLanguage::validateDownload);
      } else {
        write(file, () -> {
          String raw = remote().readOrInstall(locale, file.toPath(), AdaptLanguage::validateDownload);
          return new ConfigHotloadEngine.Written<>(raw, null);
        });
      }
    }
    return LocalizationSnapshot.create(loadCandidate(locale, null, null));
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

  private static void validateDownload(String locale, String raw) {
    parseOverlay("download:" + locale, locale, raw);
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

  private static String render(ResolvedText resolved) {
    return renderTemplate(resolved.template(), resolved.arguments());
  }

  private static String render(ResolvedLines resolved) {
    return renderTemplate(String.join("\n", resolved.lines()), resolved.arguments());
  }

  static String renderTemplate(String template, MessageArgs arguments) {
    return renderTemplate(template, arguments, AdaptConfig.get().isAutomaticGradients());
  }

  static String renderTemplate(String template, MessageArgs arguments, boolean automaticGradients) {
    String prepared = template;
    List<RenderedArgument> replacements = new ArrayList<>(arguments.size());
    int index = 0;
    for (MessageArgument argument : arguments.arguments().values()) {
      String token = "\uE000" + index + "\uE001";
      prepared = prepared.replace("{" + argument.name() + "}", token);
      replacements.add(new RenderedArgument(token, argument));
      index++;
    }

    return applyReplacements(renderMarkup(prepared, automaticGradients), replacements, automaticGradients);
  }

  private static String applyReplacements(
      String rendered,
      List<RenderedArgument> replacements,
      boolean automaticGradients
  ) {
    StringBuilder output = new StringBuilder(rendered.length());
    int cursor = 0;
    while (cursor < rendered.length()) {
      RenderedArgument match = null;
      for (RenderedArgument replacement : replacements) {
        if (rendered.startsWith(replacement.token(), cursor)) {
          match = replacement;
          break;
        }
      }
      if (match == null) {
        output.append(rendered.charAt(cursor));
        cursor++;
        continue;
      }

      MessageArgument argument = match.argument();
      String value = String.valueOf(argument.value());
      output.append(argument.kind() == MessageArgumentKind.TRUSTED
          ? renderMarkup(value, automaticGradients)
          : escapeUntrusted(value));
      cursor += match.token().length();
    }
    return output.toString();
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

  private record RenderedArgument(String token, MessageArgument argument) {
  }
}
