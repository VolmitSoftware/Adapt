package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.value.Id;

/** Fire Resistance on the player. */
public class NetherBoon extends BoonBlock<NetherBoon.Config> {
  private static final Id FIRE_RESISTANCE = Id.minecraft("fire_resistance");

  public NetherBoon() {
    super(SkillNether.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("magma_cream"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    int ticks = (int) (use.scaled(getConfig().durationMillisAtFirstLevel, getConfig().durationMillisAtTopLevel) / 50D);
    use.player().addEffect(EffectInstance.of(FIRE_RESISTANCE, ticks, 0).withParticles(false));
    return Outcome.lasting(use.player(), ticks);
  }

  @ConfigDescription("Fire resistance for 6 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long Fire Resistance lasts at level 1, in milliseconds.", impact = "Higher values protect longer.")
    double durationMillisAtFirstLevel = 6_000D;
    @ConfigDoc(value = "How long Fire Resistance lasts at the top level, in milliseconds.", impact = "Higher values protect longer.")
    double durationMillisAtTopLevel = 10_000D;
  }
}
