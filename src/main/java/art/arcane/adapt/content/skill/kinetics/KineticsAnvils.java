package art.arcane.adapt.content.skill.kinetics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class KineticsAnvils {
  private final Object ledgerLock = new Object();
  private final ConcurrentHashMap<BlockKey, Claim> placements = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Claim> falls = new ConcurrentHashMap<>();

  public static boolean isAnvil(Material material) {
    if (material == null) {
      return false;
    }
    return switch (material) {
      case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> true;
      default -> false;
    };
  }

  public static double crushXp(double baseXp, double fallDistance, double fallFactor,
      double targetMaxHealth, double healthFactor, double damageDealt,
      boolean kill, double killBonusMultiplier, double perEventCap) {
    if (!isPositiveFinite(baseXp)
        || !Double.isFinite(fallDistance)
        || fallDistance < 1D
        || !isPositiveFinite(fallFactor)
        || !isPositiveFinite(targetMaxHealth)
        || !isPositiveFinite(healthFactor)
        || !Double.isFinite(damageDealt)
        || damageDealt < 0D
        || !isPositiveFinite(killBonusMultiplier)
        || !isPositiveFinite(perEventCap)) {
      return 0;
    }
    double killFactor = kill
        ? killBonusMultiplier
        : Math.max(0.1, damageDealt / Math.max(1, targetMaxHealth));
    double raw = (baseXp + fallDistance * fallFactor)
        * (1 + targetMaxHealth * healthFactor / 20.0)
        * killFactor;
    if (!isPositiveFinite(raw)) {
      return 0D;
    }
    double payout = Math.min(perEventCap, raw);
    return isPositiveFinite(payout) ? payout : 0D;
  }

  public static double shareXp(double crushXp, double shareFactor) {
    if (!isPositiveFinite(crushXp) || !isPositiveFinite(shareFactor)) {
      return 0D;
    }
    double payout = crushXp * shareFactor;
    return isPositiveFinite(payout) ? payout : 0D;
  }

  public void recordPlacement(Location blockLocation, UUID placer, long nowMs) {
    synchronized (ledgerLock) {
      placements.put(keyOf(blockLocation), new Claim(placer, nowMs));
    }
  }

  public void beginFall(UUID fallingBlockId, Location fromBlock, long nowMs) {
    synchronized (ledgerLock) {
      Claim claim = placements.remove(keyOf(fromBlock));
      if (claim == null) {
        falls.remove(fallingBlockId);
        return;
      }
      falls.put(fallingBlockId, claim);
    }
  }

  public void land(UUID fallingBlockId, Location blockLocation) {
    synchronized (ledgerLock) {
      Claim claim = falls.remove(fallingBlockId);
      BlockKey destination = keyOf(blockLocation);
      placements.remove(destination);
      if (claim != null) {
        placements.put(destination, claim);
      }
    }
  }

  public void clearPlacement(Location blockLocation) {
    synchronized (ledgerLock) {
      placements.remove(keyOf(blockLocation));
    }
  }

  public void clearFall(UUID fallingBlockId) {
    synchronized (ledgerLock) {
      falls.remove(fallingBlockId);
    }
  }

  public UUID ownerOf(UUID fallingBlockId, long nowMs, long ttlMs) {
    Claim claim = falls.get(fallingBlockId);
    if (claim == null) {
      return null;
    }
    if (nowMs - claim.atMs() > ttlMs) {
      falls.remove(fallingBlockId, claim);
      return null;
    }
    return claim.placer();
  }

  public void transferPiston(Location from, Location to) {
    transferPistons(List.of(new PistonTransfer(from, to)));
  }

  public void transferPistons(List<PistonTransfer> transfers) {
    if (transfers == null || transfers.isEmpty()) {
      return;
    }

    Map<BlockKey, BlockKey> movements = new HashMap<>(transfers.size());
    for (PistonTransfer transfer : transfers) {
      if (transfer != null && transfer.from() != null && transfer.to() != null) {
        movements.put(keyOf(transfer.from()), keyOf(transfer.to()));
      }
    }
    if (movements.isEmpty()) {
      return;
    }

    synchronized (ledgerLock) {
      Map<BlockKey, Claim> movingClaims = new HashMap<>(movements.size());
      for (BlockKey source : movements.keySet()) {
        Claim claim = placements.get(source);
        if (claim != null) {
          movingClaims.put(source, claim);
        }
      }
      for (BlockKey source : movements.keySet()) {
        placements.remove(source);
      }
      for (BlockKey destination : movements.values()) {
        placements.remove(destination);
      }
      for (Map.Entry<BlockKey, BlockKey> movement : movements.entrySet()) {
        Claim claim = movingClaims.get(movement.getKey());
        if (claim != null) {
          placements.put(movement.getValue(), claim);
        }
      }
    }
  }

  public void expire(long nowMs, long ttlMs) {
    synchronized (ledgerLock) {
      placements.values().removeIf(claim -> nowMs - claim.atMs() > ttlMs);
      falls.values().removeIf(claim -> nowMs - claim.atMs() > ttlMs);
    }
  }

  private static BlockKey keyOf(Location location) {
    World world = location.getWorld();
    String worldName = world == null ? "" : world.getName();
    return new BlockKey(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private static boolean isPositiveFinite(double value) {
    return Double.isFinite(value) && value > 0D;
  }

  public record CrushResult(double xp, boolean advancementQualifies) {}

  public record PistonTransfer(Location from, Location to) {}

  private record BlockKey(String world, int x, int y, int z) {}

  private record Claim(UUID placer, long atMs) {}
}
