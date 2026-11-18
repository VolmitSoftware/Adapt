package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.command.CommandHelpPort;
import art.arcane.adapt.command.CommandMenu;
import art.arcane.adapt.command.PlayerCommandSource;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.MenuAction;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralValue;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The in-game language message editor ({@code /adapt language server edit}) on core chest menus: languages, then
 * message groups, then messages; a click asks for the new value in chat and saves it through
 * {@link PluginLanguageEditor}. Same layout, texts and rules as the VolmLib Bukkit editor.
 */
public final class LanguageEditorGui {
  static final int PAGE_SIZE = 36;
  private static final int ROWS = 6;
  private static final int NAVIGATION_ROW = 5;
  private static final int BACK = -4;
  private static final int PREVIOUS = -1;
  private static final int SEARCH = 0;
  private static final int NEXT = 1;
  private static final int CLOSE = 4;
  private static final int MAXIMUM_INPUT_LENGTH = 512;
  private static final int PROMPT_TICKS = 1200;
  private static final int PREVIEW_LINES = 6;
  private static final String PLUGIN = "Adapt";
  private static final String COMMAND = "/adapt language server edit";
  private static final String MUTED = "#d8a0a0";
  private static final String DESCRIPTION = "#ffc7c7";
  private static final List<String> PLURAL_FORMS = List.of("zero", "one", "two", "few", "many", "other");
  private static volatile LanguageEditorGui active;

  private final PluginLanguageService languages;
  private final PluginLanguageEditor editor;
  private final Consumer<PlayerRef> back;
  private final AdaptLanguageEditor icons = AdaptLanguageEditor.running();
  private final Map<UUID, View> views = new ConcurrentHashMap<>();
  private final Map<UUID, GuiWindow> windows = new ConcurrentHashMap<>();
  private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
  private final Map<UUID, CompletableFuture<PluginLanguageEditor.Document>> pending = new ConcurrentHashMap<>();
  private volatile boolean closed;

  LanguageEditorGui(PluginLanguageService languages, PluginLanguageEditor.Options options, Consumer<PlayerRef> back) {
    this.languages = Objects.requireNonNull(languages, "languages");
    this.editor = new PluginLanguageEditor(languages, Objects.requireNonNull(options, "options"));
    this.back = Objects.requireNonNull(back, "back");
    active = this;
  }

  void close() {
    closed = true;
    if (active == this) {
      active = null;
    }
    for (CompletableFuture<PluginLanguageEditor.Document> future : pending.values()) {
      future.cancel(true);
    }
    pending.clear();
    prompts.clear();
    views.clear();
    editor.close();
    for (GuiWindow window : List.copyOf(windows.values())) {
      Tasks.runEntity(window.getViewer(), window::close);
    }
    windows.clear();
  }

  void open(PlayerRef player, String locale) {
    Runnable opening = () -> {
      if (!allowed(player)) {
        return;
      }
      cancel(player.uuid());
      if (locale == null) {
        show(player, new View(null, null, null, "", 1, null));
      } else {
        load(player, new View(locale, null, null, "", 1, null));
      }
    };
    if (Tasks.isOwnedByCurrentRegion(player)) {
      opening.run();
    } else {
      Tasks.runEntity(player, opening);
    }
  }

  private void click(PlayerRef player, View view, int position, int row) {
    if (!allowed(player) || views.get(player.uuid()) != view) {
      return;
    }
    if (row == NAVIGATION_ROW) {
      if (position == CLOSE) {
        cancel(player.uuid());
        player.closeInventory();
      } else if (position == BACK) {
        back(player, view);
      } else if (position == PREVIOUS || position == NEXT) {
        show(player, view.withPage(view.page() + (position == NEXT ? 1 : -1)));
      } else if (position == SEARCH && view.searchable()) {
        beginPrompt(player, new Prompt(view, null, null, null));
      } else if (position == SEARCH && view.searching()) {
        show(player, new View(view.locale(), view.document(), view.group(), "", 1, null));
      }
      return;
    }
    selectEntry(player, view, row * 9 + position + 4);
  }

