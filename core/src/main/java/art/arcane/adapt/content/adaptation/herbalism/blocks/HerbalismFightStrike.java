package art.arcane.adapt.content.adaptation.herbalism.blocks;

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
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Vines hold the target in place: its movement speed drops to nothing for a moment. */
public class HerbalismFightStrike extends StrikeBlock<HerbalismFightStrike.Config> {
  private static final String SLOT = "root";

  public HerbalismFightStrike() {
    super("block-herbalism-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("vine"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    int ticks = (int) (use.scaled(getConfig().rootMillisAtFirstLevel, getConfig().rootMillisAtTopLevel) / 50D);
    boolean scheduled = Tasks.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      AdaptAttributeService.get().applyTimed(target, getName(), SLOT, Attributes.MOVEMENT_SPEED, -1D,
          AttributeOperation.ADD_MULTIPLIED_TOTAL, ticks);
      target.setVelocity(Vec3.ZERO);
      fx(target.location().add(0D, 0.4D, 0D), FxPriority.COMBAT)
          .particle(Particles.COMPOSTER, 12, 0D, 0D, 0D, 0.4D, 0.02D)
          .particle(Particles.HAPPY_VILLAGER, 4, 0D, 0D, 0D, 0.3D, 0.02D);
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target is rooted by vines for 2 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target is rooted at level 1, in milliseconds.", impact = "Higher values hold the target longer.")
    double rootMillisAtFirstLevel = 2_000D;
    @ConfigDoc(value = "How long the target is rooted at the top level, in milliseconds.", impact = "Higher values hold the target longer.")
    double rootMillisAtTopLevel = 3_000D;
  }
}
