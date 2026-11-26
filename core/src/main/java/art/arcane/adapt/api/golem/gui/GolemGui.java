package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.inventorygui.GuiConfirm;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;

import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemEquipmentSlot;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemPacking;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.GolemHelpMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class GolemGui {
  private static final int ROWS = 5;
  private static final int HEADER_ROW = 0;
  private static final int RUNE_ROW = 1;
  private static final int CARRIED_ROW = 2;
  private static final int EQUIPMENT_ROW = 2;
  private static final int FOOTER_ROW = 4;
  private static final int LEFT_EDGE = -4;
  private static final int RIGHT_EDGE = 4;
  private static final int CHANNELS_PER_ROW = 8;
  private static final int ESSENCE_SLOT = 2;
  private static final int PRIMARY_CORE_SLOT = LEFT_EDGE + 1;
  private static final int SECOND_CORE_SLOT = LEFT_EDGE + 2;
  private static final int MASTERWORK_SLOT = LEFT_EDGE + 2;
  private static final int COMBAT_SLOT = -1;

  private final PlayerRef player;
  private final GolemHandle handle;
  private final CrookGestures<GolemHandle> gestures;
  private final LiveRefresh<GolemView> live;
  private GuiWindow window;
  private Page page = Page.MAIN;
  private Page renderedPage;
  private GolemView shown;
  private List<Carried> renderedCarried = List.of();
  private int renderedHidden;

  private GolemGui(PlayerRef player, GolemHandle handle, CrookGestures<GolemHandle> gestures) {
    this.player = player;
    this.handle = handle;
    this.gestures = gestures;
    this.live = new LiveRefresh<>(new LiveRefresh.Hooks<>(
        (task, delay, retired) -> Tasks.runEntity(handle.entity(), task, delay, retired),
        (task, delay, retired) -> Tasks.runEntity(player, task, delay, retired),
        this::read, this::render, this::gone, this::isOpen));
  }

  public static void open(PlayerRef player, GolemHandle handle, CrookGestures<GolemHandle> gestures) {
    new GolemGui(player, handle, gestures).refresh();
  }

  private void refresh() {
    Tasks.runEntity(handle.entity(), this::snapshot, this::gone);
  }

  private void snapshot() {
    Optional<GolemView> view = read();
    if (view.isEmpty()) {
      gone();
      return;
    }
    Tasks.runEntity(player, () -> render(view.get()));
  }

  private Optional<GolemView> read() {
    if (handle.isRetired() || !handle.entity().isValid()) {
      return Optional.empty();
    }
    return Optional.of(GolemView.of(handle));
  }

  private boolean isOpen() {
    return player.isOnline() && window != null && window.isVisible();
  }

  private void gone() {
    live.stop();
    GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    Tasks.runEntity(player, () -> {
      if (window != null && window.isVisible()) {
        window.close();
      }
    });
  }

  private void render(GolemView view) {
    if (!player.isOnline() || (window != null && !window.isVisible())) {
      return;
    }
    GolemView before = shown;
    boolean initial = window == null || renderedPage != page;
    if (!initial && view.equals(before)) {
      return;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      if (initial) {
        draw(view);
      } else if (page == Page.CHANNELS) {
        if (!view.channels().equals(before.channels())) {
          draw(view);
        }
      } else {
        update(view, before);
      }
    }
    shown = view;
    live.start();
  }

  private void draw(GolemView view) {
    GuiWindow next = new GuiWindow(player);
    GuiTheme.apply(next, "golemancy-golem");
    next.setViewportHeight(ROWS);
    next.setFreePlayerInventory(true);
    Placer target = (int position, int row, GuiElement element) -> {
      if (element != null) {
        next.setElement(position, row, element);
      }
    };
    renderedCarried = List.of();
    renderedHidden = 0;
    if (page == Page.CHANNELS) {
      placeChannelPicker(target, view);
    } else {
      for (GolemView.Section section : GolemView.Section.values()) {
        place(target, section, view, null);
      }
      placeActions(target, view);
    }
    String golemTitle = AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(view.body().displayName())));
    next.setTitle(page == Page.CHANNELS ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_TITLE) : golemTitle);
    next.setLayout(page == Page.CHANNELS ? MenuLayout.FORM : MenuLayout.GRID);
    if (page == Page.CHANNELS) {
      next.setBreadcrumb(List.of(golemTitle));
    }
    renderedPage = page;
    window = next;
    next.onClosed(this::closed);
    next.open();
  }

  private void update(GolemView view, GolemView before) {
    GuiWindow target = window;
    for (GolemView.Section section : view.changes(before)) {
      place(target::updateElement, section, view, before);
    }
  }

  private void closed(GuiWindow closed) {
    if (closed == window) {
      live.stop();
    }
  }

  private void place(Placer target, GolemView.Section section, GolemView view, GolemView before) {
    switch (section) {
      case BODY -> target.place(LEFT_EDGE, HEADER_ROW, bodyElement(view));
      case CORES -> placeCores(target, view);
      case MODE -> target.place(0, HEADER_ROW, modeElement(view));
      case CHANNELS -> target.place(1, HEADER_ROW, channelElement(view));
      case ANCHORS -> placeAnchors(target, view);
      case RUNES -> placeRunes(target, view, before);
      case CARRIED -> placeCarried(target, view);
      case STATS -> target.place(LEFT_EDGE, FOOTER_ROW, statsElement(view));
      case ESSENCE -> target.place(ESSENCE_SLOT, FOOTER_ROW, essenceElement(view));
      case TOGGLES -> placeToggles(target, view);
      case EQUIPMENT -> placeEquipment(target, view, before);
    }
  }

  private GuiElement bodyElement(GolemView view) {
    GuiElement body = GuiTheme.element("golem-body", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "body")
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(view.body().displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_HEALTH,
            trusted("health", view.vitals().health()),
            trusted("maximum", view.vitals().maxHealth())))
        .setRole(MenuRole.INFO)
        .setValue(view.vitals().health() + "/" + view.vitals().maxHealth());
    if (!view.vitals().workStatus().isEmpty()) {
      body.addLore(C.YELLOW + view.vitals().workStatus());
    }
    if (view.vitals().dormant()) {
      body.addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.GUI_DORMANT));
    }
    return body;
  }

  private void placeCores(Placer target, GolemView view) {
    List<GolemCoreType> cores = view.cores().slotted();
    target.place(PRIMARY_CORE_SLOT, HEADER_ROW, itemElement("golem-core-0", GolemItems.core(cores.getFirst()))
        .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.CORE_FIXED))
        .setRole(MenuRole.INFO));
    target.place(SECOND_CORE_SLOT, HEADER_ROW, GolemGuiActions.showsSecondCore(cores, view.cores().dualCore()) ? secondCoreElement(view) : null);
  }

  private GuiElement modeElement(GolemView view) {
    String help = C.GRAY + AdaptLanguage.text(switch (view.mode()) {
      case WORK -> GolemHelpMessages.MODE_WORK;
      case FOLLOW -> GolemHelpMessages.MODE_FOLLOW;
      case STAY -> GolemHelpMessages.MODE_STAY;
      case HOME -> GolemHelpMessages.MODE_HOME;
    });
    List<String> modes = new ArrayList<>();
    for (GolemMode mode : GolemMode.selectableFor(view.cores().slotted())) {
      modes.add(AdaptLanguage.text(mode.displayName()));
    }
    String current = AdaptLanguage.text(view.mode().displayName());
    return GuiTheme.element("golem-mode", GolemGuiActions.modeIcon(view.mode()), "gui", "golemancy", "mode")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_MODE, trusted("mode", current)))
        .addLore(help)
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
        .onLeftClick(clicked -> cycleMode(true))
        .onRightClick(clicked -> cycleMode(false))
        .setHint(MenuHint.choice(current, modes, null))
        .setValue(current)
        .setDescription(List.of(help))
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GuiMessages.NEXT))
        .setActionLabel(ClickKind.RIGHT, AdaptLanguage.text(GuiMessages.PREVIOUS));
  }

  private void placeAnchors(Placer target, GolemView view) {
    target.place(RIGHT_EDGE - 1, HEADER_ROW, GuiTheme.element("golem-home", Id.minecraft("compass"), "gui", "golemancy", "home")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_SET_HOME))
        .addLore(C.GRAY + coordinates(GolemancyMessages.GUI_HOME, view.anchors().home()))
        .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.HOME_REACH, trusted("value", view.anchors().workReach())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.HOME_LEASH, trusted("value", view.anchors().leash())))
        .onLeftClick(clicked -> setHome())
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_SET_HOME)));
    if (view.body() == GolemBody.BONE) {
      target.place(RIGHT_EDGE, HEADER_ROW, null);
      return;
    }
    GolemAnchor homeChest = view.anchors().chest();
    GuiElement chest = GuiTheme.element("golem-chest", Id.minecraft("chest"), "gui", "golemancy", "chest")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_LINK_CHEST))
        .addLore(C.GRAY + (homeChest == null
            ? AdaptLanguage.text(GolemancyMessages.GUI_NO_HOME_CHEST)
            : coordinates(GolemancyMessages.GUI_HOME_CHEST, homeChest)));
    String tools = toolLine(view.anchors());
    if (tools != null) {
      chest.addLore(tools);
    }
    List<String> description = List.copyOf(chest.getLore());
    target.place(RIGHT_EDGE, HEADER_ROW, chest
        .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.GUI_LINK_HINT))
        .onLeftClick(clicked -> armChestLink())
        .setRole(MenuRole.ACTION)
        .setDescription(description)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_LINK_HINT)));
  }

  private String toolLine(GolemView.Anchors anchors) {
    if (anchors.toolNote() != null) {
      return C.RED + anchors.toolNote().text();
    }
    if (anchors.held() == null) {
      return null;
    }
    try {
      Id type = GolemState.decodeItem(anchors.held()).type();
      return C.GRAY + AdaptLanguage.text(GolemancyMessages.TOOLS_HOLDING,
          trusted("tool", Form.capitalizeWords(type.path().toLowerCase(Locale.ROOT).replace('_', ' '))));
    } catch (RuntimeException unreadable) {
      AdaptLog.warn("Skipping the unreadable held tool of golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
      return null;
    }
  }

  private GuiElement secondCoreElement(GolemView view) {
    if (view.cores().slotted().size() > 1) {
      GolemCoreType core = view.cores().slotted().get(1);
      return itemElement("golem-core-1", GolemItems.core(core))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_REMOVE))
          .onLeftClick(clicked -> takeCore(core))
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_REMOVE));
    }
    return GuiTheme.element("golem-core-1", Id.minecraft("orange_stained_glass_pane"), "gui", "golemancy", "slots", "core_empty")
        .setName(C.GOLD + AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_EMPTY))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_EMPTY_LORE))
        .onLeftClick(this::slotCoreFromCursor)
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_EMPTY_LORE));
  }

  private GuiElement masterworkElement(GolemView view) {
    if (view.toggles().masterwork()) {
      return itemElement("golem-masterwork", GolemItems.masterworkHeart())
          .setName(C.GOLD + AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT_LORE))
          .setEnchanted(true)
          .onLeftClick(clicked -> undesignate())
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT_LORE));
    }
    String instruction = AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT_EMPTY_LORE,
        trusted("count", view.toggles().masterworks()), trusted("maximum", view.toggles().maxMasterworks()));
    return GuiTheme.element("golem-masterwork", Id.minecraft("yellow_stained_glass_pane"), "gui", "golemancy", "masterwork_empty")
        .setName(C.GOLD + AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT_EMPTY))
        .addLore(C.GRAY + instruction)
        .onLeftClick(this::designateFromCursor)
        .setRole(MenuRole.ACTION)
        .setValue(view.toggles().masterworks() + "/" + view.toggles().maxMasterworks())
        .setActionLabel(ClickKind.LEFT, instruction);
  }

  private GuiElement channelElement(GolemView view) {
    int mask = view.channels().mask();
    return GolemGuiActions.channelElement("golem-channel", mask)
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS, trusted("channels", channelList(mask))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_LORE, trusted("count", view.channels().slots())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.CHANNEL_PICK))
        .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.CHANNEL_MATCH))
        .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.CHANNEL_NONE))
        .onLeftClick(clicked -> show(Page.CHANNELS))
        .setRole(MenuRole.NAVIGATION)
        .setValue(channelList(mask))
        .setDescription(List.of(
            C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_LORE, trusted("count", view.channels().slots())),
            C.GRAY + AdaptLanguage.text(GolemHelpMessages.CHANNEL_MATCH),
            C.GRAY + AdaptLanguage.text(GolemHelpMessages.CHANNEL_NONE)))
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemHelpMessages.CHANNEL_PICK));
  }

  private void placeChannelPicker(Placer target, GolemView view) {
    int mask = view.channels().mask();
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      int colorMask = ChannelMask.of(color);
      boolean selected = ChannelMask.contains(mask, color);
      target.place(GuiLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), index / CHANNELS_PER_ROW,
          GolemGuiActions.channelElement("golem-channel-" + color.name().toLowerCase(Locale.ROOT), colorMask)
              .setName((selected ? C.GREEN : C.YELLOW) + AdaptLanguage.text(GolemGuiActions.channelName(colorMask)))
              .addLore(selected ? C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT))
              .setEnchanted(selected)
              .onLeftClick(clicked -> toggleChannel(color))
              .setHint(MenuHint.toggle(selected))
              .setDescription(List.of()));
    }
    target.place(LEFT_EDGE, FOOTER_ROW, GuiTheme.element("golem-channels-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
        .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_BACK))
        .onLeftClick(clicked -> show(Page.MAIN)));
    target.place(0, FOOTER_ROW, GolemGuiActions.channelElement("golem-channels-summary", mask)
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS, trusted("channels", channelList(mask))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_LORE, trusted("count", view.channels().slots())))
        .setRole(MenuRole.INFO));
    target.place(RIGHT_EDGE, FOOTER_ROW, GuiTheme.element("golem-channels-clear", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_CLEAR))
        .onLeftClick(clicked -> mutate(() -> handle.setChannels(ChannelMask.NONE)))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_CLEAR)));
  }

  private void placeRunes(Placer target, GolemView view, GolemView before) {
    int slots = runeSlots(view);
    if (before != null) {
      int previous = runeSlots(before);
      for (int index = 0; index < previous; index++) {
        int position = GuiLayout.centeredPosition(index, previous);
        if (!occupies(position, slots)) {
          target.place(position, RUNE_ROW, null);
        }
      }
    }
    for (int index = 0; index < slots; index++) {
      target.place(GuiLayout.centeredPosition(index, slots), RUNE_ROW, runeElement(view, index));
    }
  }

  private static int runeSlots(GolemView view) {
    return Math.max(view.runes().layout().maxSlots(), view.runes().slotted().size());
  }

  private static boolean occupies(int position, int slots) {
    for (int index = 0; index < slots; index++) {
      if (GuiLayout.centeredPosition(index, slots) == position) {
        return true;
      }
    }
    return false;
  }

  private GuiElement runeElement(GolemView view, int index) {
    List<GolemRune> runes = view.runes().slotted();
    GolemRuneLayout layout = view.runes().layout();
    if (index < runes.size()) {
      GolemRune rune = runes.get(index);
      ItemRef item = GolemItems.rune(rune);
      GolemGuiActions.RuneNote note = GolemGuiActions.runeNote(layout, runes, index, view.body());
      List<String> lore = new ArrayList<>();
      lore.add(C.GRAY + AdaptLanguage.text(GolemancyMessages.RUNE_TIER, trusted("tier", rune.tier())));
      lore.add((note.active() ? C.GREEN : C.RED) + note.notice().text());
      if (note.active()) {
        for (String line : GolemRunes.describe(rune, view.runes().perks())) {
          lore.add(C.GRAY + line);
        }
      }
      List<String> description = List.copyOf(lore);
      lore.add(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_REMOVE));
      item.setLore(lore);
      return itemElement("golem-rune-" + index, item).onLeftClick(clicked -> takeRune(index, rune))
          .setRole(MenuRole.ACTION)
          .setDescription(description)
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_RUNE_REMOVE));
    }
    if (index < layout.unlockedSlots()) {
      return GuiTheme.element("golem-rune-" + index, Id.minecraft("magenta_stained_glass_pane"), "gui", "golemancy", "slots", "rune_empty")
          .setName(C.LIGHT_PURPLE + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_EMPTY))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_EMPTY_LORE))
          .onLeftClick(this::slotFromCursor)
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_RUNE_EMPTY_LORE));
    }
    return GuiTheme.element("golem-rune-" + index, Id.minecraft("black_stained_glass_pane"), "gui", "golemancy", "slots", "rune_locked")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_LOCKED))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_UNLOCK_LEVEL, trusted("level", layout.slotUnlockLevel(index))))
        .setRole(MenuRole.INFO);
  }

  private void placeCarried(Placer target, GolemView view) {
    List<Carried> entries = readCarried(view);
    int hidden = view.carried().total() - entries.size();
    int cells = Math.max(cellCount(entries, hidden), cellCount(renderedCarried, renderedHidden));
    for (int index = 0; index < cells; index++) {
      if (!Objects.equals(carriedKey(entries, hidden, index), carriedKey(renderedCarried, renderedHidden, index))) {
        target.place(LEFT_EDGE + index % GuiLayout.WIDTH, CARRIED_ROW + index / GuiLayout.WIDTH, carriedCell(entries, hidden, index));
      }
    }
    renderedCarried = entries;
    renderedHidden = hidden;
  }

  private List<Carried> readCarried(GolemView view) {
    List<String> stored = view.carried().shown();
    List<Carried> entries = new ArrayList<>(stored.size());
    for (String encoded : stored) {
      try {
        entries.add(new Carried(encoded, GolemState.decodeItem(encoded)));
      } catch (RuntimeException unreadable) {
        AdaptLog.warn("Skipping unreadable carried item on golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
      }
    }
    return List.copyOf(entries);
  }

  private static int cellCount(List<Carried> entries, int hidden) {
    return entries.size() + (hidden > 0 ? 1 : 0);
  }

  private static Object carriedKey(List<Carried> entries, int hidden, int index) {
    if (index < entries.size()) {
      return entries.get(index).encoded();
    }
    return index == entries.size() && hidden > 0 ? Integer.valueOf(hidden) : null;
  }

  private GuiElement carriedCell(List<Carried> entries, int hidden, int index) {
    if (index < entries.size()) {
      Carried entry = entries.get(index);
      ItemRef item = entry.item();
      GuiElement element = itemElement("golem-carried-" + index, item.copy()).setCount(item.amount());
      for (String line : item.lore()) {
        element.addLore(line);
      }
      return takeable(element.setDescription(element.getLore()))
          .onLeftClick(clicked -> take(entry))
          .onShiftLeftClick(clicked -> take(entry));
    }
    if (index == entries.size() && hidden > 0) {
      return GuiTheme.element("golem-carried-more", Id.minecraft("paper"), "gui", "golemancy", "carried_more")
          .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_MORE_CARRIED, trusted("count", hidden)))
          .setRole(MenuRole.INFO);
    }
    return null;
  }

  private void placeEquipment(Placer target, GolemView view, GolemView before) {
    if (view.body() != GolemBody.BONE) {
      return;
    }
    int slots = GolemEquipmentSlot.values().length;
    for (GolemEquipmentSlot slot : GolemEquipmentSlot.values()) {
      String worn = view.equipment().worn().get(slot);
      if (before == null || !Objects.equals(worn, before.equipment().worn().get(slot))) {
        target.place(GuiLayout.centeredPosition(slot.ordinal(), slots), EQUIPMENT_ROW, equipmentElement(slot, worn));
      }
    }
  }

  private GuiElement equipmentElement(GolemEquipmentSlot slot, String encoded) {
    if (encoded != null) {
      try {
        ItemRef item = GolemState.decodeItem(encoded);
        GuiElement element = itemElement("golem-equipment-" + slot.id(), item.copy());
        for (String line : item.lore()) {
          element.addLore(line);
        }
        return takeable(element.setDescription(element.getLore()))
            .onLeftClick(clicked -> takeEquipment(slot, encoded))
            .onShiftLeftClick(clicked -> takeEquipment(slot, encoded));
      } catch (RuntimeException unreadable) {
        AdaptLog.warn("Skipping unreadable equipped item on golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
      }
    }
    return GuiTheme.element("golem-equipment-" + slot.id(), Id.minecraft("cyan_stained_glass_pane"), "gui", "golemancy", "slots", "equipment_empty")
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_EQUIPMENT_EMPTY, trusted("slot", AdaptLanguage.text(slot.displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_EQUIPMENT_EMPTY_LORE))
        .onLeftClick(clicked -> equipFromCursor(clicked, slot))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_EQUIPMENT_EMPTY_LORE));
  }

  private GuiElement statsElement(GolemView view) {
    return GuiTheme.element("golem-stats", Id.minecraft("book"), "gui", "golemancy", "stats")
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_STATS))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_KILLS, trusted("value", view.stats().kills())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_DAMAGE, trusted("value", view.stats().damage())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_TASKS, trusted("value", view.stats().tasks())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_ITEMS_MOVED, trusted("value", view.stats().itemsMoved())))
        .setRole(MenuRole.INFO);
  }

  private void placeToggles(Placer target, GolemView view) {
    GolemView.Toggles toggles = view.toggles();
    target.place(COMBAT_SLOT, HEADER_ROW, combatElement(toggles));
    target.place(0, FOOTER_ROW, utilityElement(view));
    target.place(MASTERWORK_SLOT, FOOTER_ROW,
        GolemGuiActions.showsMasterwork(toggles.masterwork(), toggles.maxMasterworks()) ? masterworkElement(view) : null);
  }

  private GuiElement combatElement(GolemView.Toggles toggles) {
    GuiElement combat = GuiTheme.element("golem-combat", GolemGuiActions.combatIcon(toggles.combat(), toggles.combatLocked()), "gui", "golemancy", "combat")
        .setName((toggles.combat() ? C.AQUA : C.RED) + AdaptLanguage.text(GolemGuiActions.combatName(toggles.combat(), toggles.combatLocked())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemGuiActions.combatLore(toggles.combat(), toggles.combatLocked())));
    if (toggles.combatLocked()) {
      return combat.setRole(MenuRole.INFO);
    }
    return combat.onLeftClick(clicked -> toggleCombat())
        .setHint(MenuHint.toggle(toggles.combat()))
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE));
  }

  private GuiElement utilityElement(GolemView view) {
    GolemView.Toggles toggles = view.toggles();
    if (toggles.colossusAvailable()) {
      return GuiTheme.element("golem-colossus", GolemGuiActions.colossusIcon(toggles.colossus()), "gui", "golemancy", "colossus")
          .setName(C.GOLD + AdaptLanguage.text(GolemGuiActions.colossusName(toggles.colossus())))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_COLOSSUS_LORE))
          .onLeftClick(clicked -> toggleColossus())
          .setHint(MenuHint.toggle(toggles.colossus()))
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_COLOSSUS_LORE));
    }
    if (GolemGuiActions.hasSnowTrail(view.body())) {
      return GuiTheme.element("golem-snow-trail", GolemGuiActions.snowTrailIcon(toggles.snowTrail()), "gui", "golemancy", "snow_trail")
          .setName(C.AQUA + AdaptLanguage.text(GolemGuiActions.snowTrailName(toggles.snowTrail())))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_SNOW_TRAIL_LORE))
          .onLeftClick(clicked -> toggleSnowTrail())
          .setHint(MenuHint.toggle(toggles.snowTrail()))
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_SNOW_TRAIL_LORE));
    }
    return null;
  }

  private GuiElement essenceElement(GolemView view) {
    if (!GolemGuiActions.showsEssence(view.cores().slotted(), view.essence())) {
      return null;
    }
    return GuiTheme.element("golem-essence", Id.minecraft("experience_bottle"), "gui", "golemancy", "essence")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GATHERER_CORE_ESSENCE, trusted("value", view.essence())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GATHERER_CORE_ESSENCE_LORE))
        .onLeftClick(clicked -> withdrawEssence())
        .setRole(MenuRole.ACTION)
        .setValue(Integer.toString(view.essence()))
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GATHERER_CORE_ESSENCE_LORE));
  }

  private void placeActions(Placer target, GolemView view) {
    if (GolemPacking.packs(player)) {
      target.place(RIGHT_EDGE - 1, FOOTER_ROW, GuiTheme.element("golem-pack", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "pack")
          .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_PACK))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_PACK_LORE))
          .onLeftClick(clicked -> pack())
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_PACK)));
    }
    target.place(RIGHT_EDGE, FOOTER_ROW, GuiTheme.element("golem-release", Id.minecraft("shears"), "gui", "golemancy", "release")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_RELEASE))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RELEASE_LORE))
        .onLeftClick(clicked -> confirmRelease())
        .setHint(MenuHint.destructive(AdaptLanguage.text(GolemancyMessages.GUI_RELEASE)))
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_RELEASE)));
  }

  private void cycleMode(boolean forward) {
    mutate(() -> handle.setMode(GolemGuiActions.cycleMode(GolemMastery.cores(handle), handle.state().mode(), forward)));
  }

  private void toggleChannel(DyeColor color) {
    mutate(() -> {
      GolemRuneEffects runes = GolemRunes.effects(handle);
      int mask = runes.channels(handle.state().channels());
      int next = GolemGuiActions.toggleChannel(mask, color, runes.channelSlots());
      if (next == mask) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_CHANNELS_FULL, trusted("count", runes.channelSlots()));
        return;
      }
      handle.setChannels(next);
    });
  }

  private void show(Page next) {
    page = next;
    refresh();
  }

  private void slotFromCursor(MenuClick clicked) {
    ItemRef cursor = player.inventory().cursor();
    Optional<GolemRune> held = GolemItems.runeOf(cursor);
    if (held.isEmpty()) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_RUNE_NEEDED);
      return;
    }
    GolemRune rune = held.get();
    ItemRef taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> slot(rune, taken));
  }

  private void equipFromCursor(MenuClick clicked, GolemEquipmentSlot slot) {
    ItemRef cursor = player.inventory().cursor();
    if (cursor.isEmpty() || GolemEquipmentSlot.isAir(cursor.type())) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_EQUIPMENT_NEEDED);
      return;
    }
    if (!slot.accepts(cursor.type())) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_EQUIPMENT_REFUSED, trusted("slot", GolemGuiSupport.localized(slot.displayName())));
      return;
    }
    ItemRef taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> equip(slot, taken));
  }

  private void equip(GolemEquipmentSlot slot, ItemRef taken) {
    if (handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    if (!handle.equip(slot, taken)) {
      handBack(taken);
    }
    snapshot();
  }

  private void takeEquipment(GolemEquipmentSlot slot, String encoded) {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      if (handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      Optional<ItemRef> item = handle.unequip(slot, encoded);
      item.ifPresent(this::handBack);
      snapshot();
    });
  }

  private void designateFromCursor(MenuClick clicked) {
    ItemRef cursor = player.inventory().cursor();
    if (GolemItems.kindOf(cursor).orElse(null) != GolemItemKind.MASTERWORK_HEART) {
      GolemGuiSupport.warn(player, GolemancyMessages.MASTERWORK_HEART_NEEDED);
      return;
    }
    ItemRef taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> designate(taken));
  }

  private void designate(ItemRef taken) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null || handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    GolemRegistry.Designation designation = registry.designate(handle, GolemMastery.maxMasterworks(handle.owner()));
    if (designation != GolemRegistry.Designation.DESIGNATED) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemGuiActions.designationNotice(designation, registry.masterworks(handle.owner())));
      snapshot();
      return;
    }
    Tasks.runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_DESIGNATED);
    snapshot();
  }

  private void undesignate() {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      int kept = GolemRunes.layout(handle.body(), handle.owner(), false).unlockedSlots();
      Optional<List<ItemRef>> returned = registry.undesignate(handle, kept);
      if (returned.isEmpty()) {
        snapshot();
        return;
      }
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      for (ItemRef item : returned.get()) {
        handBack(item);
      }
      Tasks.runEntity(player, player::saveData);
      GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_REMOVED);
      snapshot();
    });
  }

  private void slotCoreFromCursor(MenuClick clicked) {
    ItemRef cursor = player.inventory().cursor();
    Optional<GolemCoreType> held = GolemItems.coreOf(cursor);
    if (held.isEmpty()) {
      GolemGuiSupport.warn(player, GolemancyMessages.MASTERWORK_CORE_NEEDED);
      return;
    }
    GolemCoreType core = held.get();
    ItemRef heart = null;
    if (needsTwinHeart()) {
      heart = takeHeart();
      if (heart == null) {
        GolemGuiSupport.warn(player, GolemancyMessages.RUNEFORGED_HEART_NEEDED);
        return;
      }
    }
    ItemRef taken = takeOne(cursor);
    ItemRef paid = heart;
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> addCore(core, taken, paid));
  }

  private boolean needsTwinHeart() {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    return registry != null && !GolemMastery.perks(handle).dualCore() && registry.twinOpen(handle)
        && !registry.twin(handle) && GolemMastery.twinNeedsHeart(registry.twins(handle.owner()));
  }

  private @Nullable ItemRef takeHeart() {
    PlayerInventoryRef inventory = player.inventory();
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef item = inventory.item(slot);
      if (item != null && !item.isEmpty() && GolemItems.kindOf(item).orElse(null) == GolemItemKind.MASTERWORK_HEART) {
        ItemRef heart = item.copyWithAmount(1);
        item.setAmount(item.amount() - 1);
        inventory.setItem(slot, item.amount() > 0 ? item : AdaptCore.platform().host().items().empty());
        return heart;
      }
    }
    return null;
  }

  private void addCore(GolemCoreType core, ItemRef taken, @Nullable ItemRef heart) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null || handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      if (heart != null) {
        handBack(heart);
      }
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    GolemRegistry.CoreSlotting slotting = registry.addCore(handle, core, heart != null);
    if (heart != null && !slotting.heartSpent()) {
      handBack(heart);
    }
    if (slotting.refusal().isPresent()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemGuiActions.coreRefusalNotice(slotting.refusal().get(), core, handle.body()));
      snapshot();
      return;
    }
    Tasks.runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_CORE_SLOTTED, trusted("core", GolemGuiSupport.localized(core.displayName())));
    snapshot();
  }

  private void takeCore(GolemCoreType core) {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      if (!registry.removeCore(handle, core)) {
        snapshot();
        return;
      }
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      handBack(GolemItems.core(core));
      Tasks.runEntity(player, player::saveData);
      GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_CORE_REMOVED, trusted("core", GolemGuiSupport.localized(core.displayName())));
      snapshot();
    });
  }

  private ItemRef takeOne(ItemRef cursor) {
    ItemRef taken = cursor.copyWithAmount(1);
    cursor.setAmount(cursor.amount() - 1);
    player.inventory().setCursor(cursor.amount() > 0 ? cursor : AdaptCore.platform().host().items().empty());
    return taken;
  }

  private void slot(GolemRune rune, ItemRef taken) {
    if (handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    List<GolemRune> runes = handle.state().runes();
    Optional<GolemRuneLayout.Refusal> refusal = GolemRunes.layout(handle.body(), handle.owner(), handle.state().masterwork()).refuse(runes, rune.aspect());
    if (refusal.isPresent()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemGuiActions.refusalNotice(refusal.get(), rune.aspect(), handle.body()));
      snapshot();
      return;
    }
    handle.setRunes(GolemGuiActions.withRune(runes, rune));
    GolemStats.recompute(handle);
    Tasks.runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RUNE_SLOTTED, trusted("aspect", GolemGuiSupport.localized(rune.aspect().displayName())));
    snapshot();
  }

  private void takeRune(int index, GolemRune rune) {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      if (handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      Optional<List<GolemRune>> remaining = GolemGuiActions.withoutRune(handle.state().runes(), index, rune);
      if (remaining.isEmpty()) {
        snapshot();
        return;
      }
      handle.setRunes(remaining.get());
      handBack(GolemItems.rune(rune));
      GolemStats.recompute(handle);
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RUNE_REMOVED, trusted("aspect", GolemGuiSupport.localized(rune.aspect().displayName())));
      snapshot();
    });
  }

  private void withdrawEssence() {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      if (handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      int points = handle.state().essencePoints();
      EssenceWithdrawal withdrawal = takeEssence(handle, player);
      if (withdrawal == EssenceWithdrawal.REFUSED) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      if (withdrawal == EssenceWithdrawal.EMPTY) {
        GolemGuiSupport.warn(player, GolemancyMessages.GATHERER_CORE_ESSENCE_EMPTY);
      } else {
        GolemGuiSupport.notify(player, GolemancyMessages.GATHERER_CORE_ESSENCE_TAKEN, trusted("value", points));
      }
      snapshot();
    });
  }

  private void handBack(ItemRef item) {
    GolemGuiItems.toPlayer(player, item, handle.entity().location());
  }

  private void toggleSnowTrail() {
    mutate(() -> handle.setSnowTrail(!handle.state().snowTrail()));
  }

  private void toggleCombat() {
    mutate(() -> {
      if (!handle.setCombat(handle.state().combatOff())) {
        GolemGuiSupport.warn(player, GolemancyMessages.GUI_COMBAT_LOCKED_LORE);
      }
    });
  }

  private void toggleColossus() {
    mutate(() -> GolemColossus.form()
        .flatMap(form -> GolemGuiActions.colossusRefusal(form.toggle(handle)))
        .ifPresent(refusal -> GolemGuiSupport.warn(player, refusal)));
  }

  private void setHome() {
    Loc here = player.location();
    GolemAnchor home = GolemAnchor.at(here);
    mutate(() -> {
      handle.setHome(home);
      GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_HOME_SET,
          trusted("x", home.x()), trusted("y", home.y()), trusted("z", home.z()));
    });
  }

  private void armChestLink() {
    gestures.armLink(player.uuid(), handle, System.currentTimeMillis());
    window.close();
    GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_LINK_ARMED);
  }

  private void take(Carried entry) {
    GolemGuiItems.fromGolem(handle.entity(), player, () -> {
      if (!handle.removeCarried(entry.encoded())) {
        snapshot();
        return;
      }
      handBack(entry.item().copy());
      snapshot();
    });
  }

  private void pack() {
    window.close();
    GolemPacking.packer().ifPresent(packer -> packer.pack(player, handle));
  }

  private void confirmRelease() {
    GuiConfirm.open(player,
        AdaptLanguage.text(GolemancyMessages.GUI_RELEASE),
        AdaptLanguage.text(GolemancyMessages.GUI_RELEASE_CONFIRM),
        this::release,
        () -> open(player, handle, gestures));
  }

  private void release() {
    Tasks.runEntity(handle.entity(), () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !registry.release(handle)) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_RELEASE_FAILED);
        return;
      }
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.forget(handle));
      GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RELEASED);
    }, this::gone);
  }

  private void mutate(Runnable change) {
    Tasks.runEntity(handle.entity(), () -> {
      if (handle.isRetired() || !handle.entity().isValid()) {
        gone();
        return;
      }
      change.run();
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      snapshot();
    }, this::gone);
  }

  static EssenceWithdrawal takeEssence(GolemHandle handle, PlayerRef player) {
    int points = handle.state().essencePoints();
    if (points <= 0) {
      return EssenceWithdrawal.EMPTY;
    }
    handle.setEssence(handle.state().essence() - points);
    AtomicBoolean settled = new AtomicBoolean();
    Runnable restore = () -> {
      if (settled.compareAndSet(false, true) && !handle.isRetired()) {
        handle.setEssence(handle.state().essence() + points);
      }
    };
    Runnable give = () -> {
      if (settled.compareAndSet(false, true)) {
        AdaptPorts.get(ComponentPort.class, ComponentPort.NONE).awardExperience(player, points);
      }
    };
    if (Tasks.runEntity(player, give, () -> GolemGuiItems.fromGolem(handle.entity(), player, restore))) {
      return EssenceWithdrawal.TAKEN;
    }
    restore.run();
    return EssenceWithdrawal.REFUSED;
  }

  private static String channelList(int mask) {
    List<TextKey> names = GolemGuiActions.channelNames(mask);
    List<String> texts = new ArrayList<>(names.size());
    for (TextKey name : names) {
      texts.add(AdaptLanguage.text(name));
    }
    return String.join(", ", texts);
  }

  private static GuiElement itemElement(String id, ItemRef item) {
    return new GuiElement(id).setItem(item);
  }

  private static GuiElement takeable(GuiElement element) {
    String take = AdaptLanguage.text(GolemancyMessages.GUI_TAKE);
    return element.addLore(C.GRAY + take)
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, take)
        .setActionLabel(ClickKind.SHIFT_LEFT, take);
  }

  private static String coordinates(TextKey key, GolemAnchor anchor) {
    return AdaptLanguage.text(key, trusted("x", anchor.x()), trusted("y", anchor.y()), trusted("z", anchor.z()));
  }

  @FunctionalInterface
  private interface Placer {
    void place(int position, int row, GuiElement element);
  }

  private record Carried(String encoded, ItemRef item) {
  }

  enum EssenceWithdrawal {
    EMPTY,
    TAKEN,
    REFUSED
  }

  private enum Page {
    MAIN,
    CHANNELS
  }
}
