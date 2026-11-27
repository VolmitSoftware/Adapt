package art.arcane.adapt.content.adaptation.stealth.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/**
 * Spends the player's cover: payable while sneaking unhunted or invisible, it strips invisibility and outlines the
 * player for a few seconds. While a shadow decoy is up the gesture belongs to the decoy swap, so nothing is paid.
 */
public class StealthFuel extends FuelBlock<StealthFuel.Config> {
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final Id GLOWING = Id.minecraft("glowing");

  public StealthFuel() {
    super(SkillStealth.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_eye"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    PlayerRef player = use.player();
    StealthShadowDecoy decoy = StealthShadowDecoy.of(getSkill());
    if (decoy != null && decoy.hasActiveDecoy(player.uuid())) {
      return false;
    }
    return player.foodLevel() >= getConfig().hunger && (player.hasEffect(INVISIBILITY)
        || (player.isSneaking() && !StealthState.hunted(player, getConfig().huntRadius)));
  }

  @Override
  public boolean pay(BlockUse use) {
    if (!payHunger(use, getConfig().hunger)) {
      return false;
    }
    PlayerRef player = use.player();
    player.removeEffect(INVISIBILITY);
    player.addEffect(EffectInstance.of(GLOWING, (int) (getConfig().revealMillis / 50L), 0).withParticles(false));
    return true;
  }

  @ConfigDescription("Your cover and 2 points of hunger (you are revealed).")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long you glow after spending your cover, in milliseconds.", impact = "Higher values leave you exposed longer.")
    long revealMillis = 5_000L;
    @ConfigDoc(value = "Reach in blocks within which a mob targeting you breaks your cover.", impact = "Higher values make cover harder to keep.")
    double huntRadius = 32D;
    @ConfigDoc(value = "Hunger points taken per use.", impact = "Higher values cost more food.")
    int hunger = 2;
  }
}
