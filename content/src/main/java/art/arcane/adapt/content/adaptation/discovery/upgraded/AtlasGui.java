package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.discovery.DiscoverySixthSense;
import art.arcane.adapt.content.menu.MenuEntry;
import art.arcane.adapt.content.menu.MenuSheet;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;


/** The Explorer's Atlas menu: the cover readout, the search, waypoints, and one page of entries to pin. */
final class AtlasGui {
  static final int ROWS = 6;
  static final int ENTRY_ROWS = 4;
  static final int PER_PAGE = ENTRY_ROWS * MenuSheet.WIDTH;
  private static final String SEPARATOR = TextFormat.DARK_GRAY + " | ";

  private AtlasGui() {
  }

  static ChestMenu open(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record, int page) {
    List<AtlasRecord.Place> entries = entries(record, player.location());
    int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    int current = Math.max(0, Math.min(page, pages - 1));
    MenuSheet window = MenuSheet.plain(ROWS);
    window.layout(MenuLayout.GRID);
    window.title(TextFormat.DARK_AQUA + Text.key("adapt.tool.discovery.explorers_atlas.name").resolve());

    window.set(-4, 0, cover(omniscience, player, level, record));
    if (omniscience.searchUnlocked(level)) {
      window.set(0, 0, search(omniscience, player, level, record, current));
    }
    window.set(4, 0, newWaypoint(omniscience, player, level, record, current));

    if (entries.isEmpty()) {
      window.set(0, 2, MenuEntry.of(Id.minecraft("map"))
          .name(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.empty").resolve())
          .role(MenuRole.INFO));
    }
    int start = current * PER_PAGE;
    int end = Math.min(entries.size(), start + PER_PAGE);
    for (int index = start; index < end; index++) {
      int slot = index - start;
      window.set((slot % MenuSheet.WIDTH) - 4, 1 + (slot / MenuSheet.WIDTH),
          entry(omniscience, player, level, record, entries.get(index), current));
    }

    if (current > 0) {
      window.set(-4, ROWS - 1, MenuEntry.of(Id.minecraft("arrow"))
          .name(TextFormat.WHITE + Text.key("adapt.gui.common.previous").resolve())
          .role(MenuRole.ACTION)
          .on(ClickKind.LEFT, (MenuClick e) -> open(omniscience, player, level, record, current - 1)));
    }
    if (pages > 1) {
      window.set(0, ROWS - 1, MenuEntry.of(Id.minecraft("paper"))
          .name(TextFormat.GRAY + Text.key("adapt.gui.common.page_of").withMarkup("page", String.valueOf(current + 1)).withMarkup("pages", String.valueOf(pages)).resolve())
          .role(MenuRole.INFO));
    }
    if (current < pages - 1) {
      window.set(4, ROWS - 1, MenuEntry.of(Id.minecraft("arrow"))
          .name(TextFormat.WHITE + Text.key("adapt.gui.common.next").resolve())
          .role(MenuRole.ACTION)
          .on(ClickKind.LEFT, (MenuClick e) -> open(omniscience, player, level, record, current + 1)));
    }
    ChestMenu menu = window.build();
    AdaptApi.platform().menus().open(player, menu);
    return menu;
  }

  /** Search find first, then pins, waypoints, structures nearest first, and biomes. */
  static List<AtlasRecord.Place> entries(AtlasRecord record, Loc origin) {
    List<AtlasRecord.Place> entries = new ArrayList<>();
    if (record.lead != null) {
      entries.add(record.lead);
    }
    for (AtlasRecord.Place pinned : record.pinned(Integer.MAX_VALUE)) {
      if (!entries.contains(pinned)) {
        entries.add(pinned);
      }
    }
    Comparator<AtlasRecord.Place> nearest = Comparator.comparingDouble((AtlasRecord.Place place) -> distance(place, origin));
    for (List<AtlasRecord.Place> group : List.of(record.waypoints, record.structures, record.biomes)) {
      List<AtlasRecord.Place> sorted = new ArrayList<>(group);
      sorted.sort(nearest);
      for (AtlasRecord.Place place : sorted) {
        if (!entries.contains(place)) {
          entries.add(place);
        }
      }
    }
    return entries;
  }

  private static double distance(AtlasRecord.Place place, Loc origin) {
    UUID worldId = origin.world() == null ? null : origin.world().uid();
    if (!place.inWorld(worldId)) {
      return Double.MAX_VALUE;
    }
    double dx = place.x - origin.x();
    double dz = place.z - origin.z();
    return Math.sqrt(dx * dx + dz * dz);
  }

  private static MenuEntry cover(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record) {
    MenuEntry cover = MenuEntry.of(Id.minecraft("book"))
        .glint(true)
        .name(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.name").resolve())
        .role(MenuRole.HEADER);
    String readout = omniscience.cover(player);
    if (!readout.isEmpty()) {
      for (String part : readout.split(Pattern.quote(SEPARATOR))) {
        cover.lore(part);
      }
    }
    cover.lore(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.biomes").withMarkup("count", TextFormat.WHITE.toString() + record.biomes.size()).resolve());
    cover.lore(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.structures").withMarkup("count", TextFormat.WHITE.toString() + record.structures.size()).resolve());
    cover.lore(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.pins").withMarkup("count", TextFormat.WHITE.toString() + record.pinned(omniscience.pinsAllowed(level)).size()).withMarkup("max", String.valueOf(omniscience.pinsAllowed(level))).resolve());
    return cover;
  }

  private static MenuEntry search(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record, int page) {
    String structure = DiscoverySixthSense.structureName(null, record.searchType(Omniscience.SEARCHABLE));
    MenuEntry search = MenuEntry.of(Id.minecraft("compass"))
        .name(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.search").withMarkup("structure", TextFormat.WHITE + structure).resolve())
        .lore(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.search_cost").withMarkup("paper", String.valueOf(1)).withMarkup("hunger", String.valueOf(omniscience.getConfig().searchHunger)).resolve())
        .role(MenuRole.ACTION)
        .label(ClickKind.LEFT, Text.key("adapt.tool.discovery.explorers_atlas.search_action").resolve())
        .label(ClickKind.RIGHT, Text.key("adapt.tool.discovery.explorers_atlas.next_type").resolve())
        .on(ClickKind.LEFT, (MenuClick e) -> {
          if (omniscience.search(player)) {
            player.closeInventory();
          }
        })
        .on(ClickKind.RIGHT, (MenuClick e) -> {
          omniscience.cycleSearchType(player);
          open(omniscience, player, level, record, page);
        });
    if (omniscience.searchRunning(player)) {
      search.lore(TextFormat.YELLOW + Text.key("adapt.tool.discovery.explorers_atlas.searching").withMarkup("structure", structure).resolve());
    } else {
      long left = omniscience.searchCooldownLeft(player);
      if (left > 0L) {
        search.lore(TextFormat.YELLOW + Text.key("adapt.tool.discovery.explorers_atlas.search_cooldown").withMarkup("time", Format.duration(left, 0)).resolve());
      }
    }
    return search;
  }

  private static MenuEntry newWaypoint(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record, int page) {
    return MenuEntry.of(Id.minecraft("name_tag"))
        .name(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.new_waypoint").resolve())
        .lore(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.waypoints").withMarkup("count", TextFormat.WHITE.toString() + record.waypoints.size()).withMarkup("max", String.valueOf(omniscience.waypointsAllowed(level))).resolve())
        .role(MenuRole.ACTION)
        .hint(MenuHint.text("", (MenuClick e, String text) -> {
          omniscience.addWaypoint(player, text);
          open(omniscience, player, level, record, page);
        }))
        .on(ClickKind.LEFT, (MenuClick e) -> omniscience.promptWaypoint(player));
  }

  private static MenuEntry entry(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record,
                                  AtlasRecord.Place place, int page) {
    boolean pinned = record.pins.contains(place.id);
    MenuEntry element = MenuEntry.of(material(place))
        .glint(pinned)
        .name(TextFormat.WHITE + Omniscience.symbol(place) + " " + Omniscience.displayName(place))
        .role(MenuRole.INFO);
    Loc origin = player.location();
    if (place.inWorld(origin.world() == null ? null : origin.world().uid())) {
      double dx = place.x - origin.x();
      double dz = place.z - origin.z();
      element.lore(TextFormat.YELLOW + DiscoverySixthSense.compassDirection(dx, dz) + " " + TextFormat.GRAY + Math.round(Math.sqrt(dx * dx + dz * dz)));
    } else {
      element.lore(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.elsewhere").with("world", place.worldName).resolve());
    }
    if (place.kind == AtlasRecord.Kind.STRUCTURE) {
      element.lore(place.looted
          ? TextFormat.GOLD + Text.key("adapt.tool.discovery.explorers_atlas.looted").resolve()
          : TextFormat.GREEN + Text.key("adapt.tool.discovery.explorers_atlas.not_looted").resolve());
    }
    if (place.kind == AtlasRecord.Kind.LEAD) {
      element.lore(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.lead").resolve());
      return element;
    }
    if (pinned) {
      element.lore(TextFormat.LIGHT_PURPLE + Text.key("adapt.tool.discovery.explorers_atlas.pinned").resolve());
    }
    element.role(MenuRole.ACTION)
        .label(ClickKind.LEFT, (pinned ? Text.key("adapt.tool.discovery.explorers_atlas.unpin") : Text.key("adapt.tool.discovery.explorers_atlas.pin")).resolve())
        .on(ClickKind.LEFT, (MenuClick e) -> {
          omniscience.togglePin(player, place.id);
          open(omniscience, player, level, record, page);
        });
    Omniscience.Structure structure = place.kind == AtlasRecord.Kind.STRUCTURE ? Omniscience.structure(place) : null;
    if (structure != null) {
      for (Omniscience.StructureAction action : Omniscience.structureActions()) {
        Text label = action.label(player, structure);
        if (label != null) {
          element.label(ClickKind.RIGHT, label.resolve())
              .on(ClickKind.RIGHT, (MenuClick e) -> {
                action.run(player, structure);
                open(omniscience, player, level, record, page);
              });
          break;
        }
      }
    }
    if (place.kind == AtlasRecord.Kind.WAYPOINT) {
      element.label(ClickKind.SHIFT_RIGHT, Text.key("adapt.tool.discovery.explorers_atlas.delete").resolve())
          .on(ClickKind.SHIFT_RIGHT, (MenuClick e) -> {
            omniscience.removeWaypoint(player, place.id);
            open(omniscience, player, level, record, page);
          });
    }
    return element;
  }

  private static Id material(AtlasRecord.Place place) {
    return switch (place.kind) {
      case WAYPOINT -> Id.minecraft("lodestone");
      case BIOME -> Id.minecraft("grass_block");
      case LEAD -> Id.minecraft("compass");
      case STRUCTURE -> place.looted ? Id.minecraft("map") : Id.minecraft("filled_map");
    };
  }
}
