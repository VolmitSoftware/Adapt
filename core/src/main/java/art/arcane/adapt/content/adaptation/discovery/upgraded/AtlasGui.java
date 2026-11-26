package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.content.adaptation.discovery.DiscoverySixthSense;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/** The Explorer's Atlas menu: the cover readout, the search, waypoints, and one page of entries to pin. */
final class AtlasGui {
  static final int ROWS = 6;
  static final int ENTRY_ROWS = 4;
  static final int PER_PAGE = ENTRY_ROWS * GuiLayout.WIDTH;
  private static final String SEPARATOR = C.DARK_GRAY + " | ";

  private AtlasGui() {
  }

  static GuiWindow open(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record, int page) {
    List<AtlasRecord.Place> entries = entries(record, player.location());
    int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    int current = Math.max(0, Math.min(page, pages - 1));
    GuiWindow window = new GuiWindow(player);
    window.setViewportHeight(ROWS);
    window.setLayout(MenuLayout.GRID);
    window.setTitle(C.DARK_AQUA + AdaptLanguage.text(DiscoveryMessages.ATLAS_NAME));

    window.setElement(-4, 0, cover(omniscience, player, level, record));
    if (omniscience.searchUnlocked(level)) {
      window.setElement(0, 0, search(omniscience, player, level, record, current));
    }
    window.setElement(4, 0, newWaypoint(omniscience, player, level, record, current));

    if (entries.isEmpty()) {
      window.setElement(0, 2, new GuiElement("atlas-empty")
          .setMaterial(Id.minecraft("map"))
          .setName(C.GRAY + AdaptLanguage.text(DiscoveryMessages.ATLAS_EMPTY))
          .setRole(MenuRole.INFO));
    }
    int start = current * PER_PAGE;
    int end = Math.min(entries.size(), start + PER_PAGE);
    for (int index = start; index < end; index++) {
      int slot = index - start;
      window.setElement((slot % GuiLayout.WIDTH) - 4, 1 + (slot / GuiLayout.WIDTH),
          entry(omniscience, player, level, record, entries.get(index), current));
    }

    if (current > 0) {
      window.setElement(-4, ROWS - 1, new GuiElement("atlas-previous")
          .setMaterial(Id.minecraft("arrow"))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .setRole(MenuRole.ACTION)
          .onLeftClick((MenuClick e) -> open(omniscience, player, level, record, current - 1)));
    }
    if (pages > 1) {
      window.setElement(0, ROWS - 1, new GuiElement("atlas-page")
          .setMaterial(Id.minecraft("paper"))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.PAGE_OF, trusted("page", current + 1), trusted("pages", pages)))
          .setRole(MenuRole.INFO));
    }
    if (current < pages - 1) {
      window.setElement(4, ROWS - 1, new GuiElement("atlas-next")
          .setMaterial(Id.minecraft("arrow"))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .setRole(MenuRole.ACTION)
          .onLeftClick((MenuClick e) -> open(omniscience, player, level, record, current + 1)));
    }
    return window.open();
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

  private static GuiElement cover(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record) {
    GuiElement cover = new GuiElement("atlas-cover")
        .setMaterial(Id.minecraft("book"))
        .setEnchanted(true)
        .setName(C.AQUA + AdaptLanguage.text(DiscoveryMessages.ATLAS_NAME))
        .setRole(MenuRole.HEADER);
    String readout = omniscience.cover(player);
    if (!readout.isEmpty()) {
      for (String part : readout.split(Pattern.quote(SEPARATOR))) {
        cover.addLore(part);
      }
    }
    cover.addLore(C.GRAY + AdaptLanguage.text(DiscoveryMessages.ATLAS_BIOMES, trusted("count", C.WHITE.toString() + record.biomes.size())));
    cover.addLore(C.GRAY + AdaptLanguage.text(DiscoveryMessages.ATLAS_STRUCTURES, trusted("count", C.WHITE.toString() + record.structures.size())));
    cover.addLore(C.GRAY + AdaptLanguage.text(DiscoveryMessages.ATLAS_PINS,
        trusted("count", C.WHITE.toString() + record.pinned(omniscience.pinsAllowed(level)).size()), trusted("max", omniscience.pinsAllowed(level))));
    return cover;
  }

  private static GuiElement search(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record, int page) {
    String structure = DiscoverySixthSense.structureName(null, record.searchType(Omniscience.SEARCHABLE));
    GuiElement search = new GuiElement("atlas-search")
        .setMaterial(Id.minecraft("compass"))
        .setName(C.AQUA + AdaptLanguage.text(DiscoveryMessages.ATLAS_SEARCH, trusted("structure", C.WHITE + structure)))
        .addLore(C.GRAY + AdaptLanguage.text(DiscoveryMessages.ATLAS_SEARCH_COST, trusted("paper", 1),
            trusted("hunger", omniscience.getConfig().searchHunger)))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(DiscoveryMessages.ATLAS_SEARCH_ACTION))
        .setActionLabel(ClickKind.RIGHT, AdaptLanguage.text(DiscoveryMessages.ATLAS_NEXT_TYPE))
        .onLeftClick((MenuClick e) -> {
          if (omniscience.search(player)) {
            player.closeInventory();
          }
        })
        .onRightClick((MenuClick e) -> {
          omniscience.cycleSearchType(player);
          open(omniscience, player, level, record, page);
        });
    if (omniscience.searchRunning(player)) {
      search.addLore(C.YELLOW + AdaptLanguage.text(DiscoveryMessages.ATLAS_SEARCHING, trusted("structure", structure)));
    } else {
      long left = omniscience.searchCooldownLeft(player);
      if (left > 0L) {
        search.addLore(C.YELLOW + AdaptLanguage.text(DiscoveryMessages.ATLAS_SEARCH_COOLDOWN, trusted("time", Form.duration(left, 0))));
      }
    }
    return search;
  }

  private static GuiElement newWaypoint(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record, int page) {
    return new GuiElement("atlas-new-waypoint")
        .setMaterial(Id.minecraft("name_tag"))
        .setName(C.AQUA + AdaptLanguage.text(DiscoveryMessages.ATLAS_NEW_WAYPOINT))
        .addLore(C.GRAY + AdaptLanguage.text(DiscoveryMessages.ATLAS_WAYPOINTS,
            trusted("count", C.WHITE.toString() + record.waypoints.size()), trusted("max", omniscience.waypointsAllowed(level))))
        .setRole(MenuRole.ACTION)
        .setHint(MenuHint.text("", (MenuClick e, String text) -> {
          omniscience.addWaypoint(player, text);
          open(omniscience, player, level, record, page);
        }))
        .onLeftClick((MenuClick e) -> omniscience.promptWaypoint(player));
  }

  private static GuiElement entry(Omniscience omniscience, PlayerRef player, int level, AtlasRecord record,
                                  AtlasRecord.Place place, int page) {
    boolean pinned = record.pins.contains(place.id);
    GuiElement element = new GuiElement("atlas-" + place.id)
        .setMaterial(material(place))
        .setEnchanted(pinned)
        .setName(C.WHITE + Omniscience.symbol(place) + " " + Omniscience.displayName(place))
        .setRole(MenuRole.INFO);
    Loc origin = player.location();
    if (place.inWorld(origin.world() == null ? null : origin.world().uid())) {
      double dx = place.x - origin.x();
      double dz = place.z - origin.z();
      element.addLore(C.YELLOW + DiscoverySixthSense.compassDirection(dx, dz) + " " + C.GRAY + Math.round(Math.sqrt(dx * dx + dz * dz)));
    } else {
      element.addLore(C.GRAY + AdaptLanguage.text(DiscoveryMessages.ATLAS_ELSEWHERE, untrusted("world", place.worldName)));
    }
    if (place.kind == AtlasRecord.Kind.STRUCTURE) {
      element.addLore(place.looted
          ? C.GOLD + AdaptLanguage.text(DiscoveryMessages.ATLAS_LOOTED)
          : C.GREEN + AdaptLanguage.text(DiscoveryMessages.ATLAS_NOT_LOOTED));
    }
    if (place.kind == AtlasRecord.Kind.LEAD) {
      element.addLore(C.AQUA + AdaptLanguage.text(DiscoveryMessages.ATLAS_LEAD));
      return element;
    }
    if (pinned) {
      element.addLore(C.LIGHT_PURPLE + AdaptLanguage.text(DiscoveryMessages.ATLAS_PINNED));
    }
    element.setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(pinned ? DiscoveryMessages.ATLAS_UNPIN : DiscoveryMessages.ATLAS_PIN))
        .onLeftClick((MenuClick e) -> {
          omniscience.togglePin(player, place.id);
          open(omniscience, player, level, record, page);
        });
    Omniscience.Structure structure = place.kind == AtlasRecord.Kind.STRUCTURE ? Omniscience.structure(place) : null;
    if (structure != null) {
      for (Omniscience.StructureAction action : Omniscience.structureActions()) {
        TextKey label = action.label(player, structure);
        if (label != null) {
          element.setActionLabel(ClickKind.RIGHT, AdaptLanguage.text(label))
              .onRightClick((MenuClick e) -> {
                action.run(player, structure);
                open(omniscience, player, level, record, page);
              });
          break;
        }
      }
    }
    if (place.kind == AtlasRecord.Kind.WAYPOINT) {
      element.setActionLabel(ClickKind.SHIFT_RIGHT, AdaptLanguage.text(DiscoveryMessages.ATLAS_DELETE))
          .onShiftRightClick((MenuClick e) -> {
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
