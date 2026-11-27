package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The generated work Strike: the player digs and works faster for a moment. */
public final class DefaultWorkStrike extends StrikeBlock<DefaultWorkStrike.Config> {
  private static final Id HASTE = Id.minecraft("haste");

  DefaultWorkStrike(Id skill) {
    super(skill, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_pickaxe"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) Math.round(use.scaled(getConfig().ticksAtFirstLevel, getConfig().ticksAtTopLevel));
    player.addEffect(EffectInstance.of(HASTE, ticks, 0));
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Haste for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long Haste lasts at level 1, in ticks.", impact = "Higher values keep Haste on longer.")
    double ticksAtFirstLevel = 40D;
    @ConfigDoc(value = "How long Haste lasts at the top level, in ticks.", impact = "Higher values keep Haste on longer.")
    double ticksAtTopLevel = 80D;
  }
}
