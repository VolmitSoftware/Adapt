package art.arcane.adapt.content.adaptation.excavation.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

/**
 * Fires when the player digs enough soft blocks with a shovel inside the window. The run counts as farmed when any of
 * its blocks was placed by a player or replaced in the deny window.
 */
public class ExcavationTrigger extends TriggerBlock<ExcavationTrigger.Config> {
  private final Map<UUID, Deque<Dig>> digs = playerState();

  public ExcavationTrigger() {
    super("block-excavation-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_shovel"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef block = e.block();
    if (e.protectionProbe() || !ExcavationBlocks.isShovelable(block.type()) || !isShovel(player.inventory().mainHand())
        || !hasActiveAdaptation(player)) {
      return;
    }
    long now = System.currentTimeMillis();
    Deque<Dig> recent = digs.computeIfAbsent(player.uuid(), (UUID ignored) -> new ArrayDeque<>());
    recent.addLast(new Dig(now, MutationPayouts.farmed(block)));
    while (!recent.isEmpty() && now - recent.peekFirst().at() > getConfig().windowMillis) {
      recent.removeFirst();
    }
    if (recent.size() < getConfig().blocks) {
      return;
    }
    boolean farmed = false;
    for (Dig dig : recent) {
      farmed |= dig.farmed();
    }
    recent.clear();
    fire(player, null, block.center(), farmed);
  }

  private record Dig(long at, boolean farmed) {
  }

  @ConfigDescription("Dig 8 soft blocks within 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Soft blocks to dig inside the window.", impact = "Higher values fire less often.")
    int blocks = 8;
    @ConfigDoc(value = "How long the digs may take, in milliseconds.", impact = "Higher values allow slower digging.")
    long windowMillis = 5_000L;
  }
}
