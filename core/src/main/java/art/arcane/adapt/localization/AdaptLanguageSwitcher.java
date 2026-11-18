package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.command.CommandHelpPort;
import art.arcane.adapt.command.CommandMenu;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.command.PlayerCommandSource;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.VolmitLocales;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The {@code /adapt language} chooser without Bukkit services: personal and server-default language over the shared
 * {@link PluginLanguageService} (personal choices persist in {@code languages/language-preferences.properties} exactly as
 * on Bukkit), plus the upstream language update. Menus are drawn by {@link CommandHelpPort#menu}. The in-game message
 * editor ({@code server edit}) is {@link LanguageEditorGui}; the cross-plugin {@code /volmit} hub stays Bukkit-only.
 */
public final class AdaptLanguageSwitcher implements LanguageSwitcherPort {
  public static final String SELF_PERMISSION = "volmit.language.self";
  public static final String PLUGIN_SELF_PERMISSION = "adapt.language.self";
  public static final String ADMIN_PERMISSION = "volmit.language.admin";
  public static final String PLUGIN_ADMIN_PERMISSION = "adapt.configurator";
  private static final String PLUGIN = "Adapt";
  private static final String COMMAND = "/adapt language";
  private static final int PAGE_SIZE = 9;
  private static final String PRIMARY_LEFT = "#8b0000";
  private static final String PRIMARY_RIGHT = "#ff4d4d";
  private static final String DESCRIPTION = "#ffc7c7";
  private static final String OPTIONAL = "#ff9e9e";
  private static final String MUTED = "#d8a0a0";

  private final Supplier<CompletableFuture<LanguageUpstreamSync.SyncReport>> update;
  private final PluginLanguageEditor.Options editing;
  private volatile PluginLanguageService languages;
  private volatile LanguageEditorGui editor;

  public AdaptLanguageSwitcher() {
    this(AdaptLanguage::startUpstreamCheck);
  }

  AdaptLanguageSwitcher(Supplier<CompletableFuture<LanguageUpstreamSync.SyncReport>> update) {
    this(update, AdaptLanguage.editorOptions());
  }

  AdaptLanguageSwitcher(Supplier<CompletableFuture<LanguageUpstreamSync.SyncReport>> update, PluginLanguageEditor.Options editing) {
    this.update = Objects.requireNonNull(update, "update");
    this.editing = Objects.requireNonNull(editing, "editing");
  }

  @Override
  public synchronized void open(PluginLanguageService selections) {
    close();
    languages = Objects.requireNonNull(selections, "selections");
    editor = new LanguageEditorGui(selections, editing,
        (PlayerRef player) -> command(new PlayerCommandSource(player), new String[0]));
    AdaptCore core = AdaptCore.current();
    if (core != null) {
      PermissionHost permissions = core.getPlatform().permissions();
      permissions.register(SELF_PERMISSION, "Allows choosing your own language for Volmit plugins.", PermissionDefault.TRUE);
      permissions.register(ADMIN_PERMISSION, "Allows choosing the server language of Volmit plugins.", PermissionDefault.OP);
    }
  }

  @Override
  public synchronized void close() {
    languages = null;
    LanguageEditorGui current = editor;
    editor = null;
    if (current != null) {
      current.close();
    }
  }

  @Override
  public void command(CommandSource source, String[] arguments) {
    LanguageAudience.run(playerId(source), () -> execute(source, arguments));
  }

  @Override
  public List<String> complete(CommandSource source, String[] arguments) {
    PluginLanguageService current = languages;
    if (current == null || arguments.length == 0) {
      return List.of();
    }
    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    if (arguments.length == 1) {
      if (canSelectSelf(source)) {
        candidates.add("self");
      }
      if (canSelectServer(source)) {
        candidates.add("server");
        candidates.add("update");
      }
    } else if (arguments.length == 2) {
      if (arguments[0].equalsIgnoreCase("self") && canSelectSelf(source)) {
        candidates.addAll(current.availableLocales());
        candidates.add("reset");
      } else if (arguments[0].equalsIgnoreCase("server") && canSelectServer(source)) {
        candidates.addAll(current.availableLocales());
        if (source.player().isPresent()) {
          candidates.add("edit");
        }
      }
    } else if (arguments.length == 3 && arguments[0].equalsIgnoreCase("server") && arguments[1].equalsIgnoreCase("edit")
        && canSelectServer(source) && source.player().isPresent()) {
      candidates.addAll(current.availableLocales());
    }
    return matching(candidates, arguments[arguments.length - 1]);
  }

  private void execute(CommandSource source, String[] arguments) {
    PluginLanguageService current = languages;
    if (current == null) {
      message(source, BukkitLanguageMessages.STOPPING);
      return;
    }
    if (arguments.length == 0) {
      showHome(source, current);
      return;
    }
    if (arguments[0].equalsIgnoreCase("update")) {
      if (arguments.length > 1) {
        message(source, BukkitLanguageMessages.UPDATE_USAGE, MessageArgument.untrusted("command", "adapt"));
        return;
      }
      if (allowed(source, "server")) {
        runUpdate(source);
      }
      return;
    }
    if (arguments.length >= 2 && arguments[0].equalsIgnoreCase("server") && arguments[1].equalsIgnoreCase("edit")) {
      edit(source, arguments);
      return;
    }
    String scope = arguments[0].toLowerCase(Locale.ROOT);
    if ((!scope.equals("self") && !scope.equals("server")) || arguments.length > 2) {
      message(source, BukkitLanguageMessages.SELECTION_USAGE, MessageArgument.untrusted("command", "adapt"));
      return;
    }
    if (!allowed(source, scope)) {
      return;
    }
    if (arguments.length == 1) {
      showLocales(source, current, scope, 1);
    } else if (arguments[1].startsWith("page=")) {
      try {
        showLocales(source, current, scope, Integer.parseInt(arguments[1].substring(5)));
      } catch (NumberFormatException exception) {
        message(source, BukkitLanguageMessages.NUMERIC_PAGE);
      }
    } else {
      select(source, current, scope, arguments[1]);
    }
  }

  private void edit(CommandSource source, String[] arguments) {
    if (!allowed(source, "server")) {
      return;
    }
    Optional<PlayerRef> player = source.player();
    LanguageEditorGui current = editor;
    if (player.isEmpty()) {
      message(source, BukkitLanguageMessages.EDITOR_PLAYER_ONLY);
    } else if (arguments.length > 3) {
      message(source, BukkitLanguageMessages.EDITOR_USAGE, MessageArgument.untrusted("command", "adapt"));
    } else if (current != null) {
      current.open(player.get(), arguments.length == 3 ? arguments[2] : null);
    }
  }

  private boolean allowed(CommandSource source, String scope) {
    if (scope.equals("self")) {
      if (source.player().isEmpty()) {
        message(source, BukkitLanguageMessages.PERSONAL_PLAYER_ONLY);
        return false;
      }
      if (!source.hasPermission(SELF_PERMISSION)) {
        message(source, BukkitLanguageMessages.PERSONAL_PERMISSION);
        return false;
      }
      if (!source.hasPermission(PLUGIN_SELF_PERMISSION)) {
        message(source, BukkitLanguageMessages.PLUGIN_PERSONAL_PERMISSION, MessageArgument.untrusted("plugin", PLUGIN));
        return false;
      }
      return true;
    }
    if (!canSelectServer(source)) {
      message(source, BukkitLanguageMessages.PLUGIN_SERVER_PERMISSION, MessageArgument.untrusted("plugin", PLUGIN));
      return false;
    }
    return true;
  }

  private static boolean canSelectSelf(CommandSource source) {
    return source.player().isPresent() && source.hasPermission(SELF_PERMISSION) && source.hasPermission(PLUGIN_SELF_PERMISSION);
  }

  private static boolean canSelectServer(CommandSource source) {
    return source.hasPermission(ADMIN_PERMISSION) || source.hasPermission(PLUGIN_ADMIN_PERMISSION);
  }

  private void showHome(CommandSource source, PluginLanguageService current) {
    String serverLocale = current.defaultLocale();
    List<String> entries = new ArrayList<>();
    UUID playerId = playerId(source);
    if (canSelectSelf(source) && playerId != null) {
      String personalLocale = current.effectiveLocale(playerId);
      entries.add(link(source, localized(BukkitLanguageMessages.YOUR_LANGUAGE), COMMAND + " self",
          localized(BukkitLanguageMessages.CURRENT, MessageArgs.builder().untrusted("locale", personalLocale).build())));
      if (canSelectServer(source)) {
        String state = sameLocale(serverLocale, personalLocale)
            ? localized(BukkitLanguageMessages.CURRENT, MessageArgs.builder().untrusted("locale", serverLocale).build())
            : localized(BukkitLanguageMessages.CURRENT_WITH_PERSONAL, MessageArgs.builder()
                .untrusted("locale", serverLocale)
                .untrusted("personal", personalLocale)
                .build());
        entries.add(link(source, localized(BukkitLanguageMessages.SERVER_DEFAULT), COMMAND + " server", state));
      }
      entries.add(link(source, localized(BukkitLanguageMessages.RESET_YOUR_LANGUAGE), COMMAND + " self reset",
          localized(BukkitLanguageMessages.RESET_DESCRIPTION)));
    } else if (canSelectServer(source)) {
      entries.add(link(source, localized(BukkitLanguageMessages.SERVER_DEFAULT), COMMAND + " server",
          localized(BukkitLanguageMessages.CURRENT, MessageArgs.builder().untrusted("locale", serverLocale).build())));
    }
    if (playerId != null && canSelectServer(source)) {
      entries.add(link(source, localized(BukkitLanguageMessages.EDIT_MESSAGES), COMMAND + " server edit",
          localized(BukkitLanguageMessages.EDIT_DESCRIPTION)));
    }
    menus().menu(source, new CommandMenu(COMMAND, COMMAND, "/adapt", entries,
        styled(localized(BukkitLanguageMessages.NO_CONTROLS), MUTED), 1, Math.max(1, entries.size())));
  }

  private void showLocales(CommandSource source, PluginLanguageService current, String scope, int requestedPage) {
    String base = COMMAND + " " + scope;
    UUID playerId = scope.equals("self") ? playerId(source) : null;
    String selected = playerId == null ? current.defaultLocale() : current.effectiveLocale(playerId);
    List<String> entries = new ArrayList<>();
    if (canSelectSelf(source)) {
      entries.add(link(source, localized(BukkitLanguageMessages.YOUR_LANGUAGE), COMMAND + " self",
          localized(BukkitLanguageMessages.YOUR_DESCRIPTION)));
    }
    if (canSelectServer(source)) {
      entries.add(link(source, localized(BukkitLanguageMessages.SERVER_DEFAULT), COMMAND + " server",
          localized(BukkitLanguageMessages.SERVER_DESCRIPTION)));
    }
    if (playerId != null && current.playerLocale(playerId).isPresent()) {
      entries.add(link(source, localized(BukkitLanguageMessages.USE_SERVER_DEFAULT), base + " reset",
          localized(BukkitLanguageMessages.REMOVE_PERSONAL_DESCRIPTION)));
    }
    for (String locale : current.availableLocales()) {
      entries.add(languageLink(source, locale, displayName(locale), sameLocale(locale, selected), base + " " + locale));
    }
    menus().menu(source, new CommandMenu(base, base, COMMAND, entries, "", requestedPage, PAGE_SIZE));
  }

  private void select(CommandSource source, PluginLanguageService current, String scope, String locale) {
    if (scope.equals("server") && locale.equalsIgnoreCase("reset")) {
      message(source, BukkitLanguageMessages.SERVER_LOCALE_REQUIRED);
      return;
    }
    if (!locale.equalsIgnoreCase("reset") && current.availableLocales().stream().noneMatch(candidate -> sameLocale(candidate, locale))) {
      message(source, BukkitLanguageMessages.UNAVAILABLE_FOR_ALL, MessageArgument.untrusted("locale", locale));
      return;
    }
    message(source, BukkitLanguageMessages.PREPARING,
        MessageArgument.untrusted("locale", locale),
        MessageArgument.untrusted("target", PLUGIN));
    UUID playerId = playerId(source);
    CompletableFuture<String> pending;
    if (scope.equals("server")) {
      pending = current.selectDefault(locale).thenApply(ignored -> current.defaultLocale());
    } else if (locale.equalsIgnoreCase("reset")) {
      pending = current.clearPlayer(playerId).thenApply(ignored -> "reset");
    } else {
      pending = current.selectPlayer(playerId, locale).thenApply(ignored -> current.effectiveLocale(playerId));
    }
    pending.whenComplete((String applied, Throwable failure) -> {
      if (failure != null) {
        AdaptLog.error("Unable to select " + locale + " for " + PLUGIN, failure);
      }
      if (languages == current) {
        LanguageAudience.run(playerId, () -> selectionFeedback(source, scope, locale, applied, failure));
      }
    });
  }

  private void selectionFeedback(CommandSource source, String scope, String requested, String applied, Throwable failure) {
    if (failure != null) {
      message(source, BukkitLanguageMessages.SAVE_FAILED, MessageArgument.untrusted("plugin", PLUGIN));
    } else if (!sameLocale(requested, applied)) {
      message(source, BukkitLanguageMessages.ENGLISH_FALLBACK,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("locale", requested));
    } else if (scope.equals("self") && applied.equalsIgnoreCase("reset")) {
      message(source, BukkitLanguageMessages.SERVER_DEFAULT_SELECTED, MessageArgument.untrusted("plugin", PLUGIN));
    } else if (scope.equals("self")) {
      message(source, BukkitLanguageMessages.PERSONAL_SELECTED,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("locale", applied));
    } else {
      message(source, BukkitLanguageMessages.SERVER_SELECTED,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("locale", applied));
    }
  }

  private void runUpdate(CommandSource source) {
    message(source, BukkitLanguageMessages.UPDATE_CHECKING, MessageArgument.untrusted("plugin", PLUGIN));
    CompletableFuture<LanguageUpstreamSync.SyncReport> pending;
    try {
      pending = Objects.requireNonNull(update.get(), "Language update result");
    } catch (RuntimeException failure) {
      AdaptLog.error("Unable to start the language update for " + PLUGIN, failure);
      message(source, BukkitLanguageMessages.UPDATE_FAILED,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("reason", describe(failure)));
      return;
    }
    UUID playerId = playerId(source);
    pending.whenComplete((LanguageUpstreamSync.SyncReport report, Throwable failure) ->
        LanguageAudience.run(playerId, () -> updateFeedback(source, report, failure)));
  }

  private void updateFeedback(CommandSource source, LanguageUpstreamSync.SyncReport report, Throwable failure) {
    if (failure != null || report == null) {
      if (failure != null) {
        AdaptLog.error("Language update failed for " + PLUGIN, failure);
      }
      message(source, BukkitLanguageMessages.UPDATE_FAILED,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("reason", failure == null ? "no report" : describe(failure)));
      return;
    }
    if (report.failure().isPresent()) {
      message(source, BukkitLanguageMessages.UPDATE_FAILED,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("reason", report.failure().get()));
      return;
    }
    if (report.coolingDown()) {
      message(source, BukkitLanguageMessages.UPDATE_COOLDOWN,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("remaining", report.cooldownRemaining()));
      return;
    }
    List<String> changed = report.changedLocales();
    for (String locale : changed) {
      LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
      message(source, BukkitLanguageMessages.UPDATE_APPLIED,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("locale", locale),
          MessageArgument.untrusted("added", String.valueOf(result.added().size())),
          MessageArgument.untrusted("updated", String.valueOf(result.updated().size() + result.removed().size())),
          MessageArgument.untrusted("kept", String.valueOf(result.kept().size())));
    }
    for (Map.Entry<String, String> entry : report.failures().entrySet()) {
      message(source, BukkitLanguageMessages.UPDATE_LOCALE_FAILED,
          MessageArgument.untrusted("plugin", PLUGIN),
          MessageArgument.untrusted("locale", entry.getKey()),
          MessageArgument.untrusted("reason", entry.getValue()));
    }
    if (changed.isEmpty() && report.failures().isEmpty()) {
      message(source, BukkitLanguageMessages.UPDATE_UNCHANGED, MessageArgument.untrusted("plugin", PLUGIN));
    }
  }

  private static CommandHelpPort menus() {
    return AdaptPorts.get(CommandHelpPort.class, CommandHelpPort.NONE);
  }

  static String displayName(String locale) {
    String name = AdaptLanguage.displayName(locale);
    return name != null && !name.isBlank() ? name : VolmitLocales.displayName(locale).orElse(locale);
  }

  private static String describe(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }

  private static UUID playerId(CommandSource source) {
    Optional<PlayerRef> player = source.player();
    return player.map(PlayerRef::uuid).orElse(null);
  }

  private static List<String> matching(Collection<String> candidates, String input) {
    String prefix = input.toLowerCase(Locale.ROOT);
    return candidates.stream().filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
  }

  private static boolean sameLocale(String first, String second) {
    return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
  }

  private static void message(CommandSource source, TextKey key, MessageArgument... arguments) {
    MessageArgs.Builder builder = MessageArgs.builder();
    for (MessageArgument argument : arguments) {
      builder.add(argument);
    }
    source.sendMarkup("<" + DESCRIPTION + ">" + escapeText(localized(key, builder.build())) + "</" + DESCRIPTION + ">");
  }

  private static String localized(TextKey key) {
    return localized(key, MessageArgs.empty());
  }

  static String localized(TextKey key, MessageArgs arguments) {
    String rendered;
    try {
      rendered = AdaptLanguage.directorResolver().resolve(key, arguments);
    } catch (RuntimeException exception) {
      rendered = null;
    }
    return rendered == null ? DirectorTextResolver.ENGLISH.resolve(key, arguments) : rendered;
  }

  private static String link(CommandSource source, String label, String command, String hover) {
    if (source.player().isEmpty()) {
      return label + ": " + command;
    }
    return entry("<click:run_command:" + command + "><hover:show_text:'" + escapeAttr(commandHover(label, hover, command)) + "'>"
        + "<gradient:" + PRIMARY_LEFT + ":" + PRIMARY_RIGHT + ">" + escapeText(label) + "</gradient>"
        + "<" + MUTED + "> - </" + MUTED + "><" + DESCRIPTION + ">" + escapeText(hover) + "</" + DESCRIPTION + ">"
        + "</hover></click>");
  }

  private static String languageLink(CommandSource source, String locale, String name, boolean selected, String command) {
    if (source.player().isEmpty()) {
      String prefix = selected ? "[" + localized(BukkitLanguageMessages.SELECTED) + "] " : "";
      return prefix + locale + " - " + name + ": " + command;
    }
    String hover = commandHover(locale + " - " + name, localized(BukkitLanguageMessages.SELECT_DESCRIPTION), command);
    return entry("<click:run_command:" + command + "><hover:show_text:'" + escapeAttr(hover) + "'>"
        + (selected ? "<green>✔</green> " : "<dark_gray>•</dark_gray> ")
        + "<gradient:" + PRIMARY_LEFT + ":" + PRIMARY_RIGHT + ">" + escapeText(locale) + "</gradient>"
        + "<" + MUTED + "> - </" + MUTED + "><" + DESCRIPTION + ">" + escapeText(name) + "</" + DESCRIPTION + ">"
        + "</hover></click>");
  }

  private static String commandHover(String title, String description, String command) {
    return "<" + PRIMARY_RIGHT + ">" + escapeText(title) + "</" + PRIMARY_RIGHT + "><reset>\n"
        + "<" + DESCRIPTION + ">✎ " + escapeText(description) + "</" + DESCRIPTION + "><reset>\n"
        + "<" + OPTIONAL + ">✒ " + escapeText(localized(BukkitLanguageMessages.COMMAND,
        MessageArgs.builder().untrusted("command", command).build())) + "</" + OPTIONAL + ">";
  }

  private static String entry(String content) {
    return "<" + MUTED + ">⇀</" + MUTED + "> " + content;
  }

  private static String styled(String text, String color) {
    return "<" + MUTED + ">⇀ </" + MUTED + "><" + color + ">" + escapeText(text) + "</" + color + ">";
  }

  private static String escapeText(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("<", "\\<");
  }

  private static String escapeAttr(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }
}
