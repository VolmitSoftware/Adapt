package art.arcane.adapt.api.golem;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

public record GolemRuneEffects(double radiusBonus, int extraChannels, double reclaimDelayMultiplier, double bonusYield) {
  public static final GolemRuneEffects NONE = new GolemRuneEffects(0D, 0, 1D, 0D);
  public static final Set<GolemCoreType> YIELD_CORES = Set.of(GolemCoreType.HARVESTER, GolemCoreType.LUMBER, GolemCoreType.BREAKER,
      GolemCoreType.ANGLER);
  private static final int MAX_EXTRA_CHANNELS = Integer.bitCount(ChannelMask.ALL) - 1;

  public GolemRuneEffects {
    radiusBonus = Double.isFinite(radiusBonus) ? Math.max(0D, radiusBonus) : 0D;
    extraChannels = Math.clamp(extraChannels, 0, MAX_EXTRA_CHANNELS);
    reclaimDelayMultiplier = Double.isFinite(reclaimDelayMultiplier) ? Math.clamp(reclaimDelayMultiplier, 0D, 1D) : 1D;
    bonusYield = Double.isFinite(bonusYield) ? Math.clamp(bonusYield, 0D, 1D) : 0D;
  }

  public static Tier effectiveTier(int slottedTier, boolean tierUp, double tierThreeBoost) {
    if (!tierUp || !GolemRuneType.isTier(slottedTier)) {
      return new Tier(slottedTier, 1D);
    }
    if (slottedTier < GolemRuneType.MAX_TIER) {
      return new Tier(slottedTier + 1, 1D);
    }
    return new Tier(GolemRuneType.MAX_TIER, 1D + (Double.isFinite(tierThreeBoost) ? Math.max(0D, tierThreeBoost) : 0D));
  }

  public int channelSlots() {
    return 1 + extraChannels;
  }

  public int channels(int mask) {
    int remaining = ChannelMask.normalize(mask);
    int kept = ChannelMask.NONE;
    for (int slot = 0; slot < channelSlots() && remaining != ChannelMask.NONE; slot++) {
      int lowest = Integer.lowestOneBit(remaining);
      kept |= lowest;
      remaining &= ~lowest;
    }
    return kept;
  }

  public double bonusYield(GolemCoreType core) {
    return YIELD_CORES.contains(core) ? bonusYield : 0D;
  }

  public List<ItemStack> withBonusYield(GolemCoreType core, List<ItemStack> drops, DoubleSupplier random) {
    double chance = bonusYield(core);
    if (chance <= 0D || drops.isEmpty()) {
      return drops;
    }
    List<ItemStack> yielded = new ArrayList<>(drops.size() * 2);
    for (ItemStack drop : drops) {
      yielded.add(drop);
      if (random.getAsDouble() < chance) {
        yielded.add(drop.clone());
      }
    }
    return yielded;
  }

  public record Tier(int tier, double multiplier) {
    public double scale(double value) {
      return value * multiplier;
    }

    public int scale(int value) {
      return Math.clamp(Math.round(value * multiplier), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
  }
}
