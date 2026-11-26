package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;

/** Trailblazer's record of the biomes and structure types a player has been to, and the structures around a spot. */
public final class FirstVisits {
  private FirstVisits() {
  }

  public enum Visit {
    NONE,
    BIOME,
    STRUCTURE
  }

  /** Records where the player stands and says whether it is a biome or structure type they had never visited. */
  public static Visit record(PlayerData data, PlayerRef player) {
    Loc loc = player.location();
    WorldRef world = loc.world();
    if (world == null) {
      return Visit.NONE;
    }
    Id biome = world.blockAt(loc).biome();
    String biomeKey = "discovery.trailblazer.biome." + biome;
    if (data.getStat(biomeKey) <= 0) {
      data.addStat(biomeKey, 1);
      return Visit.BIOME;
    }

    for (GeneratedStructure structure : structuresAround(loc)) {
      String structureKey = "discovery.trailblazer.structure." + structure.type();
      if (data.getStat(structureKey) > 0) {
        continue;
      }
      data.addStat(structureKey, 1);
      return Visit.STRUCTURE;
    }
    return Visit.NONE;
  }

  /** The structures whose bounds hold {@code loc}, from the pieces reaching into its chunk. Owning thread of the chunk. */
  public static List<GeneratedStructure> structuresAround(Loc loc) {
    WorldRef world = loc.world();
    if (world == null) {
      return List.of();
    }
    List<GeneratedStructure> around = new ArrayList<>(1);
    for (GeneratedStructure structure : world.structuresAt(loc.chunkX(), loc.chunkZ())) {
      if (DiscoveryStructures.contains(structure.bounds(), loc.x(), loc.y(), loc.z())) {
        around.add(structure);
      }
    }
    return around;
  }
}
