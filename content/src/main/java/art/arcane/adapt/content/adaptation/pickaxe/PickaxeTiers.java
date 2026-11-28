package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.item.ToolMatch;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Set;

/** Pickaxe tiers for adaptations that need a strong pickaxe: vanilla by name, modded by what the tool can mine. */
final class PickaxeTiers {
  private PickaxeTiers() {
  }

  /**
   * True for the listed vanilla pickaxes, a tool on the Pickaxes extra-tools list, and a modded pickaxe that is the
   * correct tool for {@code benchmark}, the block whose tier the vanilla list starts at.
   */
  static boolean reaches(ItemRef item, Set<String> vanilla, Id benchmark) {
    if (item == null || item.isEmpty()) {
      return false;
    }
    Id type = item.type();
    if (type.isMinecraft()) {
      return vanilla.contains(type.path());
    }
    ToolMatch match = Tools.match(item, ToolKind.PICKAXE);
    if (match == ToolMatch.OPERATOR) {
      return true;
    }
    if (!match.matches() || !AdaptApi.isRunning()) {
      return false;
    }
    BlockStateRef state = AdaptApi.platform().host().items().blockState(benchmark);
    return state != null && state.isCorrectTool(item);
  }
}
