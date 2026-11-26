package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * Puts the clocks on cooldown, shown as the vanilla sweep. A clock already cooling down, from this or from Instant
 * Recall, cannot pay.
 */
public class ChronosFuel extends FuelBlock<ChronosFuel.Config> {
  private final ItemCooldowns clock = ItemCooldowns.forMaterial(ChronosTrigger.CLOCK);

  public ChronosFuel() {
    super("block-chronos-fuel");
    registerConfiguration(Config.class);
    setIcon(ChronosTrigger.CLOCK);
  }

  @Override
  public boolean canPay(BlockUse use) {
    PlayerRef player = use.player();
    return player.inventory().contains(ChronosTrigger.CLOCK, 1) && !player.hasCooldown(ChronosTrigger.CLOCK)
        && clock.isReady(player, getConfig().cooldownMillis);
  }

  @Override
  public boolean pay(BlockUse use) {
    clock.mark(use.player(), getConfig().cooldownMillis);
    return true;
  }

  @ConfigDescription("A 20 s cooldown on your clock.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the clock cools down, in milliseconds.", impact = "Higher values make the clock unusable for longer.")
    long cooldownMillis = 20_000L;
  }
}
