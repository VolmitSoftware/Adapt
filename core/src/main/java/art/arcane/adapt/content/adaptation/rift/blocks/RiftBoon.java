package art.arcane.adapt.content.adaptation.rift.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.rift.RiftResist;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Resistance for a few seconds, stacked the way Rift Resist does. */
public class RiftBoon extends BoonBlock<RiftBoon.Config> {
  public RiftBoon() {
    super("block-rift-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("obsidian"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (getConfig().durationMillis / 50L);
    if (ticks <= 0) {
      return Outcome.MISSED;
    }
    int amplifier = (int) Math.round(use.scaled(getConfig().amplifierAtFirstLevel, getConfig().amplifierAtTopLevel));
    RiftResist.riftResistStackAdd(this, player, ticks, Math.max(0, amplifier));
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Resistance for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the resistance lasts, in milliseconds.", impact = "Higher values keep the resistance longer.")
    long durationMillis = 3_000L;
    @ConfigDoc(value = "Resistance amplifier at level 1; 0 is Resistance I.", impact = "Higher values take less damage.")
    double amplifierAtFirstLevel = 0D;
    @ConfigDoc(value = "Resistance amplifier at the top level.", impact = "Higher values take less damage.")
    double amplifierAtTopLevel = 1D;
  }
}
