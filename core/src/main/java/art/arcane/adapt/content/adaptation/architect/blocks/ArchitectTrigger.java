package art.arcane.adapt.content.adaptation.architect.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

/**
 * Fires when the player places enough blocks inside the window. The run counts as farmed when any of its placements
 * refilled a spot broken in the replace-deny window, the rule Architect's own XP uses.
 */
public class ArchitectTrigger extends TriggerBlock<ArchitectTrigger.Config> {
  private final Map<UUID, Deque<Placement>> placements = playerState();

  public ArchitectTrigger() {
    super("block-architect-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bricks"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef player = e.player();
    if (e.protectionProbe() || !e.canBuild() || !hasActiveAdaptation(player)) {
      return;
    }
    long now = System.currentTimeMillis();
    Deque<Placement> recent = placements.computeIfAbsent(player.uuid(), (UUID ignored) -> new ArrayDeque<>());
    recent.addLast(new Placement(now, XpProvenance.placeXpMultiplier(e.block()) <= 0D));
    while (!recent.isEmpty() && now - recent.peekFirst().at() > getConfig().windowMillis) {
      recent.removeFirst();
    }
    if (recent.size() < getConfig().blocks) {
      return;
    }
    boolean farmed = false;
    for (Placement placement : recent) {
      farmed |= placement.farmed();
    }
    recent.clear();
    fire(player, null, e.block().center(), farmed);
  }

  private record Placement(long at, boolean farmed) {
  }

  @ConfigDescription("Place 8 blocks within 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Blocks to place inside the window.", impact = "Higher values fire less often.")
    int blocks = 8;
    @ConfigDoc(value = "How long the placements may take, in milliseconds.", impact = "Higher values allow slower building.")
    long windowMillis = 5_000L;
  }
}
