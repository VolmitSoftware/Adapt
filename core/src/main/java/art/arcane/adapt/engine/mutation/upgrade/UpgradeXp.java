package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.engine.mutation.engine.FarmRules;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one rule for skill XP from upgrades. An upgrade pays only for acting on a natural block, a natural mob or a real
 * attacker, or for a discovery that cannot be repeated, never for its own movement, self-inflicted or environmental
 * events, and each upgrade pays a player at most
 * {@link AdaptConfig.Mutations#getUpgradeXpPerMinute()} a minute.
 */
public final class UpgradeXp {
  private static final Map<SkillUpgrade<?>, Map<UUID, UpgradeXp>> ALLOWANCES =
      Collections.synchronizedMap(new WeakHashMap<>());

  private double used;
  private long updatedAt;

  /** A living mob other than the player that is not spawner, egg, bred, minion, grave or the player's own pet. */
  public static boolean naturalMob(PlayerRef player, EntityRef mob) {
    return mob instanceof LivingRef && !mob.uuid().equals(player.uuid()) && !FarmRules.farmed(player, mob);
  }

  /** The damager, or the shooter of a projectile, is a natural mob or another player. */
  public static boolean realAttacker(PlayerRef player, EntityRef damager) {
    EntityRef attacker = damager instanceof ProjectileRef projectile ? projectile.shooter() : damager;
    return attacker != null && naturalMob(player, attacker);
  }

  /** A block no player placed, broke and replaced, or grew with bone meal. */
  public static boolean naturalBlock(BlockRef block) {
    return block != null && !FarmRules.farmed(block);
  }

  /** A crop that was not grown with bone meal. */
  public static boolean naturalHarvest(BlockRef crop) {
    return crop != null && !FarmRules.farmedHarvest(crop);
  }

  /** What of {@code amount} {@code upgrade} may still pay the player this minute; the rest is dropped. */
  public static double claim(SkillUpgrade<?> upgrade, PlayerRef player, double amount) {
    return ALLOWANCES.computeIfAbsent(upgrade, (SkillUpgrade<?> ignored) -> new ConcurrentHashMap<>())
        .computeIfAbsent(player.uuid(), (UUID ignored) -> new UpgradeXp()).claim(amount, System.currentTimeMillis());
  }

  /** Drops a player's allowances, for when they leave. */
  public static void forget(UUID player) {
    synchronized (ALLOWANCES) {
      for (Map<UUID, UpgradeXp> perPlayer : ALLOWANCES.values()) {
        perPlayer.remove(player);
      }
    }
  }

  /** Takes what is left of {@code amount} under the per-minute allowance at {@code now}. */
  synchronized double claim(double amount, long now) {
    double perMinute = AdaptConfig.get().getMutations().getUpgradeXpPerMinute();
    if (updatedAt > 0L && now > updatedAt) {
      used = Math.max(0D, used - ((now - updatedAt) / 60_000D) * perMinute);
    }
    updatedAt = Math.max(updatedAt, now);
    double granted = Math.max(0D, Math.min(amount, perMinute - used));
    used += granted;
    return granted;
  }
}
