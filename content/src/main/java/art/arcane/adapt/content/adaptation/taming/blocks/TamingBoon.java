package art.arcane.adapt.content.adaptation.taming.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Heals the player for every pet around them, up to a pet cap; misses with no pet near or at full health. */
public class TamingBoon extends BoonBlock<TamingBoon.Config> {
  public TamingBoon() {
    super(SkillTaming.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glistering_melon_slice"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int pets = Math.min(getConfig().maxPets, TamePets.near(player, getConfig().radius).size());
    double health = player.health();
    double maximum = player.maxHealth();
    if (pets <= 0 || health >= maximum) {
      return Outcome.MISSED;
    }
    double perPet = use.scaled(getConfig().healingPerPetAtFirstLevel, getConfig().healingPerPetAtTopLevel);
    double healing = Math.min(perPet * pets, use.scaled(getConfig().maxHealingAtFirstLevel, getConfig().maxHealingAtTopLevel));
    player.setHealth(Math.min(maximum, health + healing));
    return Outcome.INSTANT;
  }

  @ConfigDescription("2 hearts of healing for each pet nearby, at most 3 hearts.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Radius around you in which pets count, in blocks.", impact = "Higher values count pets farther away.")
    double radius = 8D;
    @ConfigDoc(value = "Most pets that count toward one heal.", impact = "Higher values let large packs heal more per boon.")
    int maxPets = 3;
    @ConfigDoc(value = "Health restored per pet at level 1.", impact = "Higher values heal more.")
    double healingPerPetAtFirstLevel = 4D;
    @ConfigDoc(value = "Health restored per pet at the top level.", impact = "Higher values heal more.")
    double healingPerPetAtTopLevel = 6D;
    @ConfigDoc(value = "Most health one grant restores at level 1, however many pets are near.", impact = "Higher values let a crowd of pets heal more.")
    double maxHealingAtFirstLevel = 6D;
    @ConfigDoc(value = "Most health one grant restores at the top level, however many pets are near.", impact = "Higher values let a crowd of pets heal more.")
    double maxHealingAtTopLevel = 8D;
  }
}
