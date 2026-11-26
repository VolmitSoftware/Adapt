package art.arcane.adapt.content.adaptation.excavation.blocks;

import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.content.adaptation.excavation.ExcavationPlane;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The soft blocks of the 3 by 3 around a dig break and drop, through Tunneler's plane: each is a normal break as the
 * player that charges the shovel. Set off by Excavation's own Trigger it digs around the last dig; set off by another
 * skill it primes the next soft block the player digs.
 */
public class ExcavationWorkStrike extends StrikeBlock<ExcavationWorkStrike.Config> {
  private static final int RING = 8;
  private final Map<UUID, Long> primedUntil = playerState();
  private final ExcavationPlane plane = new ExcavationPlane(this,
      (PlayerRef player, List<BlockRef> dug) -> addStat(player, "excavation.mutated.dug", dug.size()));

  public ExcavationWorkStrike() {
    super("block-excavation-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("gravel"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    if (!getSkillId().equals(use.initiator())) {
      primedUntil.put(player.uuid(), System.currentTimeMillis() + getConfig().primedMillis);
      return Outcome.INSTANT;
    }
    Loc center = use.center();
    return carve(player, center.world().blockAt(center)) ? Outcome.INSTANT : Outcome.MISSED;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    Long until = primedUntil.get(player.uuid());
    BlockRef dug = e.block();
    if (until == null || e.protectionProbe() || BonusBlockBreaks.isActive(player)
        || !ExcavationBlocks.isShovelable(dug.type()) || !isShovel(player.inventory().mainHand())) {
      return;
    }
    primedUntil.remove(player.uuid());
    if (System.currentTimeMillis() <= until && hasActiveAdaptation(player) && !MutationPayouts.farmed(dug)) {
      carve(player, dug);
    }
  }

  private boolean carve(PlayerRef player, BlockRef origin) {
    return isShovel(player.inventory().mainHand())
        && plane.carve(player, origin, RING, getConfig().durabilityPerBlock, (Id type) -> true);
  }

  @ConfigDescription("The 3 by 3 of soft blocks around your last dig breaks and drops.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long a dig stays primed when another skill sets this off, in milliseconds.", impact = "Higher values give more time to dig.")
    long primedMillis = 10_000L;
    @ConfigDoc(value = "Shovel durability each extra block costs.", impact = "Higher values wear the shovel faster.")
    int durabilityPerBlock = 1;
  }
}
