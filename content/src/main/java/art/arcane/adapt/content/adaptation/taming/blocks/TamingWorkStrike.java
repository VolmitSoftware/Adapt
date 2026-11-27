package art.arcane.adapt.content.adaptation.taming.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.taming.TamePackBuff;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.adaptation.taming.TameRegeneration;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** Pets around the player heal through Health Regeneration's heal and gain Pack Leader Aura's timed speed. */
public class TamingWorkStrike extends StrikeBlock<TamingWorkStrike.Config> {
  public TamingWorkStrike() {
    super(SkillTaming.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cooked_beef"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    List<LivingRef> pets = TamePets.near(player, getConfig().radius);
    if (pets.isEmpty()) {
      return Outcome.MISSED;
    }
    double healing = use.scaled(getConfig().healingAtFirstLevel, getConfig().healingAtTopLevel);
    int ticks = (int) (getConfig().speedMillis / 50L);
    for (LivingRef pet : pets) {
      AdaptApi.scheduler().runEntity(pet, () -> {
        if (!pet.isValid() || pet.isDead()) {
          return;
        }
        TameRegeneration.heal(this, pet, healing);
        TamePackBuff.apply(this, pet, 0, ticks, true, false);
      });
    }
    addStat(player, "taming.mutated.pets-tended", pets.size());
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Pets within 8 blocks heal 1 heart and keep pace with you for 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Radius around you in which pets are tended, in blocks.", impact = "Higher values reach pets farther away.")
    double radius = 8D;
    @ConfigDoc(value = "Health each pet regains at level 1.", impact = "Higher values heal pets more.")
    double healingAtFirstLevel = 2D;
    @ConfigDoc(value = "Health each pet regains at the top level.", impact = "Higher values heal pets more.")
    double healingAtTopLevel = 4D;
    @ConfigDoc(value = "How long pets keep the speed bonus, in milliseconds.", impact = "Higher values keep pets fast for longer.")
    long speedMillis = 5_000L;
  }
}
