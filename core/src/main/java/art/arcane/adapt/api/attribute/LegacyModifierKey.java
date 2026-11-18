package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A modifier key that also matches the forms pre-1.21 builds left on entities: the legacy UUID
 * {@code nameUUIDFromBytes("<namespace>-<path>")}, which vanilla now loads as {@code minecraft:<uuid>}, and the legacy
 * name {@code <namespace>-<path>} as the key path.
 */
public record LegacyModifierKey(Id key, String legacyName, Id legacyUuidKey) {
  public LegacyModifierKey {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(legacyName, "legacyName");
    Objects.requireNonNull(legacyUuidKey, "legacyUuidKey");
  }

  public static LegacyModifierKey of(Id key) {
    String legacyName = key.namespace() + "-" + key.path();
    UUID legacyUuid = UUID.nameUUIDFromBytes(legacyName.getBytes(StandardCharsets.UTF_8));
    return new LegacyModifierKey(key, legacyName, Id.minecraft(legacyUuid.toString()));
  }

  public boolean matches(Id candidate) {
    return key.equals(candidate) || legacyUuidKey.equals(candidate) || legacyName.equals(candidate.path());
  }

  public List<AttributeMod> find(LivingRef entity, Id attribute) {
    List<AttributeMod> found = new ArrayList<>(1);
    AttributeMod current = entity.modifier(attribute, key);
    if (current != null) {
      found.add(current);
    }
    for (AttributeMod modifier : entity.modifiers(attribute)) {
      if (!key.equals(modifier.key()) && matches(modifier.key())) {
        found.add(modifier);
      }
    }
    return found;
  }

  public boolean present(LivingRef entity, Id attribute) {
    if (entity.modifier(attribute, key) != null) {
      return true;
    }
    for (AttributeMod modifier : entity.modifiers(attribute)) {
      if (matches(modifier.key())) {
        return true;
      }
    }
    return false;
  }

  public void remove(LivingRef entity, Id attribute) {
    entity.removeModifier(attribute, key);
    for (AttributeMod modifier : entity.modifiers(attribute)) {
      if (!key.equals(modifier.key()) && matches(modifier.key())) {
        entity.removeModifier(attribute, modifier.key());
      }
    }
  }

  public boolean set(LivingRef entity, Id attribute, double amount, AttributeOperation operation) {
    remove(entity, attribute);
    return entity.setModifier(attribute, new AttributeMod(key, amount, operation));
  }

  public boolean setTransient(LivingRef entity, Id attribute, double amount, AttributeOperation operation) {
    remove(entity, attribute);
    return entity.setTransientModifier(attribute, new AttributeMod(key, amount, operation));
  }
}
