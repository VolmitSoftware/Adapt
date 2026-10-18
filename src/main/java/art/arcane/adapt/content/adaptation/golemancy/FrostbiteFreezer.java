package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FrostbiteFreezer {
  static final NamespacedKey KEY = new NamespacedKey("adapt", "golem_frostbite");

  private final Map<UUID, Lock> locks = new ConcurrentHashMap<>();

  void freeze(LivingEntity victim, int ticks) {
    int duration = Math.max(1, ticks);
    if (!PaperCompat.lockFreezeTicks(victim, true)) {
      victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), duration));
      return;
    }
    victim.setFreezeTicks(victim.getMaxFreezeTicks());
    victim.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
    UUID id = victim.getUniqueId();
    Lock lock = new Lock(victim, new Object());
    locks.put(id, lock);
    if (!J.runEntity(victim, () -> expire(lock), duration, () -> locks.remove(id, lock))) {
      expire(lock);
    }
  }

  void thaw(Entity entity) {
    if (!locks.isEmpty() && locks.remove(entity.getUniqueId()) != null) {
      release(entity);
    }
  }

  void recover(Entity entity) {
    if (!locks.containsKey(entity.getUniqueId()) && entity.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE)) {
      release(entity);
    }
  }

  void thawAll() {
    for (Lock lock : List.copyOf(locks.values())) {
      LivingEntity victim = lock.victim();
      if (!locks.remove(victim.getUniqueId(), lock)) {
        continue;
      }
      if (J.isOwnedByCurrentRegion(victim)) {
        release(victim);
      } else {
        J.runEntity(victim, () -> release(victim));
      }
    }
  }

  private void expire(Lock lock) {
    if (locks.remove(lock.victim().getUniqueId(), lock)) {
      release(lock.victim());
    }
  }

  private static void release(Entity entity) {
    PaperCompat.lockFreezeTicks(entity, false);
    entity.getPersistentDataContainer().remove(KEY);
  }

  private record Lock(LivingEntity victim, Object token) {
  }
}
