package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
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

import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemPacking;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemVitals;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.overseer.Overseer;
import art.arcane.adapt.content.golem.reveal.RevealService;
import art.arcane.adapt.api.text.TextFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


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
      AdaptApi.scheduler().runEntity(player, () -> open(player, page));
      return;
    }
    Text.renderFor(player.uuid(), () -> {
      PlayerProfile profile = AdaptApi.isRunning() ? AdaptApi.players().find(player.uuid()).orElse(null) : null;
      if (profile == null) {
        return;
      }
      List<GolemRoster.GolemEntry> entries = entries(profile.section(GolemRoster.section()));
      Map<UUID, GolemHandle> loaded = loaded(player.uuid());
      boolean channelFilter = filtersChannels(player);
      boolean recalls = GolemPacking.recalls(player);
      boolean lattice = ServantFrame.servantMenuUnlocked(player);
      GolemLayout.PagePlan plan = GolemLayout.plan(entries.size(), channelFilter || lattice);
      int current = GolemLayout.clampPage(page, plan.pageCount());
      int start = current * plan.itemsPerPage();
      int end = Math.min(entries.size(), start + plan.itemsPerPage());
      RosterView shown = view(player, entries, loaded, current, channelFilter, recalls, lattice);

      GolemWindow window = new GolemWindow(player);
      GolemTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(plan.rows());
      window.setLayout(MenuLayout.LIST);
      window.setPaging(new MenuPaging(current, plan.pageCount(), entries.size()));
      if (entries.isEmpty()) {
        window.setElement(0, 0, GolemTheme.element("roster-empty", Id.minecraft("paper"), "gui", "golemancy", "roster_empty")
            .setName(TextFormat.GRAY + Text.key("adapt.golemancy.roster.empty").resolve())
            .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.roster.bind").resolve())
            .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.help.roster.worker").resolve())
            .setRole(MenuRole.INFO));
      }
      for (int index = start; index < end; index++) {
        int slot = index - start;
        GolemRoster.GolemEntry entry = entries.get(index);
        window.setElement(LEFT_EDGE + slot % GolemLayout.WIDTH, slot / GolemLayout.WIDTH,
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
      window.setTitle(Text.key("adapt.golemancy.roster.title").resolve());
      LiveRefresh<RosterView> live = new LiveRefresh<>(new LiveRefresh.Hooks<>(
          playerScheduler(player), playerScheduler(player),
          () -> readView(player, current),
          next -> redrawIfChanged(player, current, shown, next),
          window::close,
          () -> player.isOnline() && window.isVisible()));
      window.onClosed((GolemWindow closed) -> live.stop());
      window.open();
      live.start();
    });
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

  static RosterView view(PlayerRef player, List<GolemRoster.GolemEntry> entries, Map<UUID, GolemHandle> loaded, int page, boolean channelFilter,
                         boolean recalls, boolean lattice) {
    GolemLayout.PagePlan plan = GolemLayout.plan(entries.size(), channelFilter || lattice);
    int current = GolemLayout.clampPage(page, plan.pageCount());
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
    PlayerProfile profile = AdaptApi.isRunning() ? AdaptApi.players().find(player.uuid()).orElse(null) : null;
    if (profile == null) {
      return Optional.empty();
    }
    return Optional.of(view(player, entries(profile.section(GolemRoster.section())), loaded(player.uuid()), page,
        filtersChannels(player), GolemPacking.recalls(player), ServantFrame.servantMenuUnlocked(player)));
  }

  private static void redrawIfChanged(PlayerRef player, int page, RosterView shown, RosterView next) {
    if (!next.equals(shown)) {
      open(player, page);
    }
  }

  private static LiveRefresh.Scheduler playerScheduler(PlayerRef player) {
    return (task, delay, retired) -> AdaptApi.scheduler().runEntity(player, task, delay, retired);
  }

  private static void openCrookChannels(PlayerRef player, int page) {
    if (!GolemGuiSupport.ownsViewer(player)) {
      AdaptApi.scheduler().runEntity(player, () -> openCrookChannels(player, page));
      return;
    }
    Text.renderFor(player.uuid(), () -> {
      DyeColor selected = GolemItems.crookChannel(player.inventory().mainHand()).orElse(null);
      GolemWindow window = new GolemWindow(player);
      GolemTheme.apply(window, "golemancy-roster");
      window.setViewportHeight(CHANNEL_ROWS);
      window.setLayout(MenuLayout.LIST);
      window.setBreadcrumb(List.of(Text.key("adapt.golemancy.roster.title").resolve()));
      DyeColor[] colors = DyeColor.values();
      for (int index = 0; index < colors.length; index++) {
        DyeColor color = colors[index];
        window.setElement(GolemLayout.centeredPosition(index % CHANNELS_PER_ROW, CHANNELS_PER_ROW), index / CHANNELS_PER_ROW,
            channelChoice(player, color, color == selected, page));
      }
      window.setElement(LEFT_EDGE, CHANNEL_ROWS - 1, GolemTheme.element("roster-crook-channel-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
          .setName(TextFormat.GRAY + Text.key("adapt.golemancy.gui.back").resolve())
          .onLeftClick(clicked -> open(player, page)));
      window.setElement(RIGHT_EDGE, CHANNEL_ROWS - 1, GolemTheme.element("roster-crook-channel-all", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
          .setName((selected == null ? TextFormat.GREEN : TextFormat.YELLOW) + Text.key("adapt.golemancy.roster.crook_channel_all").resolve())
          .setEnchanted(selected == null)
          .onLeftClick(clicked -> setCrookChannel(player, null, page))
          .setRole(MenuRole.ENTRY)
          .setValue(selected == null ? Text.key("adapt.golemancy.gui.channel_selected").resolve() : "")
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.channel_select").resolve()));
      window.setTitle(Text.key("adapt.golemancy.roster.crook_channel_title").resolve());
      window.open();
    });
  }

  private static GolemElement crookChannelElement(PlayerRef player, int page) {
    DyeColor channel = GolemItems.crookChannel(player.inventory().mainHand()).orElse(null);
    int mask = channel == null ? ChannelMask.NONE : ChannelMask.of(channel);
    String name = (channel == null ? Text.key("adapt.golemancy.roster.crook_channel_all") : GolemGuiActions.channelName(mask)).resolve();
    return GolemGuiActions.channelElement("roster-crook-channel", mask)
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.roster.crook_channel").withMarkup("channel", String.valueOf(name)).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.roster.crook_channel_lore").resolve())
        .onLeftClick(clicked -> openCrookChannels(player, page))
        .setRole(MenuRole.NAVIGATION)
        .setValue(name)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.roster.crook_channel_lore").resolve());
  }

  private static GolemElement boneLatticeElement(PlayerRef player, int page) {
    return GolemTheme.element("roster-bone-lattice", Id.minecraft("bone_block"), "gui", "golemancy", "bone_lattice")
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.bone_frame.lattice").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.bone_frame.lattice_lore").resolve())
        .onLeftClick(clicked -> ServantFrame.frame().ifPresent(frame -> frame.openLattice(player, page)))
        .setRole(MenuRole.NAVIGATION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.bone_frame.lattice_lore").resolve());
  }

  private static GolemElement channelChoice(PlayerRef player, DyeColor color, boolean selected, int page) {
    int mask = ChannelMask.of(color);
    return GolemGuiActions.channelElement("roster-crook-channel-" + color.name().toLowerCase(Locale.ROOT), mask)
        .setName((selected ? TextFormat.GREEN : TextFormat.YELLOW) + GolemGuiActions.channelName(mask).resolve())
        .addLore(selected ? TextFormat.GREEN + Text.key("adapt.golemancy.gui.channel_selected").resolve() : TextFormat.GRAY + Text.key("adapt.golemancy.gui.channel_select").resolve())
        .setEnchanted(selected)
        .onLeftClick(clicked -> setCrookChannel(player, color, page))
        .setRole(MenuRole.ENTRY)
        .setValue(selected ? Text.key("adapt.golemancy.gui.channel_selected").resolve() : "")
        .setDescription(List.of())
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.channel_select").resolve());
  }

  private static void setCrookChannel(PlayerRef player, DyeColor channel, int page) {
    if (!filtersChannels(player)) {
      open(player, page);
      return;
    }
    PlayerInventoryRef inventory = player.inventory();
    ItemRef held = inventory.mainHand();
    if (!GolemItems.setCrookChannel(held, channel)) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.crook_needed"));
      open(player, page);
      return;
    }
    inventory.setMainHand(held);
    Text name = channel == null ? Text.key("adapt.golemancy.roster.crook_channel_all") : GolemGuiActions.channelName(ChannelMask.of(channel));
    GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.crook_channel").with("channel", name));
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

  private static GolemElement entryElement(EntryContext context, GolemRoster.GolemEntry entry, GolemHandle handle) {
    PlayerRef player = context.player();
    GolemWindow window = context.window();
    int page = context.page();
    GolemBody body = entry.body();
    GolemElement element = GolemTheme.element("roster-" + entry.id(), GolemGuiActions.bodyIcon(body), "gui", "golemancy", "roster_" + body.id())
        .setName(TextFormat.AQUA + Text.key("adapt.golemancy.roster.entry").with("body", body.displayName()).with("core", entry.core().displayName()).resolve());
    MobRef golem = readable(handle);
    if (golem != null) {
      Loc at = golem.location();
      element.addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.health").withMarkup("health", String.valueOf((int) Math.ceil(Math.max(0D, golem.health())))).withMarkup("maximum", String.valueOf(Math.round(GolemVitals.maxHealth(golem)))).resolve());
      element.addLore(TextFormat.GRAY + Text.key("adapt.golemancy.roster.location").with("world", golem.world().name()).withMarkup("x", String.valueOf(at.blockX())).withMarkup("y", String.valueOf(at.blockY())).withMarkup("z", String.valueOf(at.blockZ())).resolve());
    } else {
      element.addLore(TextFormat.GRAY + lastSeen(entry).resolve());
    }
    String status = GolemGuiActions.statusName(entry.status()).resolve();
    element.addLore(TextFormat.GRAY + Text.key("adapt.golemancy.roster.status").withMarkup("status", String.valueOf(status)).resolve());
    element.setRole(MenuRole.ENTRY).setValue(status).setDescription(element.getLore());
    element.addLore(TextFormat.DARK_GRAY + Text.key("adapt.golemancy.roster.locate").resolve());
    element.onLeftClick(clicked -> locate(player, window, entry, handle))
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.roster.locate").resolve());
    boolean loaded = handle != null && !handle.isRetired();
    if (context.recalls() && GolemGuiActions.recallable(entry.status(), loaded, loaded && player.world().equals(handle.entity().world()))) {
      element.addLore(TextFormat.AQUA + Text.key("adapt.golemancy.roster.recall").resolve());
      element.onRightClick(clicked -> recall(player, window, handle))
          .setActionLabel(ClickKind.RIGHT, Text.key("adapt.golemancy.roster.recall").resolve());
    }
    if (GolemGuiActions.releasableFromRoster(entry.status())) {
      element.addLore(TextFormat.RED + Text.key("adapt.golemancy.roster.release").resolve());
      element.onShiftRightClick(clicked -> confirmRelease(player, entry, page))
          .setActionLabel(ClickKind.SHIFT_RIGHT, Text.key("adapt.golemancy.roster.release").resolve());
    }
    return element;
  }

  private static void recall(PlayerRef player, GolemWindow window, GolemHandle handle) {
    window.close();
    GolemPacking.packer().ifPresent(packer -> packer.recall(player, handle));
  }

  private static MobRef readable(GolemHandle handle) {
    if (handle == null || handle.isRetired()) {
      return null;
    }
    MobRef golem = handle.entity();
    if (!golem.isValid() || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(golem))) {
      return null;
    }
    return golem;
  }

  private static void locate(PlayerRef player, GolemWindow window, GolemRoster.GolemEntry entry, GolemHandle handle) {
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
        GolemGuiSupport.notify(player, lastSeen(entry));
      }
      case REPORT -> GolemGuiSupport.notify(player, lastSeen(entry));
    }
  }

  private static void confirmRelease(PlayerRef player, GolemRoster.GolemEntry entry, int page) {
    GolemConfirm.open(player,
        Text.key("adapt.golemancy.gui.release").resolve(),
        GolemGuiActions.releaseConfirm(entry.status()).resolve(),
        () -> {
          GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
          if (registry != null && registry.releaseEntry(player.uuid(), entry.id())) {
            GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.released"));
          } else {
            GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.release_failed"));
          }
          open(player, page);
        },
        () -> open(player, page));
  }

  private static void placeNavigation(GolemWindow window, PlayerRef player, int row, int current, int pages) {
    if (current > 0) {
      window.setElement(LEFT_EDGE, row, GolemTheme.element("roster-prev", Id.minecraft("arrow"), "gui", "navigation", "previous")
          .setName(TextFormat.WHITE + Text.key("adapt.gui.common.previous").resolve())
          .onLeftClick(clicked -> open(player, current - 1)));
    }
    if (current < pages - 1) {
      window.setElement(RIGHT_EDGE, row, GolemTheme.element("roster-next", Id.minecraft("arrow"), "gui", "navigation", "next")
          .setName(TextFormat.WHITE + Text.key("adapt.gui.common.next").resolve())
          .onLeftClick(clicked -> open(player, current + 1)));
    }
  }

  public static Text lastSeen(GolemRoster.GolemEntry entry) {
    return Text.key("adapt.golemancy.roster.last_seen")
        .with("world", GolemGuiSupport.worldName(entry.world()))
        .withMarkup("x", String.valueOf(GolemGuiActions.chunkCenterX(entry.chunkKey())))
        .withMarkup("z", String.valueOf(GolemGuiActions.chunkCenterZ(entry.chunkKey())));
  }

  private record EntryContext(PlayerRef player, GolemWindow window, int page, boolean recalls) {
  }

  record RosterView(int page, int total, List<Row> rows, boolean channelFilter, boolean recalls, boolean lattice) {
  }

  record Row(UUID id, GolemBody body, GolemCoreType core, GolemRoster.Status status, UUID world, long chunkKey, boolean loaded,
             boolean sameWorld, int health, long maxHealth, String where, int x, int y, int z) {
  }
}
