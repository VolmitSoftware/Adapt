package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.content.adaptation.discovery.DiscoveryStructures;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.GeneratedStructure;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What one player's Explorer's Atlas holds: the biomes and structures they found, their own waypoints, their pins and
 * the last unfound structure a search turned up. Kept with the player's Omniscience, never on the item, so it stays
 * private to them.
 */
final class AtlasRecord {
  private static final Gson GSON = new Gson();

  List<Place> biomes = new ArrayList<>();
  List<Place> structures = new ArrayList<>();
  List<Place> waypoints = new ArrayList<>();
  List<String> pins = new ArrayList<>();
  Place lead;
  int searchType;
  long nextWaypoint;

  enum Kind {
    BIOME,
    STRUCTURE,
    WAYPOINT,
    LEAD
  }

  enum Change {
    DONE,
    FULL,
    MISSING
  }

  static final class Place {
    String id;
    Kind kind;
    String type;
    String name;
    String world;
    String worldName;
    double x;
    double y;
    double z;
    double minX;
    double minY;
    double minZ;
    double maxX;
    double maxY;
    double maxZ;
    boolean looted;

    boolean inWorld(UUID worldId) {
      return worldId != null && worldId.toString().equals(world);
    }

    boolean holds(double atX, double atY, double atZ) {
      return DiscoveryStructures.contains(new Box(minX, minY, minZ, maxX, maxY, maxZ), atX, atY, atZ);
    }

    boolean holdsColumn(double atX, double atZ) {
      return atX >= minX && atX < maxX && atZ >= minZ && atZ < maxZ;
    }
  }

  static AtlasRecord parse(String json) {
    if (json == null || json.isEmpty()) {
      return new AtlasRecord();
    }
    try {
      AtlasRecord record = GSON.fromJson(json, AtlasRecord.class);
      return record == null ? new AtlasRecord() : record.normalized();
    } catch (JsonParseException invalid) {
      return new AtlasRecord();
    }
  }

  String json() {
    return GSON.toJson(this);
  }

  private AtlasRecord normalized() {
    biomes = biomes == null ? new ArrayList<>() : biomes;
    structures = structures == null ? new ArrayList<>() : structures;
    waypoints = waypoints == null ? new ArrayList<>() : waypoints;
    pins = pins == null ? new ArrayList<>() : pins;
    return this;
  }

  /** Records a biome the first time the player stands in it; false when it is already in the Atlas. */
  boolean recordBiome(Id biome, Loc at) {
    String id = "biome:" + biome;
    if (find(biomes, id) != null) {
      return false;
    }
    Place place = point(id, Kind.BIOME, biome.toString(), at);
    biomes.add(place);
    return true;
  }

  /**
   * Records a structure the player stands in or looted, the first time; null when it is already in the Atlas. Past
   * {@code cap} structures the oldest unpinned one is dropped.
   */
  Place recordStructure(GeneratedStructure structure, String name, Loc at, int cap) {
    Box bounds = structure.bounds();
    double centerX = bounds.minX() + bounds.widthX() * 0.5D;
    double centerZ = bounds.minZ() + bounds.widthZ() * 0.5D;
    String id = "structure:" + structure.type() + ":" + at.world().uid() + ":" + (int) Math.floor(centerX) + ":" + (int) Math.floor(centerZ);
    if (find(structures, id) != null) {
      return null;
    }
    Place place = point(id, Kind.STRUCTURE, structure.type().toString(), at);
    place.name = name;
    place.x = centerX;
    place.z = centerZ;
    bound(place, bounds);
    structures.add(place);
    while (structures.size() > Math.max(1, cap)) {
      Place oldest = null;
      for (Place candidate : structures) {
        if (!pins.contains(candidate.id)) {
          oldest = candidate;
          break;
        }
      }
      if (oldest == null) {
        break;
      }
      structures.remove(oldest);
    }
    if (lead != null && lead.type.equals(place.type) && lead.inWorld(at.world().uid()) && place.holdsColumn(lead.x, lead.z)) {
      lead = null;
    }
    return place;
  }

