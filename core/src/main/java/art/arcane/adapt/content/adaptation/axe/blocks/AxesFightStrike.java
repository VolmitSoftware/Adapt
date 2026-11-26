package art.arcane.adapt.content.adaptation.axe.blocks;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** The target's armor drops by a share of its total for a few seconds, through the timed attribute service. */
public class AxesFightStrike extends StrikeBlock<AxesFightStrike.Config> {
  private static final String SLOT = "armor-cut";

  public AxesFightStrike() {
    super("block-axes-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chainmail_chestplate"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    if (target == null || !target.hasAttribute(Attributes.ARMOR) || !canDamageTarget(use.player(), target)) {
      return Outcome.MISSED;
    }
    int ticks = (int) (getConfig().durationMillis / 50L);
    double cut = use.scaled(getConfig().armorCutAtFirstLevel, getConfig().armorCutAtTopLevel);
    boolean scheduled = Tasks.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      AdaptAttributeService.get().applyTimed(target, getName(), SLOT, Attributes.ARMOR, -cut,
          AttributeOperation.ADD_MULTIPLIED_TOTAL, ticks);
      fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
          .particle(Particles.CRIT_MAGIC, 8, 0D, 0D, 0D, 0.3D, 0.05D);
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target's armor is cut by 15% for 6 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the armor stays cut, in milliseconds.", impact = "Higher values keep the target exposed longer.")
    long durationMillis = 6_000L;
    @ConfigDoc(value = "Share of the target's armor removed at level 1.", impact = "Higher values strip more armor.")
    double armorCutAtFirstLevel = 0.15D;
    @ConfigDoc(value = "Share of the target's armor removed at the top level.", impact = "Higher values strip more armor.")
    double armorCutAtTopLevel = 0.25D;
  }
}