  private void back(PlayerRef player, View view) {
    if (view.key() != null) {
      show(player, new View(view.locale(), view.document(), view.group(), view.filter(),
          Math.max(0, keys(view).indexOf(view.key())) / PAGE_SIZE + 1, null));
    } else if (view.group() != null || view.searching()) {
      show(player, new View(view.locale(), view.document(), null, "", 1, null));
    } else if (view.locale() != null) {
      show(player, new View(null, null, null, "", 1, null));
    } else {
      cancel(player.uuid());
      player.closeInventory();
      back.accept(player);
    }
  }

  private void selectEntry(PlayerRef player, View view, int slot) {
    if (slot < 0 || slot >= PAGE_SIZE) {
      return;
    }
    int index = (view.page() - 1) * PAGE_SIZE + slot;
    if (view.locale() == null) {
      List<String> locales = languages.availableLocales();
      if (index < locales.size()) {
        load(player, new View(locales.get(index), null, null, "", 1, null));
      }
      return;
    }
    if (view.categories()) {
      List<String> groups = groups(view.document());
      if (index < groups.size()) {
        show(player, new View(view.locale(), view.document(), groups.get(index), "", 1, null));
      }
      return;
    }
    if (view.key() != null) {
      List<String> forms = parts(view);
      if (index < forms.size()) {
        beginPrompt(player, new Prompt(view, view.key(), forms.get(index), view.document().snapshot().value(view.key())));
      }
      return;
    }
    List<MessageKey> keys = keys(view);
    if (index >= keys.size()) {
      return;
    }
    MessageKey key = keys.get(index);
    MessageValue value = view.document().snapshot().value(key);
    if (value instanceof PluralValue || value instanceof LinesValue) {
      show(player, new View(view.locale(), view.document(), view.group(), view.filter(), 1, key));
    } else {
      beginPrompt(player, new Prompt(view, key, null, value));
    }
  }

  private void load(PlayerRef player, View view) {
    if (!allowed(player)) {
      return;
    }
    views.remove(player.uuid());
    prompts.remove(player.uuid());
    message(player, BukkitLanguageMessages.EDITOR_LOADING, MessageArgument.untrusted("locale", view.locale()));
    complete(player, view, editor.load(view.locale()), null);
  }

  private void complete(PlayerRef player, View view, CompletableFuture<PluginLanguageEditor.Document> future, Change change) {
    UUID playerId = player.uuid();
    CompletableFuture<PluginLanguageEditor.Document> previous = pending.put(playerId, future);
    if (previous != null) {
      previous.cancel(true);
    }
    future.whenComplete((PluginLanguageEditor.Document document, Throwable failure) -> {
      if (closed) {
        return;
      }
      Runnable retired = () -> pending.remove(playerId, future);
      Tasks.runEntity(player, () -> {
        if (!pending.remove(playerId, future) || !player.isOnline() || !allowed(player)) {
          return;
        }
        if (failure != null) {
          failed(player, failure);
          show(player, view.document() == null ? new View(null, null, null, "", 1, null) : view);
          return;
        }
        if (change != null) {
          saved(player, view, change);
        }
        show(player, new View(document.locale(), document, view.group(), view.filter(), view.page(), view.key()));
      }, retired);
    });
  }

  private void beginPrompt(PlayerRef player, Prompt prompt) {
    prompts.put(player.uuid(), prompt);
    player.closeInventory();
    LanguageAudience.run(player.uuid(), () -> {
      if (prompt.key() == null) {
        promptMenu(player, prompt.view(), List.of(text(BukkitLanguageMessages.EDITOR_SEARCH_PROMPT)));
        return;
      }
      String key = prompt.key().id() + (prompt.form() == null ? "" : " [" + partLabel(prompt.expected(), prompt.form()) + "]");
      promptMenu(player, prompt.view(), List.of(
          text(BukkitLanguageMessages.EDITOR_VALUE_PROMPT, MessageArgument.untrusted("key", key)),
          text(BukkitLanguageMessages.EDITOR_CURRENT_VALUE,
              MessageArgument.untrusted("value", clip(rawValue(prompt.expected(), prompt.form()).replace("\n", "\\n")))),
          text(BukkitLanguageMessages.EDITOR_VARIABLES, MessageArgument.untrusted("variables", variables(prompt.key()))),
          text(BukkitLanguageMessages.EDITOR_INPUT_GUIDANCE)));
    });
    Tasks.runEntity(player, () -> {
      if (prompts.remove(player.uuid(), prompt) && allowed(player)) {
        message(player, BukkitLanguageMessages.EDITOR_INPUT_EXPIRED);
        show(player, prompt.view());
      }
    }, PROMPT_TICKS, () -> retirePrompt(player, prompt));
  }

