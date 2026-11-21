package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.inventorygui.GuiConfirm;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemPacking;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.GolemHelpMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
  private static final int CHANNELS_PER_ROW = 8;
  private static final int CHANNEL_ROWS = 3;
  private static final int BONE_LATTICE_CELL = 1;

  private RosterGui() {
  }

  public static void open(PlayerRef player) {
    open(player, 0);
  }

  public static void open(PlayerRef player, int page) {
    if (player == null || !player.isOnline()) {
      return;
    }
    if (!GolemGuiSupport.ownsViewer(player)) {
      Tasks.runEntity(player, () -> open(player, page));
      return;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      AdaptCore core = AdaptCore.current();
      AdaptServer server = core == null ? null : core.getAdaptServer();
      AdaptPlayer adaptPlayer = server == null ? null : server.getOnlineAdaptPlayer(player.uuid());
      if (adaptPlayer == null) {
        return;
      }
      List<GolemRoster.GolemEntry> entries = entries(adaptPlayer.getData().getGolemancy());
      Map<UUID, GolemHandle> loaded = loaded(player.uuid());
      boolean channelFilter = filtersChannels(player);
      boolean recalls = GolemPacking.recalls(player);
      boolean lattice = ServantFrame.servantMenuUnlocked(player);
      GuiLayout.PagePlan plan = GuiLayout.plan(entries.size(), channelFilter || lattice);
      int current = GuiLayout.clampPage(page, plan.pageCount());
      int start = current * plan.itemsPerPage();
      int end = Math.min(entries.size(), start + plan.itemsPerPage());
      RosterView shown = view(player, entries, loaded, current, channelFilter, recalls, lattice);

      GuiWindow window = new GuiWindow(player);
      GuiTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(plan.rows());
      window.setLayout(MenuLayout.LIST);
      window.setPaging(new MenuPaging(current, plan.pageCount(), entries.size()));
      if (entries.isEmpty()) {
        window.setElement(0, 0, GuiTheme.element("roster-empty", Id.minecraft("paper"), "gui", "golemancy", "roster_empty")
            .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_EMPTY))
            .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.ROSTER_BIND))
            .addLore(C.GRAY + AdaptLanguage.text(GolemHelpMessages.ROSTER_WORKER))
            .setRole(MenuRole.INFO));
      }
      for (int index = start; index < end; index++) {
        int slot = index - start;
        GolemRoster.GolemEntry entry = entries.get(index);
        window.setElement(LEFT_EDGE + slot % GuiLayout.WIDTH, slot / GuiLayout.WIDTH,
            entryElement(new EntryContext(player, window, current, recalls), entry, loaded.get(entry.id())));
      }
      if (plan.hasNavigationRow()) {
        placeNavigation(window, player, plan.rows() - 1, current, plan.pageCount());
      }
      if (channelFilter) {
        window.setElement(0, plan.rows() - 1, crookChannelElement(player, current));
      }
      if (lattice) {
        window.setElement(BONE_LATTICE_CELL, plan.rows() - 1, boneLatticeElement(player, current));
      }
      window.setTitle(AdaptLanguage.text(GolemancyMessages.ROSTER_TITLE));
      LiveRefresh<RosterView> live = new LiveRefresh<>(new LiveRefresh.Hooks<>(
          playerScheduler(player), playerScheduler(player),
          () -> readView(player, current),
          next -> redrawIfChanged(player, current, shown, next),
          window::close,
          () -> player.isOnline() && window.isVisible()));
      window.onClosed((GuiWindow closed) -> live.stop());
      window.open();
      live.start();
    }
  }

  public static List<GolemRoster.GolemEntry> entries(GolemRoster roster) {
    List<GolemRoster.GolemEntry> entries = new ArrayList<>();
    for (GolemRoster.GolemEntry entry : roster.golems()) {
      if (entry.body() != null) {
        entries.add(entry);
      }
    }
    entries.sort(ORDER);
    return entries;
  }

  public static String lastSeen(GolemRoster.GolemEntry entry) {
    return AdaptLanguage.text(GolemancyMessages.ROSTER_LAST_SEEN, lastSeenArguments(entry));
  }

  static RosterView view(PlayerRef player, List<GolemRoster.GolemEntry> entries, Map<UUID, GolemHandle> loaded, int page, boolean channelFilter,
                         boolean recalls, boolean lattice) {
    GuiLayout.PagePlan plan = GuiLayout.plan(entries.size(), channelFilter || lattice);
    int current = GuiLayout.clampPage(page, plan.pageCount());
    int start = current * plan.itemsPerPage();
    int end = Math.min(entries.size(), start + plan.itemsPerPage());
    List<Row> rows = new ArrayList<>(Math.max(0, end - start));
    for (int index = start; index < end; index++) {
      GolemRoster.GolemEntry entry = entries.get(index);
      rows.add(row(player, entry, loaded.get(entry.id())));
    }
    return new RosterView(current, entries.size(), List.copyOf(rows), channelFilter, recalls, lattice);
  }

  private static Row row(PlayerRef player, GolemRoster.GolemEntry entry, GolemHandle handle) {
    MobRef golem = readable(handle);
    boolean loaded = handle != null && !handle.isRetired();
    boolean sameWorld = loaded && player.world().equals(handle.entity().world());
    if (golem == null) {
      return new Row(entry.id(), entry.body(), entry.core(), entry.status(), entry.world(), entry.chunkKey(), loaded, sameWorld,
          -1, -1L, "", 0, 0, 0);
    }
    Loc at = golem.location();
    return new Row(entry.id(), entry.body(), entry.core(), entry.status(), entry.world(), entry.chunkKey(), loaded, sameWorld,
        (int) Math.ceil(Math.max(0D, golem.health())), Math.round(GolemVitals.maxHealth(golem)), golem.world().name(),
        at.blockX(), at.blockY(), at.blockZ());
  }

  private static Optional<RosterView> readView(PlayerRef player, int page) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    AdaptPlayer adaptPlayer = server == null ? null : server.getOnlineAdaptPlayer(player.uuid());
    if (adaptPlayer == null) {
      return Optional.empty();
    }
    return Optional.of(view(player, entries(adaptPlayer.getData().getGolemancy()), loaded(player.uuid()), page,
        filtersChannels(player), GolemPacking.recalls(player), ServantFrame.servantMenuUnlocked(player)));
  }

  private static void redrawIfChanged(PlayerRef player, int page, RosterView shown, RosterView next) {
    if (!next.equals(shown)) {
      open(player, page);
    }
  }

  private static LiveRefresh.Scheduler playerScheduler(PlayerRef player) {
    return (task, delay, retired) -> Tasks.runEntity(player, task, delay, retired);
  }

  private static void openCrookChannels(PlayerRef player, int page) {
    if (!GolemGuiSupport.ownsViewer(player)) {
      Tasks.runEntity(player, () -> openCrookChannels(player, page));
      return;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      DyeColor selected = GolemItems.crookChannel(player.inventory().mainHand()).orElse(null);
      GuiWindow window = new GuiWindow(player);
      GuiTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(CHANNEL_ROWS);
      window.setLayout(MenuLayout.LIST);
      window.setBreadcrumb(List.of(AdaptLanguage.text(GolemancyMessages.ROSTER_TITLE)));
      DyeColor[] colors = DyeColor.values();
      for (int index = 0; index < colors.length; index++) {
        DyeColor color = colors[index];
        window.setElement(GuiLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), index / CHANNELS_PER_ROW,
            channelChoice(player, color, color == selected, page));
      }
      window.setElement(LEFT_EDGE, CHANNEL_ROWS - 1, GuiTheme.element("roster-crook-channel-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
          .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_BACK))
          .onLeftClick(clicked -> open(player, page)));
      window.setElement(RIGHT_EDGE, CHANNEL_ROWS - 1, GuiTheme.element("roster-crook-channel-all", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
          .setName((selected == null ? C.GREEN : C.YELLOW) + AdaptLanguage.text(GolemancyMessages.ROSTER_CROOK_CHANNEL_ALL))
          .setEnchanted(selected == null)
          .onLeftClick(clicked -> setCrookChannel(player, null, page))
          .setRole(MenuRole.ENTRY)
          .setValue(selected == null ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : "")
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT)));
      window.setTitle(AdaptLanguage.text(GolemancyMessages.ROSTER_CROOK_CHANNEL_TITLE));
      window.open();
    }
  }

  private static GuiElement crookChannelElement(PlayerRef player, int page) {
    DyeColor channel = GolemItems.crookChannel(player.inventory().mainHand()).orElse(null);
    int mask = channel == null ? ChannelMask.NONE : ChannelMask.of(channel);
    String name = AdaptLanguage.text(channel == null ? GolemancyMessages.ROSTER_CROOK_CHANNEL_ALL : GolemGuiActions.channelName(mask));
    return GolemGuiActions.channelElement("roster-crook-channel", mask)
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.ROSTER_CROOK_CHANNEL, trusted("channel", name)))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_CROOK_CHANNEL_LORE))
        .onLeftClick(clicked -> openCrookChannels(player, page))
        .setRole(MenuRole.NAVIGATION)
        .setValue(name)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.ROSTER_CROOK_CHANNEL_LORE));
  }

  private static GuiElement boneLatticeElement(PlayerRef player, int page) {
    return GuiTheme.element("roster-bone-lattice", Id.minecraft("bone_block"), "gui", "golemancy", "bone_lattice")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_LATTICE))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.BONE_FRAME_LATTICE_LORE))
        .onLeftClick(clicked -> ServantFrame.frame().ifPresent(frame -> frame.openLattice(player, page)))
        .setRole(MenuRole.NAVIGATION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.BONE_FRAME_LATTICE_LORE));
  }

  private static GuiElement channelChoice(PlayerRef player, DyeColor color, boolean selected, int page) {
    int mask = ChannelMask.of(color);
    return GolemGuiActions.channelElement("roster-crook-channel-" + color.name().toLowerCase(Locale.ROOT), mask)
        .setName((selected ? C.GREEN : C.YELLOW) + AdaptLanguage.text(GolemGuiActions.channelName(mask)))
        .addLore(selected ? C.GREEN + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT))
        .setEnchanted(selected)
        .onLeftClick(clicked -> setCrookChannel(player, color, page))
        .setRole(MenuRole.ENTRY)
        .setValue(selected ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : "")
        .setDescription(List.of())
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT));
  }

  private static void setCrookChannel(PlayerRef player, DyeColor channel, int page) {
    if (!filtersChannels(player)) {
      open(player, page);
      return;
    }
    PlayerInventoryRef inventory = player.inventory();
    ItemRef held = inventory.mainHand();
    if (!GolemItems.setCrookChannel(held, channel)) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_CROOK_NEEDED);
      open(player, page);
      return;
    }
    inventory.setMainHand(held);
    TextKey name = channel == null ? GolemancyMessages.ROSTER_CROOK_CHANNEL_ALL : GolemGuiActions.channelName(ChannelMask.of(channel));
    GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_CROOK_CHANNEL, trusted("channel", GolemGuiSupport.localized(name)));
    open(player, page);
  }

  private static boolean filtersChannels(PlayerRef player) {
    return Overseer.commander().map(commander -> commander.filtersChannels(player)).orElse(false);
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

  private static GuiElement entryElement(EntryContext context, GolemRoster.GolemEntry entry, GolemHandle handle) {
    PlayerRef player = context.player();
    GuiWindow window = context.window();
    int page = context.page();
    GolemBody body = entry.body();
    GuiElement element = GuiTheme.element("roster-" + entry.id(), GolemGuiActions.bodyIcon(body), "gui", "golemancy", "roster_" + body.id())
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.ROSTER_ENTRY,
            trusted("body", AdaptLanguage.text(body.displayName())),
            trusted("core", AdaptLanguage.text(entry.core().displayName()))));
    MobRef golem = readable(handle);
    if (golem != null) {
      Loc at = golem.location();
      element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_HEALTH,
          trusted("health", (int) Math.ceil(Math.max(0D, golem.health()))),
          trusted("maximum", Math.round(GolemVitals.maxHealth(golem)))));
      element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_LOCATION,
          untrusted("world", golem.world().name()),
          trusted("x", at.blockX()), trusted("y", at.blockY()), trusted("z", at.blockZ())));
    } else {
      element.addLore(C.GRAY + lastSeen(entry));
    }
    String status = AdaptLanguage.text(GolemGuiActions.statusName(entry.status()));
    element.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_STATUS, trusted("status", status)));
    element.setRole(MenuRole.ENTRY).setValue(status).setDescription(element.getLore());
    element.addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.ROSTER_LOCATE));
    element.onLeftClick(clicked -> locate(player, window, entry, handle))
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.ROSTER_LOCATE));
    boolean loaded = handle != null && !handle.isRetired();
    if (context.recalls() && GolemGuiActions.recallable(entry.status(), loaded, loaded && player.world().equals(handle.entity().world()))) {
      element.addLore(C.AQUA + AdaptLanguage.text(GolemancyMessages.ROSTER_RECALL));
      element.onRightClick(clicked -> recall(player, window, handle))
          .setActionLabel(ClickKind.RIGHT, AdaptLanguage.text(GolemancyMessages.ROSTER_RECALL));
    }
    if (GolemGuiActions.releasableFromRoster(entry.status())) {
      element.addLore(C.RED + AdaptLanguage.text(GolemancyMessages.ROSTER_RELEASE));
      element.onShiftRightClick(clicked -> confirmRelease(player, entry, page))
          .setActionLabel(ClickKind.SHIFT_RIGHT, AdaptLanguage.text(GolemancyMessages.ROSTER_RELEASE));
    }
    return element;
  }

  private static void recall(PlayerRef player, GuiWindow window, GolemHandle handle) {
    window.close();
    GolemPacking.packer().ifPresent(packer -> packer.recall(player, handle));
  }

  private static MobRef readable(GolemHandle handle) {
    if (handle == null || handle.isRetired()) {
      return null;
    }
    MobRef golem = handle.entity();
    if (!golem.isValid() || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(golem))) {
      return null;
    }
    return golem;
  }

  private static void locate(PlayerRef player, GuiWindow window, GolemRoster.GolemEntry entry, GolemHandle handle) {
    RevealService reveal = GolemancyRuntime.reveal().orElse(null);
    WorldRef world = player.world();
    MobRef golem = readable(handle);
    boolean loaded = golem != null;
    boolean sameWorld = loaded ? world.equals(golem.world()) : world.uid().equals(entry.world());
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
          reveal.point(player, new Loc(world, GolemGuiActions.chunkCenterX(entry.chunkKey()) + 0.5D,
              player.location().y(), GolemGuiActions.chunkCenterZ(entry.chunkKey()) + 0.5D));
        }
        GolemGuiSupport.notify(player, GolemancyMessages.ROSTER_LAST_SEEN, lastSeenArguments(entry));
      }
      case REPORT -> GolemGuiSupport.notify(player, GolemancyMessages.ROSTER_LAST_SEEN, lastSeenArguments(entry));
    }
  }

  private static void confirmRelease(PlayerRef player, GolemRoster.GolemEntry entry, int page) {
    GuiConfirm.open(player,
        AdaptLanguage.text(GolemancyMessages.GUI_RELEASE),
        AdaptLanguage.text(GolemGuiActions.releaseConfirm(entry.status())),
        () -> {
          GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
          if (registry != null && registry.releaseEntry(player.uuid(), entry.id())) {
            GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_RELEASED);
          } else {
            GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_RELEASE_FAILED);
          }
          open(player, page);
        },
        () -> open(player, page));
  }

  private static void placeNavigation(GuiWindow window, PlayerRef player, int row, int current, int pages) {
    if (current > 0) {
      window.setElement(LEFT_EDGE, row, GuiTheme.element("roster-prev", Id.minecraft("arrow"), "gui", "navigation", "previous")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .onLeftClick(clicked -> open(player, current - 1)));
    }
    if (current < pages - 1) {
      window.setElement(RIGHT_EDGE, row, GuiTheme.element("roster-next", Id.minecraft("arrow"), "gui", "navigation", "next")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .onLeftClick(clicked -> open(player, current + 1)));
    }
  }

  private static MessageArgument[] lastSeenArguments(GolemRoster.GolemEntry entry) {
    return new MessageArgument[]{
        untrusted("world", GolemGuiSupport.worldName(entry.world())),
        trusted("x", GolemGuiActions.chunkCenterX(entry.chunkKey())),
        trusted("z", GolemGuiActions.chunkCenterZ(entry.chunkKey()))
    };
  }

  private record EntryContext(PlayerRef player, GuiWindow window, int page, boolean recalls) {
  }

  record RosterView(int page, int total, List<Row> rows, boolean channelFilter, boolean recalls, boolean lattice) {
  }

  record Row(UUID id, GolemBody body, GolemCoreType core, GolemRoster.Status status, UUID world, long chunkKey, boolean loaded,
             boolean sameWorld, int health, long maxHealth, String where, int x, int y, int z) {
  }
}
