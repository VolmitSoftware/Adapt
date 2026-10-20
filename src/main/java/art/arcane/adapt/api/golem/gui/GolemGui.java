package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
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
import art.arcane.adapt.nms.NMS;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiConfirm;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
  private static final int FOOTER_ROW = 4;
  private static final int LEFT_EDGE = -4;
  private static final int RIGHT_EDGE = 4;
  private static final int CHANNELS_PER_ROW = 8;
  private static final int ESSENCE_SLOT = 2;
  private static final int PRIMARY_CORE_SLOT = LEFT_EDGE + 1;
  private static final int SECOND_CORE_SLOT = LEFT_EDGE + 2;
  private static final int MASTERWORK_SLOT = LEFT_EDGE + 2;
  private static final int COMBAT_SLOT = -1;

  private final Player player;
  private final GolemHandle handle;
  private final CrookGestures<GolemHandle> gestures;
  private final LiveRefresh<GolemView> live;
  private UIWindow window;
  private Page page = Page.MAIN;
  private Page renderedPage;
  private GolemView shown;
  private List<Carried> renderedCarried = List.of();
  private int renderedHidden;

  private GolemGui(Player player, GolemHandle handle, CrookGestures<GolemHandle> gestures) {
    this.player = player;
    this.handle = handle;
    this.gestures = gestures;
    this.live = new LiveRefresh<>(new LiveRefresh.Hooks<>(
        (task, delay, retired) -> J.runEntity(handle.entity(), task, delay, retired),
        (task, delay, retired) -> J.runEntity(player, task, delay, retired),
        this::read, this::render, this::gone, this::isOpen));
  }

  public static void open(Player player, GolemHandle handle, CrookGestures<GolemHandle> gestures) {
    new GolemGui(player, handle, gestures).refresh();
  }

  private void refresh() {
    J.runEntity(handle.entity(), this::snapshot, this::gone);
  }

  private void snapshot() {
    Optional<GolemView> view = read();
    if (view.isEmpty()) {
      gone();
      return;
    }
    J.runEntity(player, () -> render(view.get()));
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
    J.runEntity(player, () -> {
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
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getUniqueId())) {
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
    UIWindow next = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(next, "golemancy-golem");
    next.setViewportHeight(ROWS);
    Placer target = (position, row, element) -> {
      if (element != null) {
        next.setElement(position, row, element);
      }
    };
    renderedCarried = List.of();
    renderedHidden = 0;
    next.batch(() -> {
      if (page == Page.CHANNELS) {
        placeChannelPicker(target, view);
        return;
      }
      for (GolemView.Section section : GolemView.Section.values()) {
        place(target, section, view, null);
      }
      placeActions(target, view);
    });
    next.setTitle(page == Page.CHANNELS
        ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_TITLE)
        : AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(view.body().displayName()))));
    renderedPage = page;
    window = next;
    next.open();
    Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), next);
    Inventory top = UIWindow.inventoryViewTopInventory(player.getOpenInventory());
    next.onClosed(closed -> closed(next, top));
    if (top != null) {
      GuiCursor.track(player.getUniqueId(), top);
    }
  }

  private void update(GolemView view, GolemView before) {
    UIWindow target = window;
    for (GolemView.Section section : view.changes(before)) {
      place(target::updateElement, section, view, before);
    }
  }

  private void closed(UIWindow closed, Inventory top) {
    Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), closed);
    if (top != null) {
      GuiCursor.untrack(player.getUniqueId(), top);
    }
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
    }
  }

  private Element bodyElement(GolemView view) {
    Element body = GuiTheme.element("golem-body", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "body")
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(view.body().displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_HEALTH,
            trusted("health", view.vitals().health()),
            trusted("maximum", view.vitals().maxHealth())));
    if (view.vitals().dormant()) {
      body.addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.GUI_DORMANT));
    }
    return body.setProgress(1D);
  }

  private void placeCores(Placer target, GolemView view) {
    List<GolemCoreType> cores = view.cores().slotted();
    target.place(PRIMARY_CORE_SLOT, HEADER_ROW, itemElement("golem-core-0", GolemItems.core(cores.getFirst())));
    target.place(SECOND_CORE_SLOT, HEADER_ROW, GolemGuiActions.showsSecondCore(cores, view.cores().dualCore()) ? secondCoreElement(view) : null);
  }

  private Element modeElement(GolemView view) {
    return GuiTheme.element("golem-mode", GolemGuiActions.modeIcon(view.mode()), "gui", "golemancy", "mode")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_MODE, trusted("mode", AdaptLanguage.text(view.mode().displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
        .setProgress(1D)
        .onLeftClick(clicked -> cycleMode(true))
        .onRightClick(clicked -> cycleMode(false));
  }

  private void placeAnchors(Placer target, GolemView view) {
    target.place(RIGHT_EDGE - 1, HEADER_ROW, GuiTheme.element("golem-home", Material.COMPASS, "gui", "golemancy", "home")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_SET_HOME))
        .addLore(C.GRAY + coordinates(GolemancyMessages.GUI_HOME, view.anchors().home()))
        .setProgress(1D)
        .onLeftClick(clicked -> setHome()));
    GolemState.Anchor homeChest = view.anchors().chest();
    target.place(RIGHT_EDGE, HEADER_ROW, GuiTheme.element("golem-chest", Material.CHEST, "gui", "golemancy", "chest")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_LINK_CHEST))
        .addLore(C.GRAY + (homeChest == null
            ? AdaptLanguage.text(GolemancyMessages.GUI_NO_HOME_CHEST)
            : coordinates(GolemancyMessages.GUI_HOME_CHEST, homeChest)))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.GUI_LINK_HINT))
        .setProgress(1D)
        .onLeftClick(clicked -> armChestLink()));
  }

  private Element secondCoreElement(GolemView view) {
    if (view.cores().slotted().size() > 1) {
      GolemCoreType core = view.cores().slotted().get(1);
      return itemElement("golem-core-1", GolemItems.core(core))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_REMOVE))
          .onLeftClick(clicked -> takeCore(core));
    }
    return GuiTheme.element("golem-core-1", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "gui", "golemancy", "core_empty")
        .setName(C.GOLD + AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_EMPTY))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.MASTERWORK_CORE_EMPTY_LORE))
        .setProgress(1D)
        .onLeftClick(clicked -> slotCoreFromCursor());
  }

  private Element masterworkElement(GolemView view) {
    if (view.toggles().masterwork()) {
      return itemElement("golem-masterwork", GolemItems.masterworkHeart())
          .setName(C.GOLD + AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT_LORE))
          .setEnchanted(true)
          .onLeftClick(clicked -> undesignate());
    }
    return GuiTheme.element("golem-masterwork", Material.YELLOW_STAINED_GLASS_PANE, "gui", "golemancy", "masterwork_empty")
        .setName(C.GOLD + AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT_EMPTY))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.MASTERWORK_SLOT_EMPTY_LORE,
            trusted("count", view.toggles().masterworks()), trusted("maximum", view.toggles().maxMasterworks())))
        .setProgress(1D)
        .onLeftClick(clicked -> designateFromCursor());
  }

  private Element channelElement(GolemView view) {
    int mask = view.channels().mask();
    if (view.channels().slots() <= 1) {
      return GuiTheme.element("golem-channel", GolemGuiActions.channelIcon(mask), "gui", "golemancy", "channel")
          .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL,
              trusted("channel", AdaptLanguage.text(GolemGuiActions.channelName(mask)))))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
          .setProgress(1D)
          .onLeftClick(clicked -> cycleChannel(true))
          .onRightClick(clicked -> cycleChannel(false));
    }
    return GuiTheme.element("golem-channel", GolemGuiActions.channelIcon(mask), "gui", "golemancy", "channel")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS, trusted("channels", channelList(mask))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_LORE, trusted("count", view.channels().slots())))
        .setProgress(1D)
        .onLeftClick(clicked -> show(Page.CHANNELS));
  }

  private void placeChannelPicker(Placer target, GolemView view) {
    int mask = view.channels().mask();
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      int colorMask = ChannelMask.of(color);
      boolean selected = ChannelMask.contains(mask, color);
      target.place(GuiLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), index / CHANNELS_PER_ROW,
          GuiTheme.element("golem-channel-" + color.name().toLowerCase(Locale.ROOT), GolemGuiActions.channelIcon(colorMask), "gui", "golemancy", "channel")
              .setName((selected ? C.GREEN : C.YELLOW) + AdaptLanguage.text(GolemGuiActions.channelName(colorMask)))
              .addLore(selected ? C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT))
              .setEnchanted(selected)
              .setProgress(1D)
              .onLeftClick(clicked -> toggleChannel(color)));
    }
    target.place(LEFT_EDGE, FOOTER_ROW, GuiTheme.element("golem-channels-back", Material.ARROW, "gui", "golemancy", "back")
        .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_BACK))
        .setProgress(1D)
        .onLeftClick(clicked -> show(Page.MAIN)));
    target.place(0, FOOTER_ROW, GuiTheme.element("golem-channels-summary", GolemGuiActions.channelIcon(mask), "gui", "golemancy", "channel")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS, trusted("channels", channelList(mask))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_LORE, trusted("count", view.channels().slots())))
        .setProgress(1D));
    target.place(RIGHT_EDGE, FOOTER_ROW, GuiTheme.element("golem-channels-clear", Material.GLASS_BOTTLE, "gui", "golemancy", "channels_clear")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_CLEAR))
        .setProgress(1D)
        .onLeftClick(clicked -> mutate(() -> handle.setChannels(ChannelMask.NONE))));
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

  private Element runeElement(GolemView view, int index) {
    List<GolemRune> runes = view.runes().slotted();
    GolemRuneLayout layout = view.runes().layout();
    if (index < runes.size()) {
      GolemRune rune = runes.get(index);
      ItemStack item = GolemItems.rune(rune);
      Element element = itemElement("golem-rune-" + index, item);
      ItemMeta meta = item.getItemMeta();
      if (meta != null && meta.getLore() != null) {
        for (String line : meta.getLore()) {
          element.addLore(line);
        }
      }
      GolemGuiActions.RuneNote note = GolemGuiActions.runeNote(layout, runes, index, view.body());
      return element.addLore((note.active() ? C.GREEN : C.RED) + note.notice().text())
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_REMOVE))
          .onLeftClick(clicked -> takeRune(index, rune));
    }
    if (index < layout.unlockedSlots()) {
      return GuiTheme.element("golem-rune-" + index, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "gui", "golemancy", "rune_empty")
          .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_EMPTY))
          .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_EMPTY_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> slotFromCursor());
    }
    return GuiTheme.element("golem-rune-" + index, Material.BARRIER, "gui", "golemancy", "rune_locked")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_LOCKED))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_UNLOCK_LEVEL, trusted("level", layout.slotUnlockLevel(index))))
        .setProgress(1D);
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
        entries.add(new Carried(encoded, NMS.deserializeStack(encoded)));
      } catch (RuntimeException unreadable) {
        Adapt.warn("Skipping unreadable carried item on golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
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

  private Element carriedCell(List<Carried> entries, int hidden, int index) {
    if (index < entries.size()) {
      Carried entry = entries.get(index);
      ItemStack item = entry.item();
      Element element = itemElement("golem-carried-" + index, item.clone()).setCount(item.getAmount());
      ItemMeta meta = item.getItemMeta();
      List<String> lore = meta == null || meta.getLore() == null ? List.of() : meta.getLore();
      for (String line : lore) {
        element.addLore(line);
      }
      return element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_TAKE))
          .onLeftClick(clicked -> take(entry))
          .onShiftLeftClick(clicked -> take(entry));
    }
    if (index == entries.size() && hidden > 0) {
      return GuiTheme.element("golem-carried-more", Material.PAPER, "gui", "golemancy", "carried_more")
          .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_MORE_CARRIED, trusted("count", hidden)))
          .setProgress(1D);
    }
    return null;
  }

  private Element statsElement(GolemView view) {
    return GuiTheme.element("golem-stats", Material.BOOK, "gui", "golemancy", "stats")
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_STATS))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_KILLS, trusted("value", view.stats().kills())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_DAMAGE, trusted("value", view.stats().damage())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_TASKS, trusted("value", view.stats().tasks())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_ITEMS_MOVED, trusted("value", view.stats().itemsMoved())))
        .setProgress(1D);
  }

  private void placeToggles(Placer target, GolemView view) {
    GolemView.Toggles toggles = view.toggles();
    target.place(COMBAT_SLOT, HEADER_ROW, combatElement(toggles));
    target.place(0, FOOTER_ROW, utilityElement(view));
    target.place(MASTERWORK_SLOT, FOOTER_ROW,
        GolemGuiActions.showsMasterwork(view.body(), toggles.masterwork(), toggles.maxMasterworks()) ? masterworkElement(view) : null);
  }

  private Element combatElement(GolemView.Toggles toggles) {
    Element combat = GuiTheme.element("golem-combat", GolemGuiActions.combatIcon(toggles.combat(), toggles.combatLocked()), "gui", "golemancy", "combat")
        .setName((toggles.combat() ? C.AQUA : C.RED) + AdaptLanguage.text(GolemGuiActions.combatName(toggles.combat(), toggles.combatLocked())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemGuiActions.combatLore(toggles.combat(), toggles.combatLocked())))
        .setProgress(1D);
    return toggles.combatLocked() ? combat : combat.onLeftClick(clicked -> toggleCombat());
  }

  private Element utilityElement(GolemView view) {
    GolemView.Toggles toggles = view.toggles();
    if (toggles.colossusAvailable()) {
      return GuiTheme.element("golem-colossus", GolemGuiActions.colossusIcon(toggles.colossus()), "gui", "golemancy", "colossus")
          .setName(C.GOLD + AdaptLanguage.text(GolemGuiActions.colossusName(toggles.colossus())))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_COLOSSUS_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> toggleColossus());
    }
    if (GolemGuiActions.hasSnowTrail(view.body())) {
      return GuiTheme.element("golem-snow-trail", GolemGuiActions.snowTrailIcon(toggles.snowTrail()), "gui", "golemancy", "snow_trail")
          .setName(C.AQUA + AdaptLanguage.text(GolemGuiActions.snowTrailName(toggles.snowTrail())))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_SNOW_TRAIL_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> toggleSnowTrail());
    }
    return null;
  }

  private Element essenceElement(GolemView view) {
    if (!GolemGuiActions.showsEssence(view.cores().slotted(), view.essence())) {
      return null;
    }
    return GuiTheme.element("golem-essence", Material.EXPERIENCE_BOTTLE, "gui", "golemancy", "essence")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GATHERER_CORE_ESSENCE, trusted("value", view.essence())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GATHERER_CORE_ESSENCE_LORE))
        .setProgress(1D)
        .onLeftClick(clicked -> withdrawEssence());
  }

  private void placeActions(Placer target, GolemView view) {
    if (GolemPacking.packs(player)) {
      target.place(RIGHT_EDGE - 1, FOOTER_ROW, GuiTheme.element("golem-pack", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "pack")
          .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_PACK))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_PACK_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> pack()));
    }
    target.place(RIGHT_EDGE, FOOTER_ROW, GuiTheme.element("golem-release", Material.SHEARS, "gui", "golemancy", "release")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_RELEASE))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RELEASE_LORE))
        .setProgress(1D)
        .onLeftClick(clicked -> confirmRelease()));
  }

  private void cycleMode(boolean forward) {
    mutate(() -> handle.setMode(GolemGuiActions.cycleMode(GolemMastery.cores(handle), handle.state().mode(), forward)));
  }

  private void cycleChannel(boolean forward) {
    mutate(() -> handle.setChannels(GolemGuiActions.cycleChannel(handle.state().channels(), forward)));
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

  private void slotFromCursor() {
    ItemStack cursor = player.getItemOnCursor();
    Optional<GolemRune> held = GolemItems.runeOf(cursor);
    if (held.isEmpty()) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_RUNE_NEEDED);
      return;
    }
    GolemRune rune = held.get();
    ItemStack taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> slot(rune, taken));
  }

  private void designateFromCursor() {
    ItemStack cursor = player.getItemOnCursor();
    if (GolemItems.kindOf(cursor).orElse(null) != GolemItemKind.MASTERWORK_HEART) {
      GolemGuiSupport.warn(player, GolemancyMessages.MASTERWORK_HEART_NEEDED);
      return;
    }
    ItemStack taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> designate(taken));
  }

  private void designate(ItemStack taken) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null || handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    GolemRegistry.Designation designation = registry.designate(handle, GolemMastery.maxMasterworks(handle.owner()));
    if (designation != GolemRegistry.Designation.DESIGNATED) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemGuiActions.designationNotice(designation, registry.masterworks(handle.owner()), handle.body()));
      snapshot();
      return;
    }
    J.runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_DESIGNATED);
    snapshot();
  }

  private void undesignate() {
    Mob golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      int kept = GolemRunes.layout(handle.body(), handle.owner(), false).unlockedSlots();
      Optional<List<ItemStack>> returned = registry.undesignate(handle, kept);
      if (returned.isEmpty()) {
        snapshot();
        return;
      }
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      for (ItemStack item : returned.get()) {
        handBack(item);
      }
      J.runEntity(player, player::saveData);
      GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_REMOVED);
      snapshot();
    });
  }

  private void slotCoreFromCursor() {
    ItemStack cursor = player.getItemOnCursor();
    Optional<GolemCoreType> held = GolemItems.coreOf(cursor);
    if (held.isEmpty()) {
      GolemGuiSupport.warn(player, GolemancyMessages.MASTERWORK_CORE_NEEDED);
      return;
    }
    GolemCoreType core = held.get();
    ItemStack taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> addCore(core, taken));
  }

  private void addCore(GolemCoreType core, ItemStack taken) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null || handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    Optional<GolemMastery.CoreRefusal> refusal = registry.addCore(handle, core);
    if (refusal.isPresent()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemGuiActions.coreRefusalNotice(refusal.get(), core, handle.body()));
      snapshot();
      return;
    }
    J.runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_CORE_SLOTTED, trusted("core", GolemGuiSupport.localized(core.displayName())));
    snapshot();
  }

  private void takeCore(GolemCoreType core) {
    Mob golem = handle.entity();
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
      J.runEntity(player, player::saveData);
      GolemGuiSupport.notify(player, GolemancyMessages.MASTERWORK_CORE_REMOVED, trusted("core", GolemGuiSupport.localized(core.displayName())));
      snapshot();
    });
  }

  private ItemStack takeOne(ItemStack cursor) {
    ItemStack taken = cursor.clone();
    taken.setAmount(1);
    cursor.setAmount(cursor.getAmount() - 1);
    player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
    return taken;
  }

  private void slot(GolemRune rune, ItemStack taken) {
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
    J.runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RUNE_SLOTTED, trusted("aspect", GolemGuiSupport.localized(rune.aspect().displayName())));
    snapshot();
  }

  private void takeRune(int index, GolemRune rune) {
    Mob golem = handle.entity();
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
    Mob golem = handle.entity();
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

  private void handBack(ItemStack item) {
    GolemGuiItems.toPlayer(player, item, handle.entity().getLocation());
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
    Location here = player.getLocation();
    GolemState.Anchor home = GolemState.Anchor.at(here);
    mutate(() -> {
      handle.setHome(home);
      GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_HOME_SET,
          trusted("x", home.x()), trusted("y", home.y()), trusted("z", home.z()));
    });
  }

  private void armChestLink() {
    gestures.armLink(player.getUniqueId(), handle, System.currentTimeMillis());
    window.close();
    GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_LINK_ARMED);
  }

  private void take(Carried entry) {
    GolemGuiItems.fromGolem(handle.entity(), player, () -> {
      if (!handle.removeCarried(entry.encoded())) {
        snapshot();
        return;
      }
      handBack(entry.item().clone());
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
    J.runEntity(handle.entity(), () -> {
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
    J.runEntity(handle.entity(), () -> {
      if (handle.isRetired() || !handle.entity().isValid()) {
        gone();
        return;
      }
      change.run();
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      snapshot();
    }, this::gone);
  }

  static EssenceWithdrawal takeEssence(GolemHandle handle, Player player) {
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
        player.giveExp(points);
      }
    };
    if (J.runEntity(player, give, () -> GolemGuiItems.fromGolem(handle.entity(), player, restore))) {
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

  private static Element itemElement(String id, ItemStack item) {
    return new UIElement(id)
        .setMaterial(new MaterialBlock(item.getType()))
        .setBaseItemStack(item)
        .setProgress(1D);
  }

  private static String coordinates(TextKey key, GolemState.Anchor anchor) {
    return AdaptLanguage.text(key, trusted("x", anchor.x()), trusted("y", anchor.y()), trusted("z", anchor.z()));
  }

  @FunctionalInterface
  private interface Placer {
    void place(int position, int row, Element element);
  }

  private record Carried(String encoded, ItemStack item) {
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
