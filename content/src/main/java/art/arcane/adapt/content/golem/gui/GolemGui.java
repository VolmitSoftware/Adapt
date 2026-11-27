package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.player.Experience;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
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

import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemColossus;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemEquipmentSlot;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemMode;
import art.arcane.adapt.content.golem.GolemPacking;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneEffects;
import art.arcane.adapt.content.golem.GolemRuneLayout;
import art.arcane.adapt.content.golem.GolemRunes;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemStats;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.api.text.TextFormat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;


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
  private GolemWindow window;
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
        (task, delay, retired) -> AdaptApi.scheduler().runEntity(handle.entity(), task, delay, retired),
        (task, delay, retired) -> AdaptApi.scheduler().runEntity(player, task, delay, retired),
        this::read, this::render, this::gone, this::isOpen));
  }

  public static void open(PlayerRef player, GolemHandle handle, CrookGestures<GolemHandle> gestures) {
    new GolemGui(player, handle, gestures).refresh();
  }

  private void refresh() {
    AdaptApi.scheduler().runEntity(handle.entity(), this::snapshot, this::gone);
  }

  private void snapshot() {
    Optional<GolemView> view = read();
    if (view.isEmpty()) {
      gone();
      return;
    }
    AdaptApi.scheduler().runEntity(player, () -> render(view.get()));
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
    GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
    AdaptApi.scheduler().runEntity(player, () -> {
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
    Text.renderFor(player.uuid(), () -> {
      if (initial) {
        draw(view);
      } else if (page == Page.CHANNELS) {
        if (!view.channels().equals(before.channels())) {
          draw(view);
        }
      } else {
        update(view, before);
      }
    });
    shown = view;
    live.start();
  }

  private void draw(GolemView view) {
    GolemWindow next = new GolemWindow(player);
    GolemTheme.apply(next, "golemancy-golem");
    next.setViewportHeight(ROWS);
    next.setFreePlayerInventory(true);
    Placer target = (int position, int row, GolemElement element) -> {
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
    String golemTitle = Text.key("adapt.golemancy.gui.title").with("body", view.body().displayName()).resolve();
    next.setTitle(page == Page.CHANNELS ? Text.key("adapt.golemancy.gui.channels_title").resolve() : golemTitle);
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
    GolemWindow target = window;
    for (GolemView.Section section : view.changes(before)) {
      place(target::updateElement, section, view, before);
    }
  }

  private void closed(GolemWindow closed) {
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

  private GolemElement bodyElement(GolemView view) {
    GolemElement body = GolemTheme.element("golem-body", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "body")
        .setName(TextFormat.AQUA + Text.key("adapt.golemancy.gui.title").with("body", view.body().displayName()).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.health").withMarkup("health", String.valueOf(view.vitals().health())).withMarkup("maximum", String.valueOf(view.vitals().maxHealth())).resolve())
        .setRole(MenuRole.INFO)
        .setValue(view.vitals().health() + "/" + view.vitals().maxHealth());
    if (!view.vitals().workStatus().isEmpty()) {
      body.addLore(TextFormat.YELLOW + view.vitals().workStatus());
    }
    if (view.vitals().dormant()) {
      body.addLore(TextFormat.DARK_GRAY + Text.key("adapt.golemancy.gui.dormant").resolve());
    }
    return body;
  }

  private void placeCores(Placer target, GolemView view) {
    List<GolemCoreType> cores = view.cores().slotted();
    target.place(PRIMARY_CORE_SLOT, HEADER_ROW, itemElement("golem-core-0", GolemItems.core(cores.getFirst()))
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.core.fixed").resolve())
        .setRole(MenuRole.INFO));
    target.place(SECOND_CORE_SLOT, HEADER_ROW, GolemGuiActions.showsSecondCore(cores, view.cores().dualCore()) ? secondCoreElement(view) : null);
  }

  private GolemElement modeElement(GolemView view) {
    String help = TextFormat.GRAY + (switch (view.mode()) {
      case WORK -> Text.key("adapt.golemancy.help.mode.work");
      case FOLLOW -> Text.key("adapt.golemancy.help.mode.follow");
      case STAY -> Text.key("adapt.golemancy.help.mode.stay");
      case HOME -> Text.key("adapt.golemancy.help.mode.home");
    }).resolve();
    List<String> modes = new ArrayList<>();
    for (GolemMode mode : GolemMode.selectableFor(view.cores().slotted())) {
      modes.add(mode.displayName().resolve());
    }
    String current = view.mode().displayName().resolve();
    return GolemTheme.element("golem-mode", GolemGuiActions.modeIcon(view.mode()), "gui", "golemancy", "mode")
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.gui.mode").withMarkup("mode", String.valueOf(current)).resolve())
        .addLore(help)
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.cycle").resolve())
        .onLeftClick(clicked -> cycleMode(true))
        .onRightClick(clicked -> cycleMode(false))
        .setHint(MenuHint.choice(current, modes, null))
        .setValue(current)
        .setDescription(List.of(help))
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.gui.common.next").resolve())
        .setActionLabel(ClickKind.RIGHT, Text.key("adapt.gui.common.previous").resolve());
  }

  private void placeAnchors(Placer target, GolemView view) {
    target.place(RIGHT_EDGE - 1, HEADER_ROW, GolemTheme.element("golem-home", Id.minecraft("compass"), "gui", "golemancy", "home")
        .setName(TextFormat.GREEN + Text.key("adapt.golemancy.gui.set_home").resolve())
        .addLore(TextFormat.GRAY + coordinates(Text.key("adapt.golemancy.gui.home"), view.anchors().home()))
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.home.reach").withMarkup("value", String.valueOf(view.anchors().workReach())).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.home.leash").withMarkup("value", String.valueOf(view.anchors().leash())).resolve())
        .onLeftClick(clicked -> setHome())
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.set_home").resolve()));
    if (view.body() == GolemBody.BONE) {
      target.place(RIGHT_EDGE, HEADER_ROW, null);
      return;
    }
    GolemAnchor homeChest = view.anchors().chest();
    GolemElement chest = GolemTheme.element("golem-chest", Id.minecraft("chest"), "gui", "golemancy", "chest")
        .setName(TextFormat.GREEN + Text.key("adapt.golemancy.gui.link_chest").resolve())
        .addLore(TextFormat.GRAY + (homeChest == null
            ? Text.key("adapt.golemancy.gui.no_home_chest").resolve()
            : coordinates(Text.key("adapt.golemancy.gui.home_chest"), homeChest)));
    String tools = toolLine(view.anchors());
    if (tools != null) {
      chest.addLore(tools);
    }
    List<String> description = List.copyOf(chest.getLore());
    target.place(RIGHT_EDGE, HEADER_ROW, chest
        .addLore(TextFormat.DARK_GRAY + Text.key("adapt.golemancy.gui.link_hint").resolve())
        .onLeftClick(clicked -> armChestLink())
        .setRole(MenuRole.ACTION)
        .setDescription(description)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.link_hint").resolve()));
  }

  private String toolLine(GolemView.Anchors anchors) {
    if (anchors.toolNote() != null) {
      return TextFormat.RED + anchors.toolNote().text();
    }
    if (anchors.held() == null) {
      return null;
    }
    try {
      Id type = GolemState.decodeItem(anchors.held()).type();
      return TextFormat.GRAY + Text.key("adapt.golemancy.tools.holding").withMarkup("tool", String.valueOf(Format.capitalizeWords(type.path().toLowerCase(Locale.ROOT).replace('_', ' ')))).resolve();
    } catch (RuntimeException unreadable) {
      AdaptApi.log().error("Skipping the unreadable held tool of golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
      return null;
    }
  }

  private GolemElement secondCoreElement(GolemView view) {
    if (view.cores().slotted().size() > 1) {
      GolemCoreType core = view.cores().slotted().get(1);
      return itemElement("golem-core-1", GolemItems.core(core))
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.masterwork.core_remove").resolve())
          .onLeftClick(clicked -> takeCore(core))
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.masterwork.core_remove").resolve());
    }
    return GolemTheme.element("golem-core-1", Id.minecraft("orange_stained_glass_pane"), "gui", "golemancy", "slots", "core_empty")
        .setName(TextFormat.GOLD + Text.key("adapt.golemancy.masterwork.core_empty").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.masterwork.core_empty_lore").resolve())
        .onLeftClick(this::slotCoreFromCursor)
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.masterwork.core_empty_lore").resolve());
  }

  private GolemElement masterworkElement(GolemView view) {
    if (view.toggles().masterwork()) {
      return itemElement("golem-masterwork", GolemItems.masterworkHeart())
          .setName(TextFormat.GOLD + Text.key("adapt.golemancy.masterwork.slot").resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.masterwork.slot_lore").resolve())
          .setEnchanted(true)
          .onLeftClick(clicked -> undesignate())
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.masterwork.slot_lore").resolve());
    }
    String instruction = Text.key("adapt.golemancy.masterwork.slot_empty_lore").withMarkup("count", String.valueOf(view.toggles().masterworks())).withMarkup("maximum", String.valueOf(view.toggles().maxMasterworks())).resolve();
    return GolemTheme.element("golem-masterwork", Id.minecraft("yellow_stained_glass_pane"), "gui", "golemancy", "masterwork_empty")
        .setName(TextFormat.GOLD + Text.key("adapt.golemancy.masterwork.slot_empty").resolve())
        .addLore(TextFormat.GRAY + instruction)
        .onLeftClick(this::designateFromCursor)
        .setRole(MenuRole.ACTION)
        .setValue(view.toggles().masterworks() + "/" + view.toggles().maxMasterworks())
        .setActionLabel(ClickKind.LEFT, instruction);
  }

  private GolemElement channelElement(GolemView view) {
    int mask = view.channels().mask();
    return GolemGuiActions.channelElement("golem-channel", mask)
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.gui.channels").withMarkup("channels", String.valueOf(channelList(mask))).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.channels_lore").withMarkup("count", String.valueOf(view.channels().slots())).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.channel.pick").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.channel.match").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.channel.none").resolve())
        .onLeftClick(clicked -> show(Page.CHANNELS))
        .setRole(MenuRole.NAVIGATION)
        .setValue(channelList(mask))
        .setDescription(List.of(
            TextFormat.GRAY + Text.key("adapt.golemancy.gui.channels_lore").withMarkup("count", String.valueOf(view.channels().slots())).resolve(),
            TextFormat.GRAY + Text.key("adapt.golemancy.help.channel.match").resolve(),
            TextFormat.GRAY + Text.key("adapt.golemancy.help.channel.none").resolve()))
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.help.channel.pick").resolve());
  }

  private void placeChannelPicker(Placer target, GolemView view) {
    int mask = view.channels().mask();
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      int colorMask = ChannelMask.of(color);
      boolean selected = ChannelMask.contains(mask, color);
      target.place(GolemLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), index / CHANNELS_PER_ROW,
          GolemGuiActions.channelElement("golem-channel-" + color.name().toLowerCase(Locale.ROOT), colorMask)
              .setName((selected ? TextFormat.GREEN : TextFormat.YELLOW) + GolemGuiActions.channelName(colorMask).resolve())
              .addLore(selected ? TextFormat.GREEN + Text.key("adapt.golemancy.gui.channel_selected").resolve() : TextFormat.GRAY + Text.key("adapt.golemancy.gui.channel_select").resolve())
              .setEnchanted(selected)
              .onLeftClick(clicked -> toggleChannel(color))
              .setHint(MenuHint.toggle(selected))
              .setDescription(List.of()));
    }
    target.place(LEFT_EDGE, FOOTER_ROW, GolemTheme.element("golem-channels-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
        .setName(TextFormat.GRAY + Text.key("adapt.golemancy.gui.back").resolve())
        .onLeftClick(clicked -> show(Page.MAIN)));
    target.place(0, FOOTER_ROW, GolemGuiActions.channelElement("golem-channels-summary", mask)
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.gui.channels").withMarkup("channels", String.valueOf(channelList(mask))).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.channels_lore").withMarkup("count", String.valueOf(view.channels().slots())).resolve())
        .setRole(MenuRole.INFO));
    target.place(RIGHT_EDGE, FOOTER_ROW, GolemTheme.element("golem-channels-clear", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
        .setName(TextFormat.RED + Text.key("adapt.golemancy.gui.channels_clear").resolve())
        .onLeftClick(clicked -> mutate(() -> handle.setChannels(ChannelMask.NONE)))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.channels_clear").resolve()));
  }

  private void placeRunes(Placer target, GolemView view, GolemView before) {
    int slots = runeSlots(view);
    if (before != null) {
      int previous = runeSlots(before);
      for (int index = 0; index < previous; index++) {
        int position = GolemLayout.centeredPosition(index, previous);
        if (!occupies(position, slots)) {
          target.place(position, RUNE_ROW, null);
        }
      }
    }
    for (int index = 0; index < slots; index++) {
      target.place(GolemLayout.centeredPosition(index, slots), RUNE_ROW, runeElement(view, index));
    }
  }

  private static int runeSlots(GolemView view) {
    return Math.max(view.runes().layout().maxSlots(), view.runes().slotted().size());
  }

  private static boolean occupies(int position, int slots) {
    for (int index = 0; index < slots; index++) {
      if (GolemLayout.centeredPosition(index, slots) == position) {
        return true;
      }
    }
    return false;
  }

  private GolemElement runeElement(GolemView view, int index) {
    List<GolemRune> runes = view.runes().slotted();
    GolemRuneLayout layout = view.runes().layout();
    if (index < runes.size()) {
      GolemRune rune = runes.get(index);
      ItemRef item = GolemItems.rune(rune);
      GolemGuiActions.RuneNote note = GolemGuiActions.runeNote(layout, runes, index, view.body());
      List<String> lore = new ArrayList<>();
      lore.add(TextFormat.GRAY + Text.key("adapt.golemancy.item.rune.tier").withMarkup("tier", String.valueOf(rune.tier())).resolve());
      lore.add((note.active() ? TextFormat.GREEN : TextFormat.RED) + note.notice().resolve());
      if (note.active()) {
        for (String line : GolemRunes.describe(rune, view.runes().perks())) {
          lore.add(TextFormat.GRAY + line);
        }
      }
      List<String> description = List.copyOf(lore);
      lore.add(TextFormat.GRAY + Text.key("adapt.golemancy.gui.rune_remove").resolve());
      item.setLore(lore);
      return itemElement("golem-rune-" + index, item).onLeftClick(clicked -> takeRune(index, rune))
          .setRole(MenuRole.ACTION)
          .setDescription(description)
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.rune_remove").resolve());
    }
    if (index < layout.unlockedSlots()) {
      return GolemTheme.element("golem-rune-" + index, Id.minecraft("magenta_stained_glass_pane"), "gui", "golemancy", "slots", "rune_empty")
          .setName(TextFormat.LIGHT_PURPLE + Text.key("adapt.golemancy.gui.rune_empty").resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.rune_empty_lore").resolve())
          .onLeftClick(this::slotFromCursor)
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.rune_empty_lore").resolve());
    }
    return GolemTheme.element("golem-rune-" + index, Id.minecraft("black_stained_glass_pane"), "gui", "golemancy", "slots", "rune_locked")
        .setName(TextFormat.RED + Text.key("adapt.golemancy.gui.rune_locked").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.rune_unlock_level").withMarkup("level", String.valueOf(layout.slotUnlockLevel(index))).resolve())
        .setRole(MenuRole.INFO);
  }

  private void placeCarried(Placer target, GolemView view) {
    List<Carried> entries = readCarried(view);
    int hidden = view.carried().total() - entries.size();
    int cells = Math.max(cellCount(entries, hidden), cellCount(renderedCarried, renderedHidden));
    for (int index = 0; index < cells; index++) {
      if (!Objects.equals(carriedKey(entries, hidden, index), carriedKey(renderedCarried, renderedHidden, index))) {
        target.place(LEFT_EDGE + index % GolemLayout.WIDTH, CARRIED_ROW + index / GolemLayout.WIDTH, carriedCell(entries, hidden, index));
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
        AdaptApi.log().error("Skipping unreadable carried item on golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
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

  private GolemElement carriedCell(List<Carried> entries, int hidden, int index) {
    if (index < entries.size()) {
      Carried entry = entries.get(index);
      ItemRef item = entry.item();
      GolemElement element = itemElement("golem-carried-" + index, item.copy()).setCount(item.amount());
      for (String line : item.lore()) {
        element.addLore(line);
      }
      return takeable(element.setDescription(element.getLore()))
          .onLeftClick(clicked -> take(entry))
          .onShiftLeftClick(clicked -> take(entry));
    }
    if (index == entries.size() && hidden > 0) {
      return GolemTheme.element("golem-carried-more", Id.minecraft("paper"), "gui", "golemancy", "carried_more")
          .setName(TextFormat.GRAY + Text.key("adapt.golemancy.gui.more_carried").withMarkup("count", String.valueOf(hidden)).resolve())
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
        target.place(GolemLayout.centeredPosition(slot.ordinal(), slots), EQUIPMENT_ROW, equipmentElement(slot, worn));
      }
    }
  }

  private GolemElement equipmentElement(GolemEquipmentSlot slot, String encoded) {
    if (encoded != null) {
      try {
        ItemRef item = GolemState.decodeItem(encoded);
        GolemElement element = itemElement("golem-equipment-" + slot.id(), item.copy());
        for (String line : item.lore()) {
          element.addLore(line);
        }
        return takeable(element.setDescription(element.getLore()))
            .onLeftClick(clicked -> takeEquipment(slot, encoded))
            .onShiftLeftClick(clicked -> takeEquipment(slot, encoded));
      } catch (RuntimeException unreadable) {
        AdaptApi.log().error("Skipping unreadable equipped item on golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
      }
    }
    return GolemTheme.element("golem-equipment-" + slot.id(), Id.minecraft("cyan_stained_glass_pane"), "gui", "golemancy", "slots", "equipment_empty")
        .setName(TextFormat.AQUA + Text.key("adapt.golemancy.gui.equipment_empty").with("slot", slot.displayName()).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.equipment_empty_lore").resolve())
        .onLeftClick(clicked -> equipFromCursor(clicked, slot))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.equipment_empty_lore").resolve());
  }

  private GolemElement statsElement(GolemView view) {
    return GolemTheme.element("golem-stats", Id.minecraft("book"), "gui", "golemancy", "stats")
        .setName(TextFormat.AQUA + Text.key("adapt.golemancy.gui.stats").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.kills").withMarkup("value", String.valueOf(view.stats().kills())).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.damage").withMarkup("value", String.valueOf(view.stats().damage())).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.tasks").withMarkup("value", String.valueOf(view.stats().tasks())).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.items_moved").withMarkup("value", String.valueOf(view.stats().itemsMoved())).resolve())
        .setRole(MenuRole.INFO);
  }

  private void placeToggles(Placer target, GolemView view) {
    GolemView.Toggles toggles = view.toggles();
    target.place(COMBAT_SLOT, HEADER_ROW, combatElement(toggles));
    target.place(0, FOOTER_ROW, utilityElement(view));
    target.place(MASTERWORK_SLOT, FOOTER_ROW,
        GolemGuiActions.showsMasterwork(toggles.masterwork(), toggles.maxMasterworks()) ? masterworkElement(view) : null);
  }

  private GolemElement combatElement(GolemView.Toggles toggles) {
    GolemElement combat = GolemTheme.element("golem-combat", GolemGuiActions.combatIcon(toggles.combat(), toggles.combatLocked()), "gui", "golemancy", "combat")
        .setName((toggles.combat() ? TextFormat.AQUA : TextFormat.RED) + GolemGuiActions.combatName(toggles.combat(), toggles.combatLocked()).resolve())
        .addLore(TextFormat.GRAY + GolemGuiActions.combatLore(toggles.combat(), toggles.combatLocked()).resolve());
    if (toggles.combatLocked()) {
      return combat.setRole(MenuRole.INFO);
    }
    return combat.onLeftClick(clicked -> toggleCombat())
        .setHint(MenuHint.toggle(toggles.combat()))
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node_gui.toggle").resolve());
  }

  private GolemElement utilityElement(GolemView view) {
    GolemView.Toggles toggles = view.toggles();
    if (toggles.colossusAvailable()) {
      return GolemTheme.element("golem-colossus", GolemGuiActions.colossusIcon(toggles.colossus()), "gui", "golemancy", "colossus")
          .setName(TextFormat.GOLD + GolemGuiActions.colossusName(toggles.colossus()).resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.colossus_lore").resolve())
          .onLeftClick(clicked -> toggleColossus())
          .setHint(MenuHint.toggle(toggles.colossus()))
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.colossus_lore").resolve());
    }
    if (GolemGuiActions.hasSnowTrail(view.body())) {
      return GolemTheme.element("golem-snow-trail", GolemGuiActions.snowTrailIcon(toggles.snowTrail()), "gui", "golemancy", "snow_trail")
          .setName(TextFormat.AQUA + GolemGuiActions.snowTrailName(toggles.snowTrail()).resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.snow_trail_lore").resolve())
          .onLeftClick(clicked -> toggleSnowTrail())
          .setHint(MenuHint.toggle(toggles.snowTrail()))
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.snow_trail_lore").resolve());
    }
    return null;
  }

  private GolemElement essenceElement(GolemView view) {
    if (!GolemGuiActions.showsEssence(view.cores().slotted(), view.essence())) {
      return null;
    }
    return GolemTheme.element("golem-essence", Id.minecraft("experience_bottle"), "gui", "golemancy", "essence")
        .setName(TextFormat.GREEN + Text.key("adapt.golemancy.gatherer_core.essence").withMarkup("value", String.valueOf(view.essence())).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gatherer_core.essence_lore").resolve())
        .onLeftClick(clicked -> withdrawEssence())
        .setRole(MenuRole.ACTION)
        .setValue(Integer.toString(view.essence()))
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gatherer_core.essence_lore").resolve());
  }

  private void placeActions(Placer target, GolemView view) {
    if (GolemPacking.packs(player)) {
      target.place(RIGHT_EDGE - 1, FOOTER_ROW, GolemTheme.element("golem-pack", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "pack")
          .setName(TextFormat.AQUA + Text.key("adapt.golemancy.gui.pack").resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.pack_lore").resolve())
          .onLeftClick(clicked -> pack())
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.pack").resolve()));
    }
    target.place(RIGHT_EDGE, FOOTER_ROW, GolemTheme.element("golem-release", Id.minecraft("shears"), "gui", "golemancy", "release")
        .setName(TextFormat.RED + Text.key("adapt.golemancy.gui.release").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.release_lore").resolve())
        .onLeftClick(clicked -> confirmRelease())
        .setHint(MenuHint.destructive(Text.key("adapt.golemancy.gui.release").resolve()))
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.release").resolve()));
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
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.channels_full").withMarkup("count", String.valueOf(runes.channelSlots())));
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
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.rune_needed"));
      return;
    }
    GolemRune rune = held.get();
    ItemRef taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> slot(rune, taken));
  }

  private void equipFromCursor(MenuClick clicked, GolemEquipmentSlot slot) {
    ItemRef cursor = player.inventory().cursor();
    if (cursor.isEmpty() || GolemEquipmentSlot.isAir(cursor.type())) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.equipment_needed"));
      return;
    }
    if (!slot.accepts(cursor.type())) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.equipment_refused").with("slot", slot.displayName()));
      return;
    }
    ItemRef taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> equip(slot, taken));
  }

  private void equip(GolemEquipmentSlot slot, ItemRef taken) {
    if (handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
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
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
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
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.masterwork.heart_needed"));
      return;
    }
    ItemRef taken = takeOne(cursor);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> designate(taken));
  }

  private void designate(ItemRef taken) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null || handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
      return;
    }
    GolemRegistry.Designation designation = registry.designate(handle, GolemMastery.maxMasterworks(handle.owner()));
    if (designation != GolemRegistry.Designation.DESIGNATED) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemGuiActions.designationNotice(designation, registry.masterworks(handle.owner())));
      snapshot();
      return;
    }
    AdaptApi.scheduler().runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, Text.key("adapt.golemancy.masterwork.designated"));
    snapshot();
  }

  private void undesignate() {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
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
      AdaptApi.scheduler().runEntity(player, player::saveData);
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.masterwork.removed"));
      snapshot();
    });
  }

  private void slotCoreFromCursor(MenuClick clicked) {
    ItemRef cursor = player.inventory().cursor();
    Optional<GolemCoreType> held = GolemItems.coreOf(cursor);
    if (held.isEmpty()) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.masterwork.core_needed"));
      return;
    }
    GolemCoreType core = held.get();
    ItemRef heart = null;
    if (needsTwinHeart()) {
      heart = takeHeart();
      if (heart == null) {
        GolemGuiSupport.warn(player, Text.key("adapt.upgrade.golemancy.runeforged.heart_needed"));
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
        inventory.setItem(slot, item.amount() > 0 ? item : AdaptApi.platform().host().items().empty());
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
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
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
    AdaptApi.scheduler().runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, Text.key("adapt.golemancy.masterwork.core_slotted").with("core", core.displayName()));
    snapshot();
  }

  private void takeCore(GolemCoreType core) {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
        return;
      }
      if (!registry.removeCore(handle, core)) {
        snapshot();
        return;
      }
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      handBack(GolemItems.core(core));
      AdaptApi.scheduler().runEntity(player, player::saveData);
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.masterwork.core_removed").with("core", core.displayName()));
      snapshot();
    });
  }

  private ItemRef takeOne(ItemRef cursor) {
    ItemRef taken = cursor.copyWithAmount(1);
    cursor.setAmount(cursor.amount() - 1);
    player.inventory().setCursor(cursor.amount() > 0 ? cursor : AdaptApi.platform().host().items().empty());
    return taken;
  }

  private void slot(GolemRune rune, ItemRef taken) {
    if (handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
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
    AdaptApi.scheduler().runEntity(player, player::saveData);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
    GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.rune_slotted").with("aspect", rune.aspect().displayName()));
    snapshot();
  }

  private void takeRune(int index, GolemRune rune) {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      if (handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
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
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.rune_removed").with("aspect", rune.aspect().displayName()));
      snapshot();
    });
  }

  private void withdrawEssence() {
    MobRef golem = handle.entity();
    GolemGuiItems.fromGolem(golem, player, () -> {
      if (handle.isRetired() || !golem.isValid()) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
        return;
      }
      int points = handle.state().essencePoints();
      EssenceWithdrawal withdrawal = takeEssence(handle, player);
      if (withdrawal == EssenceWithdrawal.REFUSED) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
        return;
      }
      if (withdrawal == EssenceWithdrawal.EMPTY) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.gatherer_core.essence_empty"));
      } else {
        GolemGuiSupport.notify(player, Text.key("adapt.golemancy.gatherer_core.essence_taken").withMarkup("value", String.valueOf(points)));
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
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.gui.combat_locked_lore"));
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
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.home_set").withMarkup("x", String.valueOf(home.x())).withMarkup("y", String.valueOf(home.y())).withMarkup("z", String.valueOf(home.z())));
    });
  }

  private void armChestLink() {
    gestures.armLink(player.uuid(), handle, System.currentTimeMillis());
    window.close();
    GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.link_armed"));
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
    GolemConfirm.open(player,
        Text.key("adapt.golemancy.gui.release").resolve(),
        Text.key("adapt.golemancy.gui.release_confirm").resolve(),
        this::release,
        () -> open(player, handle, gestures));
  }

  private void release() {
    AdaptApi.scheduler().runEntity(handle.entity(), () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !registry.release(handle)) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.release_failed"));
        return;
      }
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.forget(handle));
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.released"));
    }, this::gone);
  }

  private void mutate(Runnable change) {
    AdaptApi.scheduler().runEntity(handle.entity(), () -> {
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
        Experience.award(player, points);
      }
    };
    if (AdaptApi.scheduler().runEntity(player, give, () -> GolemGuiItems.fromGolem(handle.entity(), player, restore))) {
      return EssenceWithdrawal.TAKEN;
    }
    restore.run();
    return EssenceWithdrawal.REFUSED;
  }

  private static String channelList(int mask) {
    List<Text> names = GolemGuiActions.channelNames(mask);
    List<String> texts = new ArrayList<>(names.size());
    for (Text name : names) {
      texts.add(name.resolve());
    }
    return String.join(", ", texts);
  }

  private static GolemElement itemElement(String id, ItemRef item) {
    return new GolemElement(id).setItem(item);
  }

  private static GolemElement takeable(GolemElement element) {
    String take = Text.key("adapt.golemancy.gui.take").resolve();
    return element.addLore(TextFormat.GRAY + take)
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, take)
        .setActionLabel(ClickKind.SHIFT_LEFT, take);
  }

  private static String coordinates(Text key, GolemAnchor anchor) {
    return key.withMarkup("x", String.valueOf(anchor.x())).withMarkup("y", String.valueOf(anchor.y())).withMarkup("z", String.valueOf(anchor.z())).resolve();
  }

  @FunctionalInterface
  private interface Placer {
    void place(int position, int row, GolemElement element);
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
