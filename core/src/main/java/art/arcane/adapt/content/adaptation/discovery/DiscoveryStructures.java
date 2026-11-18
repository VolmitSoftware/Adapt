package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** Vanilla structure type keys the discovery adaptations search for. */
final class DiscoveryStructures {
  static final Id BURIED_TREASURE = Id.minecraft("buried_treasure");
  static final Id DESERT_PYRAMID = Id.minecraft("desert_pyramid");
  static final Id IGLOO = Id.minecraft("igloo");
  static final Id JIGSAW = Id.minecraft("jigsaw");
  static final Id JUNGLE_TEMPLE = Id.minecraft("jungle_temple");
  static final Id MINESHAFT = Id.minecraft("mineshaft");
  static final Id OCEAN_MONUMENT = Id.minecraft("ocean_monument");
  static final Id OCEAN_RUIN = Id.minecraft("ocean_ruin");
  static final Id RUINED_PORTAL = Id.minecraft("ruined_portal");
  static final Id SHIPWRECK = Id.minecraft("shipwreck");
  static final Id STRONGHOLD = Id.minecraft("stronghold");
  static final Id SWAMP_HUT = Id.minecraft("swamp_hut");
  static final Id WOODLAND_MANSION = Id.minecraft("woodland_mansion");
  static final Id FORTRESS = Id.minecraft("fortress");
  static final Id END_CITY = Id.minecraft("end_city");
  static final Id NETHER_FOSSIL = Id.minecraft("nether_fossil");

  static final List<Id> SENSED = List.of(
      BURIED_TREASURE,
      DESERT_PYRAMID,
      IGLOO,
      JIGSAW,
      JUNGLE_TEMPLE,
      MINESHAFT,
      OCEAN_MONUMENT,
      OCEAN_RUIN,
      RUINED_PORTAL,
      SHIPWRECK,
      STRONGHOLD,
      SWAMP_HUT,
      WOODLAND_MANSION,
      FORTRESS,
      END_CITY,
      NETHER_FOSSIL);

  private DiscoveryStructures() {
  }

  /** Bukkit {@code BoundingBox#contains(x, y, z)}: minimum inclusive, maximum exclusive. */
  static boolean contains(Box box, double x, double y, double z) {
    return x >= box.minX() && x < box.maxX()
        && y >= box.minY() && y < box.maxY()
        && z >= box.minZ() && z < box.maxZ();
  }
}
