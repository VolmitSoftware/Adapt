package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiConfirm;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class RosterGui {
  private static final Comparator<GolemRoster.GolemEntry> ORDER = Comparator
      .comparing((GolemRoster.GolemEntry entry) -> entry.status().ordinal())
      .thenComparing(entry -> entry.body().ordinal())
      .thenComparing(entry -> entry.id().toString());
  private static final int LEFT_EDGE = -4;
  private static final int RIGHT_EDGE = 4;

  private RosterGui() {
  }

  public static void open(Player player) {
    open(player, 0);
  }

  public static void open(Player player, int page) {
    if (player == null || !player.isOnline()) {
      return;
    }
    if (!GolemGuiSupport.ownsViewer(player)) {
      J.runEntity(player, () -> open(player, page));
      return;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getUniqueId())) {
      AdaptServer server = Adapt.instance.getAdaptServer();
      AdaptPlayer adaptPlayer = server == null ? null : server.getOnlineAdaptPlayer(player.getUniqueId());
      if (adaptPlayer == null) {
        return;
      }
      List<GolemRoster.GolemEntry> entries = entries(adaptPlayer.getData().getGolemancy());
      Map<UUID, GolemHandle> loaded = loaded(player.getUniqueId());
      GuiLayout.PagePlan plan = GuiLayout.plan(entries.size(), false);
      int current = GuiLayout.clampPage(page, plan.pageCount());
      int start = current * plan.itemsPerPage();
      int end = Math.min(entries.size(), start + plan.itemsPerPage());

      UIWindow window = new UIWindow(Adapt.instance, player);
      GuiTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(plan.rows());
      window.batch(() -> {
        if (entries.isEmpty()) {
          window.setElement(0, 0, GuiTheme.element("roster-empty", Material.PAPER, "gui", "golemancy", "roster_empty")
              .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_EMPTY)));
        }
        for (int index = start; index < end; index++) {
          int slot = index - start;
          GolemRoster.GolemEntry entry = entries.get(index);
          window.setElement(LEFT_EDGE + slot % GuiLayout.WIDTH, slot / GuiLayout.WIDTH,
              entryElement(player, window, entry, loaded.get(entry.id()), current));
        }
        if (plan.hasNavigationRow()) {
          placeNavigation(window, player, plan.rows() - 1, current, plan.pageCount());
        }
      });
      window.setTitle(AdaptLanguage.text(GolemancyMessages.ROSTER_TITLE));
      window.onClosed(closed -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), window));
      window.open();
      Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), window);
    }
  }

  private static List<GolemRoster.GolemEntry> entries(GolemRoster roster) {
    List<GolemRoster.GolemEntry> entries = new ArrayList<>();
    for (GolemRoster.GolemEntry entry : roster.golems()) {
      if (entry.body() != null) {
        entries.add(entry);
      }
    }
    entries.sort(ORDER);
    return entries;
  }

  private static Map<UUID, GolemHandle> loaded(UUID owner) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null) {
      return Map.of();
    }
    Map<UUID, GolemHandle> loaded = new HashMap<>();
    for (GolemHandle handle : registry.loadedOf(owner)) {
      loaded.put(handle.id(), handle);
    }
    return loaded;
  }

  private static Element entryElement(Player player, UIWindow window, GolemRoster.GolemEntry entry, GolemHandle handle, int page) {
    GolemBody body = entry.body();
    Element element = GuiTheme.element("roster-" + entry.id(), GolemGuiActions.bodyIcon(body), "gui", "golemancy", "roster_" + body.id())
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.ROSTER_ENTRY,
            trusted("body", AdaptLanguage.text(body.displayName())),
            trusted("core", AdaptLanguage.text(entry.core().displayName()))))
        .setProgress(1D);
    Mob golem = readable(handle);
    if (golem != null) {
      Location at = golem.getLocation();
      element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_HEALTH,
          trusted("health", (int) Math.ceil(Math.max(0D, golem.getHealth()))),
          trusted("maximum", Math.round(GolemVitals.maxHealth(golem)))));
      element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_LOCATION,
          untrusted("world", golem.getWorld().getName()),
          trusted("x", at.getBlockX()), trusted("y", at.getBlockY()), trusted("z", at.getBlockZ())));
    } else {
      element.addLore(C.GRAY + lastSeen(entry));
    }
    element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_STATUS,
        trusted("status", AdaptLanguage.text(GolemGuiActions.statusName(entry.status())))));
    element.addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_LOCATE));
    element.onLeftClick(clicked -> locate(player, window, entry, handle));
    if (GolemGuiActions.releasableFromRoster(entry.status())) {
      element.addLore(C.RED + AdaptLanguage.text(GolemancyMessages.ROSTER_RELEASE));
      element.onShiftRightClick(clicked -> confirmRelease(player, entry, page));
    }
    return element;
  }

  private static Mob readable(GolemHandle handle) {
    if (handle == null || handle.isRetired()) {
      return null;
    }
    Mob golem = handle.entity();
    if (!golem.isValid() || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(golem))) {
      return null;
    }
    return golem;
  }

  private static void locate(Player player, UIWindow window, GolemRoster.GolemEntry entry, GolemHandle handle) {
    RevealService reveal = GolemancyRuntime.reveal().orElse(null);
    World world = player.getWorld();
    Mob golem = readable(handle);
    boolean loaded = golem != null;
    boolean sameWorld = loaded ? world.equals(golem.getWorld()) : world.getUID().equals(entry.world());
    switch (GolemGuiActions.locateAction(loaded, sameWorld)) {
      case GLOW -> {
        window.close();
        if (reveal != null) {
          reveal.locate(player, handle);
        }
      }
      case POINT -> {
        window.close();
        if (reveal != null) {
          reveal.point(player, new Location(world, GolemGuiActions.chunkCenterX(entry.chunkKey()) + 0.5D,
              player.getLocation().getY(), GolemGuiActions.chunkCenterZ(entry.chunkKey()) + 0.5D));
        }
        GolemGuiSupport.notify(player, GolemancyMessages.ROSTER_LAST_SEEN, lastSeenArguments(entry));
      }
      case REPORT -> GolemGuiSupport.notify(player, GolemancyMessages.ROSTER_LAST_SEEN, lastSeenArguments(entry));
    }
  }

  private static void confirmRelease(Player player, GolemRoster.GolemEntry entry, int page) {
    GuiConfirm.open(player,
        AdaptLanguage.text(GolemancyMessages.GUI_RELEASE),
        AdaptLanguage.text(GolemGuiActions.releaseConfirm(entry.status())),
        () -> {
          GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
          if (registry != null && registry.releaseEntry(player.getUniqueId(), entry.id())) {
            GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RELEASED);
          } else {
            GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_RELEASE_FAILED);
          }
          open(player, page);
        },
        () -> open(player, page));
  }

  private static void placeNavigation(UIWindow window, Player player, int row, int current, int pages) {
    if (current > 0) {
      window.setElement(LEFT_EDGE, row, GuiTheme.element("roster-prev", Material.ARROW, "gui", "navigation", "previous")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .onLeftClick(clicked -> open(player, current - 1)));
    }
    if (current < pages - 1) {
      window.setElement(RIGHT_EDGE, row, GuiTheme.element("roster-next", Material.ARROW, "gui", "navigation", "next")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .onLeftClick(clicked -> open(player, current + 1)));
    }
  }

  private static String lastSeen(GolemRoster.GolemEntry entry) {
    return AdaptLanguage.text(GolemancyMessages.ROSTER_LAST_SEEN, lastSeenArguments(entry));
  }

  private static MessageArgument[] lastSeenArguments(GolemRoster.GolemEntry entry) {
    return new MessageArgument[]{
        untrusted("world", GolemGuiSupport.worldName(entry.world())),
        trusted("x", GolemGuiActions.chunkCenterX(entry.chunkKey())),
        trusted("z", GolemGuiActions.chunkCenterZ(entry.chunkKey()))
    };
  }
}
