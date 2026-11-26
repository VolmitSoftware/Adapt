package art.arcane.adapt.content.adaptation.ranged.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.ranged.HeartseekerGuidance;
import art.arcane.adapt.content.adaptation.ranged.ProjectileMemo;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * A spectral arrow leaves the player aimed at the target's chest, flown by Heartseeker's launch helper. It cannot be
 * picked up and never sets off the Ranged Trigger.
 */
public class RangedFightStrike extends StrikeBlock<RangedFightStrike.Config> {
  private static final ProjectileMemo<Boolean> FIRED = ProjectileMemo.create();

  public RangedFightStrike() {
    super("block-ranged-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spectral_arrow"));
  }

  /** Whether this arrow was fired by the Strike. */
  public static boolean fired(EntityRef projectile) {
    return FIRED.has(projectile);
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    Vec3 aim = target.location().add(0D, target.height() * 0.5D, 0D).position()
        .subtract(player.eyeLocation().position());
    double damage = use.scaled(getConfig().damageAtFirstLevel, getConfig().damageAtTopLevel);
    ProjectileRef arrow = player.launchProjectile(RangedProjectiles.SPECTRAL_ARROW, (ProjectileRef launched) -> {
      FIRED.set(launched, true);
      launched.setPickupRule(ArrowPickup.DISALLOWED);
      launched.setDamage(damage);
      HeartseekerGuidance.fly(launched, aim, getConfig().speed);
    });
    return arrow == null ? Outcome.MISSED : Outcome.INSTANT;
  }

  @ConfigDescription("A spectral arrow fires at the target.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Speed of the arrow in blocks per tick.", impact = "Higher values reach the target sooner and hit harder.")
    double speed = 3D;
    @ConfigDoc(value = "Base damage of the arrow at level 1.", impact = "Higher values make the arrow deadlier.")
    double damageAtFirstLevel = 2D;
    @ConfigDoc(value = "Base damage of the arrow at the top level.", impact = "Higher values make the arrow deadlier.")
    double damageAtTopLevel = 3D;
  }
}
