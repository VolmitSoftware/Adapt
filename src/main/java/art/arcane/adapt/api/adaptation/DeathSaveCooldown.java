package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public enum DeathSaveCooldown {
  LAST_RITES("tragoul_last_rites"),
  HOURGLASS_GUARD("chronos_hourglass_guard"),
  VOID_SKIN("rift_void_skin");

  private final NamespacedKey deadlineKey;

  DeathSaveCooldown(String name) {
    deadlineKey = new NamespacedKey("adapt", "death_save_" + name);
  }

  public boolean isReady(Player player, long now) {
    return remaining(player, now) == 0L;
  }

  public long remaining(Player player, long now) {
    try {
      long deadline = player.getPersistentDataContainer().getOrDefault(deadlineKey, PersistentDataType.LONG, 0L);
      return deadline <= now ? 0L : deadline - now;
    } catch (RuntimeException exception) {
      reportFailure(player, exception);
      return Long.MAX_VALUE;
    }
  }

  public boolean activate(Player player, long now, long durationMillis) {
    if (!isReady(player, now)) {
      return false;
    }
    long duration = Math.max(0L, durationMillis);
    long deadline = now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
    try {
      player.getPersistentDataContainer().set(deadlineKey, PersistentDataType.LONG, deadline);
      return true;
    } catch (RuntimeException exception) {
      reportFailure(player, exception);
      return false;
    }
  }

  private void reportFailure(Player player, RuntimeException exception) {
    Adapt.error("Could not persist death-save cooldown " + deadlineKey + " for " + player.getUniqueId() + ".");
    Adapt.error(exception);
  }
}
