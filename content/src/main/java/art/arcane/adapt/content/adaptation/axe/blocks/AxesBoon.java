package art.arcane.adapt.content.adaptation.axe.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Absorption hearts from the vanilla effect, two hearts per amplifier step. */
public class AxesBoon extends BoonBlock<AxesBoon.Config> {
  private static final Id ABSORPTION = Id.minecraft("absorption");
  private static final double HEARTS_PER_STEP = 2D;

  public AxesBoon() {
    super(SkillAxes.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_apple"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (getConfig().durationMillis / 50L);
    double hearts = use.scaled(getConfig().heartsAtFirstLevel, getConfig().heartsAtTopLevel);
    int amplifier = Math.max(0, (int) Math.round(hearts / HEARTS_PER_STEP) - 1);
    player.addEffect(EffectInstance.of(ABSORPTION, ticks, amplifier));
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("2 absorption hearts for 6 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the absorption lasts, in milliseconds.", impact = "Higher values keep the hearts longer.")
    long durationMillis = 6_000L;
    @ConfigDoc(value = "Absorption hearts at level 1, in steps of 2.", impact = "Higher values soak more damage.")
    double heartsAtFirstLevel = 2D;
    @ConfigDoc(value = "Absorption hearts at the top level, in steps of 2.", impact = "Higher values soak more damage.")
    double heartsAtTopLevel = 4D;
  }
}
