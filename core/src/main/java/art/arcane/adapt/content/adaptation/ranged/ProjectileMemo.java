package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.platform.entity.EntityRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory value attached to a projectile by UUID, kept like plugin metadata: not saved with the entity, readable
 * after its chunk reloads, dropped when the projectile leaves the world for good ({@link #forgetAll(UUID)}).
 */
final class ProjectileMemo<T> {
  private static final List<ProjectileMemo<?>> ALL = new CopyOnWriteArrayList<>();

  private final Map<UUID, T> values = new ConcurrentHashMap<>();

  private ProjectileMemo() {
  }

  static <T> ProjectileMemo<T> create() {
    ProjectileMemo<T> memo = new ProjectileMemo<>();
    ALL.add(memo);
    return memo;
  }

  static void forgetAll(UUID entityId) {
    for (ProjectileMemo<?> memo : ALL) {
      memo.values.remove(entityId);
    }
  }

  T get(EntityRef entity) {
    return entity == null ? null : values.get(entity.uuid());
  }

  T getOrDefault(EntityRef entity, T fallback) {
    T value = get(entity);
    return value == null ? fallback : value;
  }

  boolean has(EntityRef entity) {
    return entity != null && values.containsKey(entity.uuid());
  }

  void set(EntityRef entity, T value) {
    values.put(entity.uuid(), value);
  }

  void remove(EntityRef entity) {
    values.remove(entity.uuid());
  }
}
