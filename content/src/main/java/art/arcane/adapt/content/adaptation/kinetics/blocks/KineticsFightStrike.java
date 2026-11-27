package art.arcane.adapt.content.adaptation.kinetics.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.kinetics.KineticsWindburst;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

/** The target is thrown straight up, keeping a little of its sideways motion, with Windburst's impact puff. */
public class KineticsFightStrike extends StrikeBlock<KineticsFightStrike.Config> {
  private static final double KEPT_SIDEWAYS = 0.2D;

  public KineticsFightStrike() {
    super(SkillKinetics.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("piston"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    Vec3 velocity = target.velocity();
    target.setVelocity(new Vec3(velocity.x() * KEPT_SIDEWAYS,
        use.scaled(getConfig().launchAtFirstLevel, getConfig().launchAtTopLevel), velocity.z() * KEPT_SIDEWAYS));
    KineticsWindburst.impactFx(this, target.location());
    return Outcome.INSTANT;
  }

  @ConfigDescription("The target is launched upward.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Upward velocity given to the target at level 1, in blocks per tick.", impact = "Higher values launch the target higher.")
    double launchAtFirstLevel = 0.9D;
    @ConfigDoc(value = "Upward velocity given to the target at the top level, in blocks per tick.", impact = "Higher values launch the target higher.")
    double launchAtTopLevel = 1.3D;
  }
}
