package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.platform.value.Id;

import java.util.Set;

final class ExcavationBlocks {
  private static final Set<String> SHOVELABLE = Set.of("clay", "dirt", "coarse_dirt", "rooted_dirt", "farmland",
      "grass_block", "dirt_path", "gravel", "mycelium", "podzol", "sand", "red_sand", "soul_sand", "soul_soil", "snow",
      "snow_block", "mud", "muddy_mangrove_roots");

  private ExcavationBlocks() {
  }

  static boolean isShovelable(Id type) {
    return type.isMinecraft() && SHOVELABLE.contains(type.path());
  }
}
