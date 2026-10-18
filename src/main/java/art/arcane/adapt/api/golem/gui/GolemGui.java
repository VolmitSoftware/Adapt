package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemPacking;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
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

  private final Player player;
  private final GolemHandle handle;
  private final CrookGestures<GolemHandle> gestures;
  private UIWindow window;
  private Page page = Page.MAIN;

  private GolemGui(Player player, GolemHandle handle, CrookGestures<GolemHandle> gestures) {
    this.player = player;
    this.handle = handle;
    this.gestures = gestures;
  }

  public static void open(Player player, GolemHandle handle, CrookGestures<GolemHandle> gestures) {
    new GolemGui(player, handle, gestures).refresh();
  }

  private void refresh() {
    if (!J.runEntity(handle.entity(), this::snapshot)) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    }
  }

  private void snapshot() {
    if (handle.isRetired() || !handle.entity().isValid()) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    View view = View.of(handle);
    J.runEntity(player, () -> render(view));
  }

  private void render(View view) {
    if (!player.isOnline() || (window != null && !window.isVisible())) {
      return;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getUniqueId())) {
      UIWindow next = new UIWindow(Adapt.instance, player);
      GuiTheme.apply(next, "golemancy-golem");
      next.setViewportHeight(ROWS);
      next.batch(() -> {
        if (page == Page.CHANNELS) {
          placeChannelPicker(next, view);
          return;
        }
        placeHeader(next, view);
        placeRunes(next, view);
        placeCarried(next, view);
        placeFooter(next, view);
      });
      next.setTitle(page == Page.CHANNELS
          ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_TITLE)
          : AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(view.body().displayName()))));
      window = next;
      next.open();
      Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), next);
      Inventory top = UIWindow.inventoryViewTopInventory(player.getOpenInventory());
      next.onClosed(closed -> closed(next, top));
      if (top != null) {
        GuiCursor.track(player.getUniqueId(), top);
      }
    }
  }

  private void closed(UIWindow closed, Inventory top) {
    Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), closed);
    if (top != null) {
      GuiCursor.untrack(player.getUniqueId(), top);
    }
  }

  private void placeHeader(UIWindow target, View view) {
    Element body = GuiTheme.element("golem-body", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "body")
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(view.body().displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_HEALTH,
            trusted("health", (int) Math.ceil(Math.max(0D, view.health()))),
            trusted("maximum", Math.round(Math.max(0D, view.maxHealth())))));
    if (view.dormant()) {
      body.addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.GUI_DORMANT));
    }
    target.setElement(LEFT_EDGE, HEADER_ROW, body.setProgress(1D));
    for (int index = 0; index < view.cores().size(); index++) {
      target.setElement(LEFT_EDGE + 1 + index, HEADER_ROW, itemElement("golem-core-" + index, GolemItems.core(view.cores().get(index))));
    }
    target.setElement(0, HEADER_ROW, GuiTheme.element("golem-mode", GolemGuiActions.modeIcon(view.mode()), "gui", "golemancy", "mode")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_MODE, trusted("mode", AdaptLanguage.text(view.mode().displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
        .setProgress(1D)
        .onLeftClick(clicked -> cycleMode(true))
        .onRightClick(clicked -> cycleMode(false)));
    target.setElement(1, HEADER_ROW, channelElement(view));
    target.setElement(RIGHT_EDGE - 1, HEADER_ROW, GuiTheme.element("golem-home", Material.COMPASS, "gui", "golemancy", "home")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_SET_HOME))
        .addLore(C.GRAY + coordinates(GolemancyMessages.GUI_HOME, view.home()))
        .setProgress(1D)
        .onLeftClick(clicked -> setHome()));
    Element chest = GuiTheme.element("golem-chest", Material.CHEST, "gui", "golemancy", "chest")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_LINK_CHEST))
        .addLore(C.GRAY + (view.homeChest() == null
            ? AdaptLanguage.text(GolemancyMessages.GUI_NO_HOME_CHEST)
            : coordinates(GolemancyMessages.GUI_HOME_CHEST, view.homeChest())))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.GUI_LINK_HINT))
        .setProgress(1D)
        .onLeftClick(clicked -> armChestLink());
    target.setElement(RIGHT_EDGE, HEADER_ROW, chest);
  }

  private Element channelElement(View view) {
    if (view.channelSlots() <= 1) {
      return GuiTheme.element("golem-channel", GolemGuiActions.channelIcon(view.channels()), "gui", "golemancy", "channel")
          .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL,
              trusted("channel", AdaptLanguage.text(GolemGuiActions.channelName(view.channels())))))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
          .setProgress(1D)
          .onLeftClick(clicked -> cycleChannel(true))
          .onRightClick(clicked -> cycleChannel(false));
    }
    return GuiTheme.element("golem-channel", GolemGuiActions.channelIcon(view.channels()), "gui", "golemancy", "channel")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS, trusted("channels", channelList(view.channels()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_LORE, trusted("count", view.channelSlots())))
        .setProgress(1D)
        .onLeftClick(clicked -> show(Page.CHANNELS));
  }

  private void placeChannelPicker(UIWindow target, View view) {
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      int mask = ChannelMask.of(color);
      boolean selected = ChannelMask.contains(view.channels(), color);
      target.setElement(GuiLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), index / CHANNELS_PER_ROW,
          GuiTheme.element("golem-channel-" + color.name().toLowerCase(Locale.ROOT), GolemGuiActions.channelIcon(mask), "gui", "golemancy", "channel")
              .setName((selected ? C.GREEN : C.YELLOW) + AdaptLanguage.text(GolemGuiActions.channelName(mask)))
              .addLore(selected ? C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT))
              .setEnchanted(selected)
              .setProgress(1D)
              .onLeftClick(clicked -> toggleChannel(color)));
    }
    target.setElement(LEFT_EDGE, FOOTER_ROW, GuiTheme.element("golem-channels-back", Material.ARROW, "gui", "golemancy", "back")
        .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_BACK))
        .setProgress(1D)
        .onLeftClick(clicked -> show(Page.MAIN)));
    target.setElement(0, FOOTER_ROW, GuiTheme.element("golem-channels-summary", GolemGuiActions.channelIcon(view.channels()), "gui", "golemancy", "channel")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS, trusted("channels", channelList(view.channels()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_LORE, trusted("count", view.channelSlots())))
        .setProgress(1D));
    target.setElement(RIGHT_EDGE, FOOTER_ROW, GuiTheme.element("golem-channels-clear", Material.GLASS_BOTTLE, "gui", "golemancy", "channels_clear")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_CHANNELS_CLEAR))
        .setProgress(1D)
        .onLeftClick(clicked -> mutate(() -> handle.setChannels(ChannelMask.NONE))));
  }

  private void placeRunes(UIWindow target, View view) {
    int slots = Math.max(view.runeLayout().maxSlots(), view.runes().size());
    for (int index = 0; index < slots; index++) {
      target.setElement(GuiLayout.centeredPosition(index, slots), RUNE_ROW, runeElement(view, index));
    }
  }

  private Element runeElement(View view, int index) {
    List<GolemRune> runes = view.runes();
    GolemRuneLayout layout = view.runeLayout();
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
      GolemGuiActions.RuneNote note = GolemGuiActions.runeNote(layout, runes, index);
      return element.addLore((note.active() ? C.GREEN : C.RED) + AdaptLanguage.text(note.key(),
              trusted("tier", rune.tier()), trusted("level", note.level()),
              trusted("aspect", AdaptLanguage.text(rune.aspect().displayName())), trusted("body", AdaptLanguage.text(view.body().displayName()))))
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

  private void placeCarried(UIWindow target, View view) {
    List<Carried> carried = view.carried();
    for (int index = 0; index < carried.size(); index++) {
      Carried entry = carried.get(index);
      ItemStack item = entry.item();
      Element element = itemElement("golem-carried-" + index, item.clone()).setCount(item.getAmount());
      ItemMeta meta = item.getItemMeta();
      List<String> lore = meta == null || meta.getLore() == null ? List.of() : meta.getLore();
      for (String line : lore) {
        element.addLore(line);
      }
      element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_TAKE))
          .onLeftClick(clicked -> take(entry))
          .onShiftLeftClick(clicked -> take(entry));
      target.setElement(LEFT_EDGE + index % GuiLayout.WIDTH, CARRIED_ROW + index / GuiLayout.WIDTH, element);
    }
    int hidden = view.carriedTotal() - carried.size();
    if (hidden > 0) {
      int slot = carried.size();
      target.setElement(LEFT_EDGE + slot % GuiLayout.WIDTH, CARRIED_ROW + slot / GuiLayout.WIDTH,
          GuiTheme.element("golem-carried-more", Material.PAPER, "gui", "golemancy", "carried_more")
              .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_MORE_CARRIED, trusted("count", hidden)))
              .setProgress(1D));
    }
  }

  private void placeFooter(UIWindow target, View view) {
    target.setElement(LEFT_EDGE, FOOTER_ROW, GuiTheme.element("golem-stats", Material.BOOK, "gui", "golemancy", "stats")
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_STATS))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_KILLS, trusted("value", view.kills())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_DAMAGE, trusted("value", String.format(Locale.ROOT, "%.1f", view.damage()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_TASKS, trusted("value", view.tasks())))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_ITEMS_MOVED, trusted("value", view.itemsMoved())))
        .setProgress(1D));
    if (GolemGuiActions.hasSnowTrail(view.body())) {
      target.setElement(0, FOOTER_ROW, GuiTheme.element("golem-snow-trail", GolemGuiActions.snowTrailIcon(view.snowTrail()), "gui", "golemancy", "snow_trail")
          .setName(C.AQUA + AdaptLanguage.text(GolemGuiActions.snowTrailName(view.snowTrail())))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_SNOW_TRAIL_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> toggleSnowTrail()));
    }
    if (view.colossusAvailable()) {
      target.setElement(0, FOOTER_ROW, GuiTheme.element("golem-colossus", GolemGuiActions.colossusIcon(view.colossus()), "gui", "golemancy", "colossus")
          .setName(C.GOLD + AdaptLanguage.text(GolemGuiActions.colossusName(view.colossus())))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_COLOSSUS_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> toggleColossus()));
    }
    if (GolemGuiActions.showsEssence(view.cores(), view.essence())) {
      target.setElement(ESSENCE_SLOT, FOOTER_ROW, GuiTheme.element("golem-essence", Material.EXPERIENCE_BOTTLE, "gui", "golemancy", "essence")
          .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.GATHERER_CORE_ESSENCE, trusted("value", view.essence())))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GATHERER_CORE_ESSENCE_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> withdrawEssence()));
    }
    if (GolemPacking.packs(player)) {
      target.setElement(RIGHT_EDGE - 1, FOOTER_ROW, GuiTheme.element("golem-pack", GolemGuiActions.bodyIcon(view.body()), "gui", "golemancy", "pack")
          .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.GUI_PACK))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_PACK_LORE))
          .setProgress(1D)
          .onLeftClick(clicked -> pack()));
    }
    target.setElement(RIGHT_EDGE, FOOTER_ROW, GuiTheme.element("golem-release", Material.SHEARS, "gui", "golemancy", "release")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_RELEASE))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RELEASE_LORE))
        .setProgress(1D)
        .onLeftClick(clicked -> confirmRelease()));
  }

  private void cycleMode(boolean forward) {
    mutate(() -> handle.setMode(GolemGuiActions.cycleMode(handle.core(), handle.state().mode(), forward)));
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
    ItemStack taken = cursor.clone();
    taken.setAmount(1);
    cursor.setAmount(cursor.getAmount() - 1);
    player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
    GolemGuiItems.toGolem(handle.entity(), player, taken, () -> slot(rune, taken));
  }

  private void slot(GolemRune rune, ItemStack taken) {
    if (handle.isRetired() || !handle.entity().isValid()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
      return;
    }
    List<GolemRune> runes = handle.state().runes();
    Optional<GolemRuneLayout.Refusal> refusal = GolemRunes.layout(handle.body(), handle.owner()).refuse(runes, rune.aspect());
    if (refusal.isPresent()) {
      handBack(taken);
      GolemGuiSupport.warn(player, GolemGuiActions.refusalMessage(refusal.get()),
          trusted("aspect", GolemGuiSupport.localized(rune.aspect().displayName())), trusted("body", GolemGuiSupport.localized(handle.body().displayName())));
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
      GolemStats.recompute(handle);
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      handBack(GolemItems.rune(rune));
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
    boolean scheduled = J.runEntity(handle.entity(), () -> {
      GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
      if (registry == null || handle.isRetired() || !registry.release(handle)) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_RELEASE_FAILED);
        return;
      }
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.forget(handle));
      GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RELEASED);
    });
    if (!scheduled) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    }
  }

  private void mutate(Runnable change) {
    boolean scheduled = J.runEntity(handle.entity(), () -> {
      if (handle.isRetired() || !handle.entity().isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      change.run();
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.refresh(handle));
      snapshot();
    });
    if (!scheduled) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    }
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

  private record View(GolemBody body, double health, double maxHealth, boolean dormant, List<GolemCoreType> cores,
                      List<GolemRune> runes, GolemRuneLayout runeLayout, GolemMode mode, int channels, int channelSlots,
                      GolemState.Anchor home, GolemState.Anchor homeChest, List<Carried> carried, int carriedTotal, long kills, double damage,
                      long tasks, long itemsMoved, boolean snowTrail, boolean colossus, boolean colossusAvailable, int essence) {
    private static View of(GolemHandle handle) {
      GolemState state = handle.state();
      Mob golem = handle.entity();
      GolemState.Stats stats = state.stats();
      GolemRuneEffects runes = GolemRunes.effects(handle);
      return new View(state.body(), golem.getHealth(), GolemVitals.maxHealth(golem), handle.isDormant(), state.cores(),
          state.runes(), GolemRunes.layout(state.body(), handle.owner()), state.mode(), runes.channels(state.channels()), runes.channelSlots(),
          state.home(), state.homeChest(), carried(handle), state.carried().size(), stats.kills(), stats.damage(), stats.tasks(),
          stats.itemsMoved(), state.snowTrail(), state.colossus(), GolemColossus.form().map(form -> form.available(handle)).orElse(false),
          state.essencePoints());
    }

    private static List<Carried> carried(GolemHandle handle) {
      List<String> stored = handle.state().carried();
      int shown = GolemGuiActions.carriedShown(stored.size());
      List<Carried> carried = new ArrayList<>(shown);
      for (String encoded : stored) {
        if (carried.size() >= shown) {
          break;
        }
        try {
          carried.add(new Carried(encoded, NMS.deserializeStack(encoded)));
        } catch (RuntimeException unreadable) {
          Adapt.warn("Skipping unreadable carried item on golem " + handle.id() + " in its menu; the stored entry is kept.", unreadable);
        }
      }
      return List.copyOf(carried);
    }
  }
}
