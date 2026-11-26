package art.arcane.adapt.content.adaptation.seaborrne.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneSwim;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Water Breathing, and Dolphin's Grace through Dolphin's Grace's own grant, which never shortens a longer grace. */
public class SeaborneBoon extends BoonBlock<SeaborneBoon.Config> {
  private static final Id WATER_BREATHING = Id.minecraft("water_breathing");

  public SeaborneBoon() {
    super("block-seaborne-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("turtle_helmet"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (use.scaled(getConfig().durationMillisAtFirstLevel, getConfig().durationMillisAtTopLevel) / 50D);
    EffectInstance breathing = player.effect(WATER_BREATHING);
    if (breathing == null || (!breathing.isInfinite() && breathing.durationTicks() < ticks)) {
      player.addEffect(new EffectInstance(WATER_BREATHING, ticks, 0, true, false, true));
    }
    SeaborneSwim.grantGrace(player, ticks);
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Water breathing and fast swimming for 6 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long both effects last at level 1, in milliseconds.", impact = "Higher values keep the effects longer.")
    double durationMillisAtFirstLevel = 6_000D;
    @ConfigDoc(value = "How long both effects last at the top level, in milliseconds.", impact = "Higher values keep the effects longer.")
    double durationMillisAtTopLevel = 10_000D;
  }
}
