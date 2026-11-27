package art.arcane.adapt.content.adaptation.agility.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Speed for a few seconds. */
public class AgilityWorkStrike extends StrikeBlock<AgilityWorkStrike.Config> {
  private static final Id SPEED = Id.minecraft("speed");

  public AgilityWorkStrike() {
    super(SkillAgility.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sugar"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (use.scaled(getConfig().durationMillisAtFirstLevel, getConfig().durationMillisAtTopLevel) / 50D);
    if (ticks <= 0 || !player.addEffect(EffectInstance.of(SPEED, ticks, Math.max(0, getConfig().amplifier)))) {
      return Outcome.MISSED;
    }
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("You gain speed II for 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Speed amplifier; 1 is Speed II.", impact = "Higher values run faster.")
    int amplifier = 1;
    @ConfigDoc(value = "How long the speed lasts at level 1, in milliseconds.", impact = "Higher values keep the speed longer.")
    double durationMillisAtFirstLevel = 4_000D;
    @ConfigDoc(value = "How long the speed lasts at the top level, in milliseconds.", impact = "Higher values keep the speed longer.")
    double durationMillisAtTopLevel = 6_000D;
  }
}
