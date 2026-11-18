package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemRitualMarker {
  public static final DataKey<Byte> KEY = DataKey.of(Id.of("adapt", "golem_ritual"), DataCodecs.BYTE);
  private static final byte AWARE = 1;
  private static final byte UNAWARE = 0;
  private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

  private GolemRitualMarker() {
  }

  public static boolean has(EntityRef entity) {
    return entity.data().has(KEY);
  }

  public static Optional<Boolean> wasAware(EntityRef entity) {
    Byte stored = entity.data().get(KEY);
    return stored == null ? Optional.empty() : Optional.of(stored == AWARE);
  }

  public static void start(MobRef mob, boolean wasAware) {
    ACTIVE.add(mob.uuid());
    DataHolder data = mob.data();
    if (!data.has(KEY)) {
      data.set(KEY, wasAware ? AWARE : UNAWARE);
    }
  }

  public static boolean finish(MobRef mob) {
    return finish(mob, mob.isValid() ? GolemScale.scaleOf(mob) : null);
  }

  public static void release(EntityRef entity) {
    ACTIVE.remove(entity.uuid());
  }

  static boolean finish(MobRef mob, GolemAttribute scale) {
    ACTIVE.remove(mob.uuid());
    return mob.isValid() && restore(mob, scale);
  }

  static boolean recover(MobRef mob, GolemAttribute scale) {
    return !ACTIVE.contains(mob.uuid()) && restore(mob, scale);
  }

  private static boolean restore(MobRef mob, GolemAttribute scale) {
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
    mob.data().remove(KEY);
    return true;
  }
}
