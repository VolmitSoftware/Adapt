package art.arcane.adapt.engine.version;

import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RuntimeBindings implements IBindings {
  private static final List<EntityType> INVALID_DAMAGEABLE_ENTITIES = detectInvalidDamageableEntities();

  private static List<EntityType> detectInvalidDamageableEntities() {
    Set<EntityType> entities = new LinkedHashSet<>();

    addIfPresent(entities,
        "ARMOR_STAND",
        "ITEM_FRAME",
        "GLOW_ITEM_FRAME",
        "PAINTING",
        "LEASH_HITCH",
        "LEASH_KNOT",
        "EVOKER_FANGS",
        "MARKER",
        "BOAT",
        "CHEST_BOAT",
        "MINECART"
    );
    addByPrefix(entities, "MINECART_");
    addBySuffix(entities, "_MINECART");
    addBySuffix(entities, "_BOAT");
    addBySuffix(entities, "_CHEST_BOAT");
    addBySuffix(entities, "_RAFT");
    addBySuffix(entities, "_CHEST_RAFT");

    return List.copyOf(entities);
  }

  private static void addIfPresent(Set<EntityType> entities, String... names) {
    for (String name : names) {
      try {
        entities.add(EntityType.valueOf(name));
      } catch (IllegalArgumentException ignored) {
        // Entity was renamed/removed in this API version.
      }
    }
  }

  private static void addByPrefix(Set<EntityType> entities, String prefix) {
    for (EntityType entity : EntityType.values()) {
      if (entity.name().startsWith(prefix)) {
        entities.add(entity);
      }
    }
  }

  private static void addBySuffix(Set<EntityType> entities, String suffix) {
    for (EntityType entity : EntityType.values()) {
      if (entity.name().endsWith(suffix)) {
        entities.add(entity);
      }
    }
  }

  @Override
  public IAttribute getAttribute(Attributable attributable, Attribute modifier) {
    return Optional.ofNullable(attributable.getAttribute(modifier))
        .map(RuntimeAttribute::new)
        .orElse(null);
  }

  @Override
  @Unmodifiable
  public List<EntityType> getInvalidDamageableEntities() {
    return INVALID_DAMAGEABLE_ENTITIES;
  }
}
