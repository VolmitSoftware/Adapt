package art.arcane.adapt.content.adaptation.crafting.blocks;

import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.crafting.CraftingMending;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Set;

/**
 * The item in the main hand repairs. When a Barter's Fuel just took durability from the held tool, every worn armor
 * piece repairs instead; with an empty main hand, the most damaged armor piece.
 */
public class CraftingBoon extends BoonBlock<CraftingBoon.Config> {
  private static final Set<String> HELD_DURABILITY_FUELS = Set.of("swords", "axes", "pickaxe");

  public CraftingBoon() {
    super("block-crafting-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_ingot"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int amount = (int) Math.round(use.scaled(getConfig().durabilityAtFirstLevel, getConfig().durabilityAtTopLevel));
    boolean heldPaid = use.recipe() == Recipe.BARTER && HELD_DURABILITY_FUELS.contains(use.initiator());
    int restored = heldPaid
        ? CraftingMending.repairAllArmor(player, amount)
        : CraftingMending.repairHeldOrArmor(player, amount);
    if (restored <= 0) {
      return Outcome.MISSED;
    }
    addStat(player, "crafting.mutated.durability-repaired", restored);
    return Outcome.INSTANT;
  }

  @ConfigDescription("6 durability on the item in your hand.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Durability repaired at level 1.", impact = "Higher values repair more.")
    double durabilityAtFirstLevel = 6D;
    @ConfigDoc(value = "Durability repaired at the top level.", impact = "Higher values repair more.")
    double durabilityAtTopLevel = 12D;
  }
}
