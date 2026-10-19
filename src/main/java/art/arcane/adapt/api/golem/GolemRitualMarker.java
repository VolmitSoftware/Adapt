package art.arcane.adapt.api.golem;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemRitualMarker {
  public static final NamespacedKey KEY = new NamespacedKey("adapt", "golem_ritual");
  private static final byte AWARE = 1;
  private static final byte UNAWARE = 0;
  private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

  private GolemRitualMarker() {
  }

  public static boolean has(Entity entity) {
    return entity.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
  }

  public static Optional<Boolean> wasAware(Entity entity) {
    Byte stored = entity.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE);
    return stored == null ? Optional.empty() : Optional.of(stored == AWARE);
  }

  public static void start(Mob mob, boolean wasAware) {
    ACTIVE.add(mob.getUniqueId());
    PersistentDataContainer data = mob.getPersistentDataContainer();
    if (!data.has(KEY, PersistentDataType.BYTE)) {
      data.set(KEY, PersistentDataType.BYTE, wasAware ? AWARE : UNAWARE);
    }
  }

  public static boolean finish(Mob mob) {
    return finish(mob, mob.isValid() ? GolemScale.scaleOf(mob) : null);
  }

  public static void release(Entity entity) {
    ACTIVE.remove(entity.getUniqueId());
  }

  static boolean finish(Mob mob, AttributeInstance scale) {
    ACTIVE.remove(mob.getUniqueId());
    return mob.isValid() && restore(mob, scale);
  }

  static boolean recover(Mob mob, AttributeInstance scale) {
    return !ACTIVE.contains(mob.getUniqueId()) && restore(mob, scale);
  }

  private static boolean restore(Mob mob, AttributeInstance scale) {
    Optional<Boolean> aware = wasAware(mob);
    if (aware.isEmpty()) {
      return false;
    }
    if (!GolemState.has(mob)) {
      mob.setAware(aware.get());
    }
    if (scale != null) {
      GolemScale.clearStepModifier(scale);
    }
    mob.getPersistentDataContainer().remove(KEY);
    return true;
  }
}
