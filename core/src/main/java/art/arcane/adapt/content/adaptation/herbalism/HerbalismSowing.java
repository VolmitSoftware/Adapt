package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed Sower's planting: fills empty farmland (soul sand for nether wart) in a square around an origin block with seeds
 * taken from one stack, paying the seed cost and rolling everything back when the cost cannot settle.
 */
public final class HerbalismSowing {
  private final Adaptation<?> owner;

  /** The stack seeds come from. */
  public interface SeedStack {
    ItemRef read();

    void write(ItemRef item);
  }

  /** One planting run: {@code reserve} seeds stay in the stack. */
  public record Request(PlayerRef player, BlockRef origin, SeedStack seeds, Id seedType, Id cropType, int radius,
                        int maxCrops, int reserve) {
  }

  public HerbalismSowing(Adaptation<?> owner) {
    this.owner = owner;
  }

  public static SeedStack mainHand(PlayerRef player) {
    return new SeedStack() {
      @Override
      public ItemRef read() {
        return player.inventory().mainHand();
      }

      @Override
      public void write(ItemRef item) {
        player.inventory().setMainHand(item);
      }
    };
  }

  public static SeedStack slot(PlayerRef player, int slot) {
    return new SeedStack() {
      @Override
      public ItemRef read() {
        return player.inventory().item(slot);
      }

      @Override
      public void write(ItemRef item) {
        player.inventory().setItem(slot, item);
      }
    };
  }

  public static boolean ownsPlantingTarget(PlayerRef player, Loc location) {
    return !Tasks.isFoliaThreading()
        || (Tasks.isOwnedByCurrentRegion(player) && Tasks.isOwnedByCurrentRegion(location));
  }

  static boolean acceptsPlantingSettlement(boolean defaultConsumed, boolean defaultCostSuppressed, boolean settled) {
    return defaultConsumed || (defaultCostSuppressed && settled);
  }

  /** Plants and returns the count, or 0 when nothing was planted or the cost failed. */
  public int plant(Request request) {
    PlayerRef p = request.player();
    int available = p.gameMode() == GameMode.CREATIVE
        ? request.maxCrops()
        : Math.max(0, request.seeds().read().amount() - request.reserve());
    List<BlockRef> targets = findPlantingTargets(p, request.origin(), request.seedType(), request.radius(),
        Math.min(request.maxCrops(), available));
    if (targets.isEmpty()) {
      return 0;
    }

    if (p.gameMode() == GameMode.CREATIVE) {
      int planted = plantTargets(p, targets, request.cropType());
      emitPlantingFx(targets.subList(0, planted));
      return planted;
    }

    int requested = targets.size();
    AbilityCharge charge = owner.payItemCostDeferred(
        p,
        "seeds",
        AdaptCore.platform().host().items().create(request.seedType()),
        requested,
        () -> hasSeeds(request.seeds(), request.seedType(), requested)
    );
    if (!charge.allowed()) {
      return 0;
    }

    int planted = plantTargets(p, targets, request.cropType());
    if (planted != requested) {
      rollbackPlanting(targets, planted, request.cropType());
      owner.refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return 0;
    }

    boolean defaultConsumed = !charge.defaultCostSuppressed()
        && consumeSeeds(request.seeds(), request.seedType(), planted);
    if (!charge.defaultCostSuppressed() && !defaultConsumed) {
      rollbackPlanting(targets, planted, request.cropType());
      owner.refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return 0;
    }

    boolean settled = owner.settleCost(charge.activationId());
    if (!acceptsPlantingSettlement(defaultConsumed, charge.defaultCostSuppressed(), settled)) {
      rollbackPlanting(targets, planted, request.cropType());
      owner.refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return 0;
    }

    emitPlantingFx(targets);
    return planted;
  }

  private List<BlockRef> findPlantingTargets(PlayerRef p, BlockRef origin, Id seedType, int radius, int limit) {
    List<BlockRef> targets = new ArrayList<>(Math.max(0, limit));
    WorldRef world = origin.world();
    int y = origin.y();

    for (int x = -radius; x <= radius && targets.size() < limit; x++) {
      for (int z = -radius; z <= radius && targets.size() < limit; z++) {
        int blockX = origin.x() + x;
        int blockZ = origin.z() + z;
        Loc baseLocation = new Loc(world, blockX, y, blockZ, 0F, 0F);
        if (!ownsPlantingTarget(p, baseLocation)) {
          continue;
        }

        BlockRef base = world.blockAt(blockX, y, blockZ);
        if (!isValidBase(seedType, base.type())) {
          continue;
        }

        Loc cropLocation = new Loc(world, blockX, y + 1, blockZ, 0F, 0F);
        if (!ownsPlantingTarget(p, cropLocation)) {
          continue;
        }

        BlockRef crop = world.blockAt(blockX, y + 1, blockZ);
        if (!crop.isAir()
            || !owner.canBlockPlace(p, cropLocation)
            || !ProtectionProbes.attemptBlockPlaceProbe(p, crop)) {
          continue;
        }
        if (!ownsPlantingTarget(p, cropLocation)
            || !crop.isAir()
            || !isValidBase(seedType, base.type())) {
          continue;
        }

        targets.add(crop);
      }
    }

    return targets;
  }

  private int plantTargets(PlayerRef player, List<BlockRef> targets, Id cropType) {
    int planted = 0;
    for (BlockRef crop : targets) {
      if (!ownsPlantingTarget(player, crop.location()) || !crop.isAir()) {
        break;
      }
      crop.setType(cropType, true);
      planted++;
    }
    return planted;
  }

  private static boolean hasSeeds(SeedStack seeds, Id seedType, int amount) {
    ItemRef stack = seeds.read();
    return stack.type().equals(seedType) && stack.amount() >= amount;
  }

  private static boolean consumeSeeds(SeedStack seeds, Id seedType, int amount) {
    if (!hasSeeds(seeds, seedType, amount)) {
      return false;
    }

    ItemRef stack = seeds.read();
    int remaining = stack.amount() - amount;
    if (remaining <= 0) {
      seeds.write(AdaptCore.platform().host().items().empty());
    } else {
      stack.setAmount(remaining);
      seeds.write(stack);
    }
    return true;
  }

  private static void rollbackPlanting(List<BlockRef> targets, int planted, Id cropType) {
    for (int index = 0; index < planted; index++) {
      BlockRef crop = targets.get(index);
      if ((!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(crop.location()))
          && crop.type().equals(cropType)) {
        crop.setType(HerbalismSeedSower.AIR, true);
      }
    }
  }

  private void emitPlantingFx(List<BlockRef> targets) {
    int count = Math.min(12, targets.size());
    for (int index = 0; index < count; index++) {
      HerbalismSeedSower.plantFx(owner, targets.get(index));
    }
  }

  private static boolean isValidBase(Id seedType, Id base) {
    if (seedType.equals(HerbalismSeedSower.NETHER_WART)) {
      return base.equals(HerbalismSeedSower.SOUL_SAND);
    }

    return base.equals(HerbalismSeedSower.FARMLAND);
  }
}
