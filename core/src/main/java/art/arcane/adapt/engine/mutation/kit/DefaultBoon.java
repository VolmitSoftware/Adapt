package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The generated Boon: a little Regeneration. */
public final class DefaultBoon extends BoonBlock<DefaultBoon.Config> {
  private static final Id REGENERATION = Id.minecraft("regeneration");

  DefaultBoon(Id skill) {
    super(skill);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_apple"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) Math.round(use.scaled(getConfig().ticksAtFirstLevel, getConfig().ticksAtTopLevel));
    player.addEffect(EffectInstance.of(REGENERATION, ticks, 0));
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Regeneration for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long Regeneration lasts at level 1, in ticks.",
        impact = "Higher values heal more.")
    double ticksAtFirstLevel = 40D;
    @ConfigDoc(value = "How long Regeneration lasts at the top level, in ticks.",
        impact = "Higher values heal more.")
    double ticksAtTopLevel = 80D;
  }
}
