package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.world.BlockOwners;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * Brewing stands an adaptation keeps hot: it watches stands that were filled or clicked, and on every tick removes
 * brew time from each owned stand while the owner holds the adaptation.
 */
public final class BrewingHeat {
  private static final int MAX_CHECKS_BEFORE_REMOVE = 20;
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id FIRE = Id.minecraft("fire");
  private static final BlockFace[] HEAT_FACES = {BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
  private final SimpleAdaptation<?> owner;
  private final Ambience ambience;
  private final Map<BlockRef, Integer> activeStands = new ConcurrentHashMap<>();

  public BrewingHeat(SimpleAdaptation<?> owner, Ambience ambience) {
    this.owner = owner;
    this.ambience = ambience;
  }

  /** Brew ticks to remove from a stand this tick, given the owner's level and the fire and lava touching it. */
  @FunctionalInterface
  public interface Ambience {
    void play(SimpleAdaptation<?> owner, Loc location, double fireSources, double lavaSources);
  }

  @FunctionalInterface
  public interface Warp {
    int ticks(int level, double fireSources, double lavaSources);
  }

  public static int computeWarpTicks(long intervalMillis, double fireBoost, double lavaBoost, double fireSources, double lavaSources) {
    double pct = (fireBoost * fireSources) + (lavaBoost * lavaSources);
    if (pct <= 0D) {
      return 0;
    }

    return (int) Math.ceil((intervalMillis / 50D) * pct);
  }

  public void watch(InventoryMoveItemEvent e) {
    if (e.destinationKind() != InventoryKind.BREWING || e.destinationBlock() == null) {
      return;
    }

    activeStands.put(e.destinationBlock(), MAX_CHECKS_BEFORE_REMOVE);
  }

  public void watch(InventoryClickEvent e) {
    if (e.clickedKind() == null) {
      return;
    }
    if (e.viewKind() == InventoryKind.BREWING) {
      Optional<BlockRef> standBlock = e.player().openContainerBlock();
      if (standBlock.isEmpty()) {
        return;
      }
      activeStands.put(standBlock.get(), MAX_CHECKS_BEFORE_REMOVE);
    }
  }

  public boolean watches(BlockRef block) {
    return activeStands.containsKey(block);
  }

  /** Keeps watching a stand that still has brew time left after a brew. */
  public void keepIfBrewing(BlockRef block) {
    Optional<BrewingStandRef> stand = block.brewingStand();
    if (stand.isPresent() && stand.get().brewingTime() > 0) {
      activeStands.put(block, MAX_CHECKS_BEFORE_REMOVE);
    }
  }

  public boolean isIdle() {
    return activeStands.isEmpty();
  }

  public void tick(ToIntFunction<UUID> ownerLevel, Warp warp) {
    if (activeStands.isEmpty()) {
      return;
    }

    for (BlockRef block : activeStands.keySet()) {
      if (block == null) {
        continue;
      }

      AdaptApi.scheduler().runAt(block.location(), () -> tickStand(block, ownerLevel, warp));
    }
  }

  private void tickStand(BlockRef block, ToIntFunction<UUID> ownerLevel, Warp warp) {
    Optional<BrewingStandRef> state = block.brewingStand();
    if (state.isEmpty()) {
      activeStands.remove(block);
      return;
    }
    BrewingStandRef brewingStand = state.get();

    if (brewingStand.brewingTime() <= 0) {
      Integer remainingChecks = activeStands.get(block);
      if (remainingChecks == null) {
        return;
      }

      if (remainingChecks <= 0) {
        activeStands.remove(block);
      } else {
        activeStands.put(block, remainingChecks - 1);
      }
      return;
    }

    UUID standOwner = BlockOwners.owner(block).orElse(null);
    if (standOwner == null) {
      activeStands.remove(block);
      return;
    }

    int level = ownerLevel.applyAsInt(standOwner);
    if (level <= 0) {
      activeStands.remove(block);
      return;
    }

    updateHeat(brewingStand, level, warp);
  }

  private void updateHeat(BrewingStandRef b, int level, Warp warp) {
    double l = 0;
    double f = 0;

    for (BlockFace face : HEAT_FACES) {
      Id type = b.block().relative(face).type();
      if (type.equals(LAVA)) {
        l = l + 1;
      } else if (type.equals(FIRE)) {
        f = f + 1;
      }
    }

    int ticks = warp.ticks(level, f, l);
    if (ticks <= 0) {
      return;
    }

    b.setBrewingTime(Math.max(1, b.brewingTime() - ticks));

    if (ThreadLocalRandom.current().nextDouble() < 1D / (333D / owner.getInterval())) {
      ambience.play(owner, b.block().location().add(0.5D, 0.9D, 0.5D), f, l);
    }
  }
}
