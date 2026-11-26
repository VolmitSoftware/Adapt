package art.arcane.adapt.content.adaptation.crafting.blocks;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * The tool in the main hand works as if it had one more level of Efficiency for a while, as a timed modifier on the
 * player that never touches the item. Without the mining efficiency attribute (before 1.21) it is a break-speed boost.
 */
public class CraftingWorkStrike extends StrikeBlock<CraftingWorkStrike.Config> {
  private static final Id EFFICIENCY = Id.minecraft("efficiency");
  private static final String SLOT = "efficiency";
  private static final long MILLIS_PER_TICK = 50L;

  public CraftingWorkStrike() {
    super("block-crafting-strike-work");
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("smithing_table"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    ItemRef tool = player.inventory().mainHand();
    if (tool == null || tool.isEmpty() || !CraftingMasterwork.isTool(tool.type()) || tool.type().path().endsWith("_sword")) {
      return Outcome.MISSED;
    }
    long millis = Math.round(use.scaled(getConfig().durationMillisAtFirstLevel, getConfig().durationMillisAtTopLevel));
    long ticks = Math.max(1L, millis / MILLIS_PER_TICK);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (player.hasAttribute(Attributes.MINING_EFFICIENCY)) {
      attributes.applyTimed(player, getName(), SLOT, Attributes.MINING_EFFICIENCY,
          nextLevelSpeed(tool.enchantmentLevel(EFFICIENCY)), AttributeOperation.ADD_VALUE, ticks);
    } else {
      attributes.applyTimed(player, getName(), SLOT, Attributes.BLOCK_BREAK_SPEED,
          getConfig().breakSpeedFallback, AttributeOperation.ADD_MULTIPLIED_TOTAL, ticks);
    }
    fx(player.location().add(0D, 1D, 0D), FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, 6, 0D, 0D, 0D, 0.3D, 0.02D);
    return Outcome.lasting(player, (int) ticks);
  }

  /** Mining speed one more Efficiency level adds over {@code level}: vanilla adds level squared plus one. */
  static double nextLevelSpeed(int level) {
    int current = Math.max(0, level);
    double now = current == 0 ? 0D : (current * current) + 1D;
    return ((current + 1D) * (current + 1D)) + 1D - now;
  }

  @ConfigDescription("The tool in your hand gets Efficiency +1 for 20 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the extra Efficiency level lasts at level 1, in milliseconds.", impact = "Higher values keep the boost longer.")
    double durationMillisAtFirstLevel = 20_000D;
    @ConfigDoc(value = "How long the extra Efficiency level lasts at the top level, in milliseconds.", impact = "Higher values keep the boost longer.")
    double durationMillisAtTopLevel = 40_000D;
    @ConfigDoc(value = "Break speed added where the mining efficiency attribute does not exist, as a fraction.", impact = "Higher values dig faster on those versions.")
    double breakSpeedFallback = 0.25D;
  }
}
