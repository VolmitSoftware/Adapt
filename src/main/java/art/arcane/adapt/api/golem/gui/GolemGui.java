package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemState;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class GolemGui {
  private static final int ROWS = 5;
  private static final int HEADER_ROW = 0;
  private static final int RUNE_ROW = 1;
  private static final int CARRIED_ROW = 2;
  private static final int FOOTER_ROW = 4;
  private static final int LEFT_EDGE = -4;
  private static final int RIGHT_EDGE = 4;

  private final Player player;
  private final GolemHandle handle;
  private final CrookGestures<GolemHandle> gestures;
  private UIWindow window;

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
        placeHeader(next, view);
        placeRunes(next, view);
        placeCarried(next, view);
        placeFooter(next, view);
      });
      next.setTitle(AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(view.body().displayName()))));
      next.onClosed(closed -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), next));
      window = next;
      next.open();
      Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), next);
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
    target.setElement(1, HEADER_ROW, GuiTheme.element("golem-channel", GolemGuiActions.channelIcon(view.channels()), "gui", "golemancy", "channel")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL,
            trusted("channel", AdaptLanguage.text(GolemGuiActions.channelName(view.channels())))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
        .setProgress(1D)
        .onLeftClick(clicked -> cycleChannel(true))
        .onRightClick(clicked -> cycleChannel(false)));
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

  private void placeRunes(UIWindow target, View view) {
    int slots = GolemGuiActions.runeSlots(view.body());
    for (int index = 0; index < slots; index++) {
      int position = GuiLayout.centeredPosition(index, slots);
      if (index < view.runes().size()) {
        target.setElement(position, RUNE_ROW, itemElement("golem-rune-" + index, GolemItems.rune(view.runes().get(index))));
        continue;
      }
      target.setElement(position, RUNE_ROW, GuiTheme.element("golem-rune-" + index, Material.BARRIER, "gui", "golemancy", "rune_locked")
          .setName(C.RED + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_LOCKED))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_RUNE_UNLOCK))
          .setProgress(1D));
    }
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
    Mob golem = handle.entity();
    boolean scheduled = J.runEntity(golem, () -> {
      if (!handle.removeCarried(entry.encoded())) {
        snapshot();
        return;
      }
      ItemStack item = entry.item().clone();
      if (!J.runEntity(player, () -> give(item))) {
        golem.getWorld().dropItemNaturally(golem.getLocation(), item);
      }
      snapshot();
    });
    if (!scheduled) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    }
  }

  private void give(ItemStack item) {
    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
    for (ItemStack leftover : leftovers.values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.requestUpdate(player));
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

  private record View(GolemBody body, double health, double maxHealth, boolean dormant, List<GolemCoreType> cores,
                      List<GolemRune> runes, GolemMode mode, int channels, GolemState.Anchor home, GolemState.Anchor homeChest,
                      List<Carried> carried, int carriedTotal, long kills, double damage, long tasks, long itemsMoved) {
    private static View of(GolemHandle handle) {
      GolemState state = handle.state();
      Mob golem = handle.entity();
      GolemState.Stats stats = state.stats();
      return new View(state.body(), golem.getHealth(), GolemVitals.maxHealth(golem), handle.isDormant(), state.cores(),
          state.runes(), state.mode(), state.channels(), state.home(), state.homeChest(), carried(handle), state.carried().size(),
          stats.kills(), stats.damage(), stats.tasks(), stats.itemsMoved());
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
