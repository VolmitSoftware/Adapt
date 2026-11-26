package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Primes the player: the next natural block they break with a tool drops what that tool would drop with Fortune one
 * level higher. Player-placed blocks and the extra blocks another strike breaks keep their usual drops and leave the
 * prime in place.
 */
public class EnchantingWorkStrike extends StrikeBlock<EnchantingWorkStrike.Config> {
  private static final Id FORTUNE = Id.minecraft("fortune");
  private final Map<UUID, Long> primed = playerState();
  private final Map<UUID, Roll> rolls = playerState();

  public EnchantingWorkStrike() {
    super("block-enchanting-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lapis_lazuli"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    primed.put(player.uuid(), System.currentTimeMillis() + getConfig().primeMillis);
    fx(player.location().add(0D, 1D, 0D), FxPriority.TRANSITION)
        .particle(Particles.ENCHANTMENT_TABLE, 10, 0D, 0.3D, 0D, 0.4D, 0.4D);
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    Long until = primed.get(player.uuid());
    if (until == null || e.protectionProbe() || !e.dropItems() || BonusBlockBreaks.isActive(player)) {
      return;
    }
    if (System.currentTimeMillis() > until) {
      primed.remove(player.uuid());
      return;
    }
    BlockRef block = e.block();
    ItemRef held = player.inventory().mainHand();
    if (held == null || held.isEmpty() || MutationPayouts.farmed(block)) {
      return;
    }
    ItemRef luckier = held.copy();
    luckier.setEnchantment(FORTUNE, held.enchantmentLevel(FORTUNE) + 1);
    primed.remove(player.uuid());
    rolls.put(player.uuid(), new Roll(block.pos(), block.drops(luckier, player)));
  }

  @Handle(priority = EventPriority.LOW)
  public void on(BlockDropItemEvent e) {
    Roll roll = rolls.remove(e.player().uuid());
    if (roll == null || !roll.at().equals(e.block().pos()) || e.items().isEmpty()) {
      return;
    }
    WorldRef world = e.block().world();
    Vec3 position = e.block().center().position();
    for (ItemEntityRef vanilla : e.items()) {
      vanilla.remove();
    }
    e.items().clear();
    for (ItemRef drop : roll.drops()) {
      if (isItem(drop) && drop.amount() > 0) {
        e.items().add(world.createItemEntity(position, drop));
      }
    }
    addStat(e.player(), "enchanting.mutated.fortune-rolls", 1);
    fx(e.block().center(), FxPriority.TRANSITION).particle(Particles.WAX_ON, 6, 0D, 0D, 0D, 0.3D, 0.02D);
  }

  private record Roll(BlockPos at, List<ItemRef> drops) {
  }

  @ConfigDescription("Your next block break rolls Fortune one level higher.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the prime waits for a block break, in milliseconds.", impact = "Higher values leave more time to pick a block.")
    long primeMillis = 30_000L;
  }
}