  private boolean chat(PlayerRef player, String input) {
    Prompt prompt = prompts.get(player.uuid());
    if (prompt == null) {
      return false;
    }
    if (!Tasks.runEntity(player, () -> submit(player, prompt, input), () -> retirePrompt(player, prompt))) {
      retirePrompt(player, prompt);
    }
    return true;
  }

  private void submit(PlayerRef player, Prompt prompt, String input) {
    if (!prompts.remove(player.uuid(), prompt) || views.get(player.uuid()) != prompt.view() || !allowed(player)) {
      return;
    }
    if (input.equalsIgnoreCase("cancel")) {
      show(player, prompt.view());
      return;
    }
    if (input.length() > MAXIMUM_INPUT_LENGTH) {
      message(player, BukkitLanguageMessages.EDITOR_INPUT_TOO_LONG, MessageArgument.trusted("maximum", MAXIMUM_INPUT_LENGTH));
      show(player, prompt.view());
      return;
    }
    View view = prompt.view();
    if (prompt.key() == null) {
      show(player, new View(view.locale(), view.document(), view.group(), input.strip(), 1, null));
      return;
    }
    try {
      MessageValue replacement = replacement(prompt.expected(), prompt.form(), decodeInput(input));
      Change change = new Change(prompt.key().id(), rawValue(prompt.expected(), prompt.form()), rawValue(replacement, prompt.form()));
      complete(player, view, editor.save(new PluginLanguageEditor.Edit(view.locale(), prompt.key().id(),
          prompt.expected(), replacement)), change);
    } catch (IllegalArgumentException exception) {
      message(player, BukkitLanguageMessages.EDITOR_UNABLE_TO_SAVE, MessageArgument.untrusted("reason",
          Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName())));
      show(player, view);
    }
  }

  private void show(PlayerRef player, View requested) {
    if (!allowed(player)) {
      return;
    }
    LanguageAudience.run(player.uuid(), () -> render(player, requested));
  }

  private void render(PlayerRef player, View requested) {
    List<String> locales = requested.locale() == null ? languages.availableLocales() : List.of();
    List<String> groups = requested.categories() ? groups(requested.document()) : List.of();
    List<MessageKey> keys = requested.document() != null && !requested.categories() && requested.key() == null
        ? keys(requested) : List.of();
    List<String> parts = requested.key() == null ? List.of() : parts(requested);
    int count = requested.locale() == null ? locales.size()
        : requested.categories() ? groups.size()
        : requested.key() == null ? keys.size() : parts.size();
    Page page = Page.of(count, requested.page(), PAGE_SIZE);
    View view = requested.withPage(page.page());
    GuiWindow window = new GuiWindow(player)
        .setTag("adapt/language-editor")
        .setViewportHeight(ROWS)
        .setTitle(title(view))
        .setDecorator((GuiWindow ignored, int position, int row) -> new GuiElement("filler")
            .setMaterial(Id.minecraft("black_stained_glass_pane")).setName(" "));
    for (int index = page.start(); index < page.end(); index++) {
      GuiElement entry;
      if (view.locale() == null) {
        String locale = locales.get(index);
        entry = localeItem(locale, locale.equalsIgnoreCase(languages.defaultLocale()));
      } else if (view.categories()) {
        entry = categoryItem(view.document(), groups.get(index));
      } else if (view.key() == null) {
        MessageKey key = keys.get(index);
        entry = messageItem(key, view.document().snapshot().value(key), null);
      } else {
        entry = messageItem(view.key(), view.document().snapshot().value(view.key()), parts.get(index));
      }
      int slot = index - page.start();
      place(window, player, view, slot % 9 - 4, slot / 9, entry);
    }
    place(window, player, view, BACK, NAVIGATION_ROW, button("arrow", C.YELLOW + text(BukkitLanguageMessages.EDITOR_BACK)));
    place(window, player, view, CLOSE, NAVIGATION_ROW, button("barrier", C.RED + text(BukkitLanguageMessages.EDITOR_CLOSE)));
    if (page.hasPrevious()) {
      place(window, player, view, PREVIOUS, NAVIGATION_ROW,
          button("arrow", C.YELLOW + text(BukkitLanguageMessages.EDITOR_PREVIOUS_PAGE)));
    }
    if (page.hasNext()) {
      place(window, player, view, NEXT, NAVIGATION_ROW,
          button("arrow", C.YELLOW + text(BukkitLanguageMessages.EDITOR_NEXT_PAGE)));
    }
    if (view.searchable()) {
      place(window, player, view, SEARCH, NAVIGATION_ROW,
          button("compass", C.RED + text(BukkitLanguageMessages.EDITOR_SEARCH_MESSAGES))
              .addLore(C.GRAY + text(BukkitLanguageMessages.EDITOR_SEARCH_MESSAGES_LORE)));
    } else if (view.searching()) {
      place(window, player, view, SEARCH, NAVIGATION_ROW, button("paper", C.RED + text(BukkitLanguageMessages.EDITOR_CLEAR_SEARCH)));
    }
    window.onClosed((GuiWindow closedWindow) -> windowClosed(player.uuid(), closedWindow));
    views.put(player.uuid(), view);
    windows.put(player.uuid(), window);
    window.open();
  }

  private void place(GuiWindow window, PlayerRef player, View view, int position, int row, GuiElement element) {
    MenuAction action = (MenuClick click) -> click(player, view, position, row);
    window.setElement(position, row, element.onLeftClick(action).onRightClick(action).onShiftLeftClick(action));
  }

  private void windowClosed(UUID playerId, GuiWindow window) {
    if (!windows.remove(playerId, window) || prompts.containsKey(playerId)) {
      return;
    }
    if (pending.containsKey(playerId)) {
      cancel(playerId);
    } else {
      views.remove(playerId);
    }
  }

  private String title(View view) {
    String section;
    if (view.locale() == null) {
      section = text(BukkitLanguageMessages.EDITOR_LANGUAGES);
    } else if (view.group() != null) {
      section = text(BukkitLanguageMessages.EDITOR_SECTION_GROUP, MessageArgument.untrusted("locale", view.locale()),
          MessageArgument.untrusted("group", groupName(view.group())));
    } else if (view.searching()) {
      section = text(BukkitLanguageMessages.EDITOR_SECTION_SEARCH, MessageArgument.untrusted("locale", view.locale()));
    } else {
      section = view.locale();
    }
    return text(BukkitLanguageMessages.EDITOR_TITLE, MessageArgument.untrusted("plugin", PLUGIN),
        MessageArgument.untrusted("section", section));
  }

  private GuiElement messageItem(MessageKey key, MessageValue value, String form) {
    GuiElement element = new GuiElement("message")
        .setName(C.WHITE + (form == null ? key.id() : partLabel(value, form)))
        .addLore(C.GRAY + text(BukkitLanguageMessages.EDITOR_CURRENT_VALUE_LABEL));
    element.addLore(preview(rawValue(value, form)));
    if (!key.placeholders().isEmpty()) {
      element.addLore(" ");
      element.addLore(C.DARK_GRAY + variableNames(key));
    }
    TextKey instruction = form == null && value instanceof PluralValue ? BukkitLanguageMessages.EDITOR_EDIT_PLURAL
        : form == null && value instanceof LinesValue ? BukkitLanguageMessages.EDITOR_EDIT_LINES
        : BukkitLanguageMessages.EDITOR_EDIT_CHAT;
    element.addLore(C.GRAY + text(instruction));
    return withIcon(element, key.id(), "paper");
  }

  private GuiElement localeItem(String locale, boolean active) {
    String marker = active ? C.GREEN + "✔ " : C.DARK_GRAY + "• ";
    return new GuiElement("locale")
        .setMaterial(Id.minecraft(active ? "writable_book" : "book"))
        .setName(marker + C.WHITE + locale + C.DARK_GRAY + " — " + C.GRAY + AdaptLanguageSwitcher.displayName(locale))
        .addLore(C.DARK_GRAY + text(BukkitLanguageMessages.EDITOR_OPEN_LANGUAGE));
  }

  private GuiElement categoryItem(PluginLanguageEditor.Document document, String group) {
    GuiElement element = new GuiElement("category")
        .setName(C.LIGHT_PURPLE + groupName(group))
        .addLore(C.GRAY + text(BukkitLanguageMessages.EDITOR_MESSAGE_COUNT, MessageArgument.trusted("count", messageCount(document, group))))
        .addLore(C.DARK_GRAY + text(BukkitLanguageMessages.EDITOR_OPEN_CATEGORY));
    return withIcon(element, group, groupMaterial(group));
  }

  private GuiElement withIcon(GuiElement element, String messageId, String fallback) {
    Optional<ItemRef> icon = Optional.empty();
    try {
      icon = icons.icon(messageId);
    } catch (RuntimeException failure) {
      AdaptLog.verbose("Language editor icon failed for " + messageId + ": " + failure.getMessage());
    }
    if (icon.isPresent() && !icon.get().isEmpty()) {
      return element.setItem(icon.get());
    }
    return element.setMaterial(Id.minecraft(fallback));
  }

  private static GuiElement button(String material, String name) {
    return new GuiElement("navigation").setMaterial(Id.minecraft(material)).setName(name);
  }

  private List<String> preview(String raw) {
    String rendered = MarkupPort.current().legacy(raw);
    if (C.stripColor(rendered).isBlank()) {
      return List.of(C.GRAY + text(BukkitLanguageMessages.EDITOR_EMPTY));
    }
    List<String> lines = new ArrayList<>();
    String color = C.GRAY.toString();
    for (String line : rendered.split("\n", -1)) {
      if (lines.size() == PREVIEW_LINES) {
        lines.set(PREVIEW_LINES - 1, lines.get(PREVIEW_LINES - 1) + C.DARK_GRAY + "...");
        break;
      }
      lines.add(color + line);
      String last = C.getLastColors(color + line);
      color = last.isEmpty() ? color : last;
    }
    return lines;
  }

  private boolean allowed(PlayerRef player) {
    if (closed) {
      return false;
    }
    if (player.hasPermission(AdaptLanguageSwitcher.ADMIN_PERMISSION)
        || player.hasPermission(AdaptLanguageSwitcher.PLUGIN_ADMIN_PERMISSION)) {
      return true;
    }
    cancel(player.uuid());
    message(player, BukkitLanguageMessages.EDITOR_NO_PERMISSION, MessageArgument.untrusted("plugin", PLUGIN));
    player.closeInventory();
    return false;
  }

  private void cancel(UUID playerId) {
    prompts.remove(playerId);
    views.remove(playerId);
    CompletableFuture<PluginLanguageEditor.Document> future = pending.remove(playerId);
    if (future != null) {
      future.cancel(true);
    }
  }

  private void retirePrompt(PlayerRef player, Prompt prompt) {
    if (prompts.remove(player.uuid(), prompt)) {
      views.remove(player.uuid(), prompt.view());
    }
  }

  private void failed(PlayerRef player, Throwable failure) {
    Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    AdaptLog.warn("Unable to edit Adapt language messages: " + cause);
    message(player, BukkitLanguageMessages.EDITOR_UNABLE_TO_EDIT, MessageArgument.untrusted("reason",
        Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName())));
  }

  private void saved(PlayerRef player, View view, Change change) {
    LanguageAudience.run(player.uuid(), () -> promptMenu(player, view, List.of(
        text(BukkitLanguageMessages.EDITOR_SAVED, MessageArgument.untrusted("locale", view.locale())),
        text(BukkitLanguageMessages.EDITOR_CHANGED, MessageArgument.untrusted("key", change.key()),
            MessageArgument.untrusted("before", clip(change.before())),
            MessageArgument.untrusted("after", clip(change.after()))))));
  }

  private static void promptMenu(PlayerRef player, View view, List<String> lines) {
    List<String> entries = new ArrayList<>(lines.size());
    for (String line : lines) {
      entries.add("<" + MUTED + ">⇀ </" + MUTED + "><" + DESCRIPTION + ">" + escape(line) + "</" + DESCRIPTION + ">");
    }
    String command = COMMAND + " " + view.locale();
    AdaptPorts.get(CommandHelpPort.class, CommandHelpPort.NONE).menu(new PlayerCommandSource(player),
        new CommandMenu(command, COMMAND, command, entries, "", 1, Math.max(1, entries.size())));
  }

  private static void message(PlayerRef player, TextKey key, MessageArgument... arguments) {
    LanguageAudience.run(player.uuid(), () -> player.sendMessage(
        "<" + DESCRIPTION + ">" + escape(text(key, arguments)) + "</" + DESCRIPTION + ">"));
  }

  private static String text(TextKey key, MessageArgument... arguments) {
    MessageArgs.Builder builder = MessageArgs.builder();
    for (MessageArgument argument : arguments) {
      builder.add(argument);
    }
    return MarkupPort.current().plain(AdaptLanguageSwitcher.localized(key, builder.build()));
  }

  private String partLabel(MessageValue value, String part) {
    return value instanceof LinesValue
        ? text(BukkitLanguageMessages.EDITOR_LINE, MessageArgument.trusted("line", Integer.parseInt(part) + 1))
        : part;
  }

  private static String variables(MessageKey key) {
    String names = variableNames(key);
    return names.isEmpty() ? text(BukkitLanguageMessages.EDITOR_NONE) : names;
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("<", "\\<");
  }

  private static String clip(String value) {
    return value.length() <= MAXIMUM_INPUT_LENGTH ? value : value.substring(0, MAXIMUM_INPUT_LENGTH) + "...";
  }

  private static List<MessageKey> keys(View view) {
    return matchingKeys(view.document(), view.group(), view.filter());
  }

  private static List<String> parts(View view) {
    return parts(view.document().snapshot().value(view.key()));
  }

  static List<String> parts(MessageValue value) {
    if (value instanceof LinesValue lines) {
      List<String> indices = new ArrayList<>(lines.lines().size());
      for (int index = 0; index < lines.lines().size(); index++) {
        indices.add(Integer.toString(index));
      }
      return indices;
    }
    PluralValue plural = (PluralValue) value;
    return PLURAL_FORMS.stream().filter(plural.forms()::containsKey).toList();
  }

  static List<MessageKey> matchingKeys(PluginLanguageEditor.Document document, String group, String filter) {
    String query = filter.toLowerCase(Locale.ROOT);
    List<MessageKey> keys = new ArrayList<>();
    for (MessageKey key : document.snapshot().catalog().keys()) {
      if (group != null && !group(key.id()).equals(group)) {
        continue;
      }
      if (query.isEmpty() || key.id().toLowerCase(Locale.ROOT).contains(query)
          || rawValue(document.snapshot().value(key), null).toLowerCase(Locale.ROOT).contains(query)) {
        keys.add(key);
      }
    }
    keys.sort(Comparator.comparing(MessageKey::id));
    return keys;
  }

  static List<String> groups(PluginLanguageEditor.Document document) {
    Set<String> groups = new LinkedHashSet<>();
    for (MessageKey key : document.snapshot().catalog().keys()) {
      groups.add(group(key.id()));
    }
    List<String> sorted = new ArrayList<>(groups);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    return List.copyOf(sorted);
  }

  static String group(String messageId) {
    int separator = messageId.indexOf('.');
    return separator < 0 ? messageId : messageId.substring(0, separator);
  }

  static String groupName(String group) {
    if (group.equalsIgnoreCase("gui") || group.equalsIgnoreCase("hud") || group.equalsIgnoreCase("api")) {
      return group.toUpperCase(Locale.ROOT);
    }
    String normalized = group.replace('_', ' ').replace('-', ' ').trim();
    return normalized.isEmpty() ? group : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }

  private static int messageCount(PluginLanguageEditor.Document document, String group) {
    int count = 0;
    for (MessageKey key : document.snapshot().catalog().keys()) {
      if (group(key.id()).equals(group)) {
        count++;
      }
    }
    return count;
  }

  private static String groupMaterial(String group) {
    return switch (group.toLowerCase(Locale.ROOT)) {
      case "command" -> "command_block";
      case "config", "configuration" -> "comparator";
      case "debug", "diagnostics" -> "spyglass";
      case "director", "help" -> "writable_book";
      case "gui", "menu" -> "chest";
      case "hud", "presentation" -> "name_tag";
      case "integration" -> "ender_chest";
      case "runtime", "system" -> "redstone_torch";
      default -> "paper";
    };
  }

  static String variableNames(MessageKey key) {
    return String.join(" ", key.placeholders().stream().sorted().map((String name) -> "{" + name + "}").toList());
  }

  static MessageValue replacement(MessageValue current, String form, String value) {
    if (current instanceof TextValue) {
      return new TextValue(value);
    }
    if (current instanceof LinesValue lines) {
      List<String> updated = new ArrayList<>(lines.lines());
      updated.set(Integer.parseInt(Objects.requireNonNull(form, "line")), value);
      return new LinesValue(updated);
    }
    PluralValue plural = (PluralValue) current;
    Map<String, String> forms = new LinkedHashMap<>(plural.forms());
    forms.put(Objects.requireNonNull(form, "plural form"), value);
    return new PluralValue(forms);
  }

  static String rawValue(MessageValue value, String form) {
    if (value instanceof TextValue text) {
      return text.template();
    }
    if (value instanceof LinesValue lines) {
      return form == null ? String.join("\n", lines.lines()) : lines.lines().get(Integer.parseInt(form));
    }
    PluralValue plural = (PluralValue) value;
    if (form != null) {
      return plural.forms().getOrDefault(form, plural.forms().get("other"));
    }
    return String.join("\n", plural.forms().entrySet().stream()
        .map((Map.Entry<String, String> entry) -> entry.getKey() + ": " + entry.getValue()).toList());
  }

  static String decodeInput(String input) {
    StringBuilder result = new StringBuilder(input.length());
    for (int index = 0; index < input.length(); index++) {
      char current = input.charAt(index);
      if (current == '\\' && index + 1 < input.length()) {
        char next = input.charAt(index + 1);
        if (next == 'n' || next == '\\') {
          result.append(next == 'n' ? '\n' : '\\');
          index++;
          continue;
        }
      }
      result.append(current);
    }
    return result.toString();
  }

  /** Chat typed for an open editor prompt is consumed before any other listener sees it. */
  public static final class Input {
    @Handle(priority = EventPriority.LOWEST)
    public void onChat(PlayerChatEvent event) {
      LanguageEditorGui current = active;
      if (current != null && current.chat(event.player(), event.message())) {
        event.setCancelled(true);
      }
    }

    @Handle
    public void onQuit(PlayerQuitEvent event) {
      LanguageEditorGui current = active;
      if (current != null) {
        current.cancel(event.player().uuid());
        current.windows.remove(event.player().uuid());
      }
    }
  }

  record Page(int page, int start, int end, boolean hasPrevious, boolean hasNext) {
    static Page of(int count, int requested, int size) {
      int pages = Math.max(1, (count + size - 1) / size);
      int page = Math.max(1, Math.min(requested, pages));
      int start = (page - 1) * size;
      return new Page(page, start, Math.min(count, start + size), page > 1, page < pages);
    }
  }

  private record View(String locale, PluginLanguageEditor.Document document, String group, String filter, int page,
                      MessageKey key) {
    private boolean categories() {
      return document != null && group == null && filter.isEmpty();
    }

    private boolean searchable() {
      return categories() && key == null;
    }

    private boolean searching() {
      return document != null && group == null && key == null && !filter.isEmpty();
    }

    private View withPage(int page) {
      return new View(locale, document, group, filter, page, key);
    }
  }

  private record Prompt(View view, MessageKey key, String form, MessageValue expected) {
  }

  private record Change(String key, String before, String after) {
  }
}
