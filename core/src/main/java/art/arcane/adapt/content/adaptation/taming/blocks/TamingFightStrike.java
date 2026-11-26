package art.arcane.adapt.content.adaptation.taming.blocks;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.taming.TamePackCommand;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.adaptation.taming.TamingGuardianInstinct;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * The owner's nearest combat pet stands up, leaps at the target with Guardian Instinct's leap and takes it as its
 * target.
 */
public class TamingFightStrike extends StrikeBlock<TamingFightStrike.Config> {
  public TamingFightStrike() {
    super("block-taming-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lead"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target) || TamePets.isOwnedPet(target, player.uuid())
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    LivingRef pet = TamePets.nearest(player, getConfig().petRadius, TamePackCommand::isCombatCapableTameable);
    if (pet == null) {
      return Outcome.MISSED;
    }
    double strength = use.scaled(getConfig().leapStrengthAtFirstLevel, getConfig().leapStrengthAtTopLevel);
    Loc chest = target.location().add(0D, target.height() * 0.5D, 0D);
    boolean scheduled = Tasks.runEntity(pet, () -> {
      if (!pet.isValid() || pet.isDead() || !target.isValid() || target.isDead()) {
        return;
      }
      AnimalOps animals = AdaptCore.platform().host().animals();
      if (animals.isSitting(pet)) {
        animals.setSitting(pet, false);
      }
      TamePets.leap(pet, chest, strength);
      if (pet instanceof MobRef mob) {
        mob.setTarget(target);
      }
      TamingGuardianInstinct.leapFx(this, pet, chest);
    });
    return scheduled ? Outcome.INSTANT : Outcome.MISSED;
  }

  @ConfigDescription("Your nearest pet leaps at the target.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How far from you a pet can be to answer, in blocks.", impact = "Higher values call pets from farther away.")
    double petRadius = 16D;
    @ConfigDoc(value = "Leap speed at level 1.", impact = "Higher values close the distance faster.")
    double leapStrengthAtFirstLevel = 0.9D;
    @ConfigDoc(value = "Leap speed at the top level.", impact = "Higher values close the distance faster.")
    double leapStrengthAtTopLevel = 1.3D;
  }
}
