package art.arcane.adapt.content.adaptation.architect.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.architect.ArchitectBlockMarks;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.UUID;

/**
 * A stock of reinforced placements: each of the player's next placements is blast and fire proof for a while, then an
 * ordinary block again. Breaking one by hand drops it as usual. Grants top the stock up. Nothing is ever given back.
 */
public class ArchitectBoon extends BoonBlock<ArchitectBoon.Config> {
  private final Map<UUID, Integer> stock = playerState();
  private final ArchitectBlockMarks reinforced = new ArchitectBlockMarks();

  public ArchitectBoon() {
    super("block-architect-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("obsidian"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    int count = (int) Math.round(use.scaled(getConfig().placementsAtFirstLevel, getConfig().placementsAtTopLevel));
    Integer held = stock.get(use.player().uuid());
    if (held != null && held >= count) {
      return Outcome.MISSED;
    }
    stock.put(use.player().uuid(), count);
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef player = e.player();
    Integer left = stock.get(player.uuid());
    if (left == null || e.protectionProbe() || !e.canBuild() || !hasActiveAdaptation(player)) {
      return;
    }
    reinforced.add(e.block(), System.currentTimeMillis() + getConfig().reinforcedMillis);
    if (left <= 1) {
      stock.remove(player.uuid());
    } else {
      stock.put(player.uuid(), left - 1);
    }
    addStat(player, "architect.mutated.reinforced", 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    reinforced.spare(e.blocks());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    reinforced.spare(e.blocks());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBurnEvent e) {
    if (reinforced.contains(e.block())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (!e.protectionProbe()) {
      reinforced.remove(e.block());
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    reinforced.clear();
  }

  @ConfigDescription("Your next 8 placements are blast and fire proof for 60 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Placements reinforced at level 1.", impact = "Higher values reinforce more blocks.")
    double placementsAtFirstLevel = 8D;
    @ConfigDoc(value = "Placements reinforced at the top level.", impact = "Higher values reinforce more blocks.")
    double placementsAtTopLevel = 12D;
    @ConfigDoc(value = "How long a placement stays blast and fire proof, in milliseconds.", impact = "Higher values keep builds safe longer.")
    long reinforcedMillis = 60_000L;
  }
}
