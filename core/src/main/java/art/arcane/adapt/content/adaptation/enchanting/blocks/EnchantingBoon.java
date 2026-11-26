package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** The vanilla Luck effect for a few seconds. */
public class EnchantingBoon extends BoonBlock<EnchantingBoon.Config> {
  private static final Id LUCK = Id.minecraft("luck");

  public EnchantingBoon() {
    super("block-enchanting-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rabbit_foot"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (getConfig().durationMillis / 50L);
    int amplifier = (int) Math.round(use.scaled(getConfig().amplifierAtFirstLevel, getConfig().amplifierAtTopLevel));
    if (!player.addEffect(EffectInstance.of(LUCK, ticks, Math.max(0, amplifier)))) {
      return Outcome.MISSED;
    }
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Luck for 8 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the luck lasts, in milliseconds.", impact = "Higher values keep the luck longer.")
    long durationMillis = 8_000L;
    @ConfigDoc(value = "Luck amplifier at level 1; 0 is Luck I.", impact = "Higher values raise loot quality more.")
    double amplifierAtFirstLevel = 0D;
    @ConfigDoc(value = "Luck amplifier at the top level; 0 is Luck I.", impact = "Higher values raise loot quality more.")
    double amplifierAtTopLevel = 1D;
  }
}