  /** The recorded structure whose bounds hold the spot. */
  Place structureAt(UUID worldId, double atX, double atY, double atZ) {
    for (Place place : structures) {
      if (place.inWorld(worldId) && place.holds(atX, atY, atZ)) {
        return place;
      }
    }
    return null;
  }

  /** Whether a structure of {@code type} covering the column at {@code (atX, atZ)} is already in the Atlas. */
  boolean known(UUID worldId, Id type, double atX, double atZ) {
    String key = type.toString();
    for (Place place : structures) {
      if (place.type.equals(key) && place.inWorld(worldId) && place.holdsColumn(atX, atZ)) {
        return true;
      }
    }
    return false;
  }

  Change addWaypoint(String name, Loc at, int max) {
    if (waypoints.size() >= max) {
      return Change.FULL;
    }
    nextWaypoint++;
    Place place = point("waypoint:" + nextWaypoint, Kind.WAYPOINT, "waypoint", at);
    place.name = name;
    waypoints.add(place);
    return Change.DONE;
  }

  boolean removeWaypoint(String id) {
    Place place = find(waypoints, id);
    if (place == null) {
      return false;
    }
    waypoints.remove(place);
    pins.remove(id);
    return true;
  }

  /** Pins or unpins an entry; an entry past the first {@code max} pins cannot be added. */
  Change togglePin(String id, int max) {
    if (pins.remove(id)) {
      return Change.DONE;
    }
    if (place(id) == null) {
      return Change.MISSING;
    }
    if (pins.size() >= max) {
      return Change.FULL;
    }
    pins.add(id);
    return Change.DONE;
  }

  /** The first {@code max} pinned entries that still exist. */
  List<Place> pinned(int max) {
    List<Place> pinned = new ArrayList<>(Math.min(max, pins.size()));
    for (String id : pins) {
      if (pinned.size() >= max) {
        break;
      }
      Place place = place(id);
      if (place != null) {
        pinned.add(place);
      }
    }
    return pinned;
  }

  Place place(String id) {
    Place found = find(waypoints, id);
    if (found == null) {
      found = find(structures, id);
    }
    if (found == null) {
      found = find(biomes, id);
    }
    if (found == null && lead != null && lead.id.equals(id)) {
      found = lead;
    }
    return found;
  }

  void lead(Id type, String name, UUID worldId, String worldName, double atX, double atZ, Box bounds) {
    Place place = new Place();
    place.id = "lead:" + type + ":" + (int) Math.floor(atX) + ":" + (int) Math.floor(atZ);
    place.kind = Kind.LEAD;
    place.type = type.toString();
    place.name = name;
    place.world = worldId.toString();
    place.worldName = worldName;
    place.x = atX;
    place.z = atZ;
    bound(place, bounds);
    lead = place;
  }

  Id searchType(List<Id> types) {
    return types.get(Math.floorMod(searchType, types.size()));
  }

  private static Place point(String id, Kind kind, String type, Loc at) {
    Place place = new Place();
    place.id = id;
    place.kind = kind;
    place.type = type;
    place.world = at.world().uid().toString();
    place.worldName = at.world().name();
    place.x = at.x();
    place.y = at.y();
    place.z = at.z();
    return place;
  }

  private static void bound(Place place, Box bounds) {
    place.minX = bounds.minX();
    place.minY = bounds.minY();
    place.minZ = bounds.minZ();
    place.maxX = bounds.maxX();
    place.maxY = bounds.maxY();
    place.maxZ = bounds.maxZ();
    place.y = bounds.minY();
  }

  private static Place find(List<Place> places, String id) {
    for (Place place : places) {
      if (place.id.equals(id)) {
        return place;
      }
    }
    return null;
  }
}
