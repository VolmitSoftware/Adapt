package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.item.ToolRules;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

public final class ExcavationBlocks {
  private static final Set<String> SHOVELABLE = Set.of("clay", "dirt", "coarse_dirt", "rooted_dirt", "farmland",
      "grass_block", "dirt_path", "gravel", "mycelium", "podzol", "sand", "red_sand", "soul_sand", "soul_soil", "snow",
      "snow_block", "mud", "muddy_mangrove_roots");

  private ExcavationBlocks() {
  }

  /**
   * Soil the Excavation adaptations dig: the vanilla list, a block of another namespace in {@code mineable/shovel},
   * or a block on the Excavation extra-blocks list; never one on its excluded-blocks list.
   */
  public static boolean isShovelable(Id type) {
    ToolRules rules = Tools.rules(ToolKind.SHOVEL);
    if (rules.excludesBlock(type)) {
      return false;
    }
    if (type.isMinecraft()) {
      return SHOVELABLE.contains(type.path()) || rules.blockXp(type).isPresent();
    }
    return Tools.claims(ToolKind.SHOVEL, type);
  }
}
