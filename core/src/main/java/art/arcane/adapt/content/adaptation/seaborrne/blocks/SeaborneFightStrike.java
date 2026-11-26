package art.arcane.adapt.content.adaptation.seaborrne.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneSurge;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** A burst of water throws the target away from the player, shaped by Tidecaller's surge velocity, and douses it. */
public class SeaborneFightStrike extends StrikeBlock<SeaborneFightStrike.Config> {
  public SeaborneFightStrike() {
    super("block-seaborne-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("heart_of_the_sea"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    Vec3 away = target.location().position().subtract(player.location().position()).withY(0D);
    Vec3 direction = away.lengthSquared() < 1.0E-6D ? player.location().direction().withY(0D) : away;
    if (direction.lengthSquared() < 1.0E-6D) {
      direction = Vec3.ZERO.withZ(1D);
    }
    double strength = use.scaled(getConfig().pushAtFirstLevel, getConfig().pushAtTopLevel);
    Vec3 push = direction.normalize();
    boolean scheduled = Tasks.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      target.setVelocity(SeaborneSurge.velocity(target.velocity(), push, strength, getConfig().lift, false,
          getConfig().maxVelocity));
      target.setFireTicks(0);
      fx(target.location().add(0D, 0.6D, 0D), FxPriority.COMBAT)
          .particle(Particles.SPLASH, 24, 0D, 0D, 0D, 0.45D, 0.1D)
          .particle(Particles.BUBBLE_POP, 10, 0D, 0D, 0D, 0.35D, 0.05D);
    });
    return scheduled ? Outcome.INSTANT : Outcome.MISSED;
  }

  @ConfigDescription("A water burst pushes the target back and puts out fire.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Horizontal push at level 1.", impact = "Higher values throw the target farther.")
    double pushAtFirstLevel = 1.1D;
    @ConfigDoc(value = "Horizontal push at the top level.", impact = "Higher values throw the target farther.")
    double pushAtTopLevel = 1.8D;
    @ConfigDoc(value = "Upward lift of the push.", impact = "Higher values lift the target off the ground.")
    double lift = 0.35D;
    @ConfigDoc(value = "Fastest the target can be thrown.", impact = "Lower values cap the push.")
    double maxVelocity = 2.2D;
  }
}
