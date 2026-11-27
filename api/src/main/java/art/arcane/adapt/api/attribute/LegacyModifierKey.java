package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A modifier key that also matches the forms pre-1.21 builds left on entities: the legacy UUID
 * {@code nameUUIDFromBytes("<namespace>-<path>")}, which vanilla now loads as {@code minecraft:<uuid>}, and the legacy
 * name {@code <namespace>-<path>} as the key path.
 *
 * @param key the key Adapt writes now
 * @param legacyName the legacy key path
 * @param legacyUuidKey the legacy UUID as a {@code minecraft} key
 */
@ApiStatus.Experimental
public record LegacyModifierKey(Id key, String legacyName, Id legacyUuidKey) {
  public LegacyModifierKey {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(legacyName, "legacyName");
    Objects.requireNonNull(legacyUuidKey, "legacyUuidKey");
  }

  /** The key {@code key} with the legacy forms older builds derived from it. */
  public static LegacyModifierKey of(Id key) {
    String legacyName = key.namespace() + "-" + key.path();
    UUID legacyUuid = UUID.nameUUIDFromBytes(legacyName.getBytes(StandardCharsets.UTF_8));
    return new LegacyModifierKey(key, legacyName, Id.minecraft(legacyUuid.toString()));
  }

  /** Whether {@code candidate} is this key in any of its forms. */
  public boolean matches(Id candidate) {
    return key.equals(candidate) || legacyUuidKey.equals(candidate) || legacyName.equals(candidate.path());
  }

  /** The modifiers on the entity's attribute under this key in any form, the current key first. */
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

  /** Whether the entity's attribute has a modifier under this key in any form. */
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

  /** Removes every modifier under this key, in any form, from the entity's attribute. */
  public void remove(LivingRef entity, Id attribute) {
    entity.removeModifier(attribute, key);
    for (AttributeMod modifier : entity.modifiers(attribute)) {
      if (!key.equals(modifier.key()) && matches(modifier.key())) {
        entity.removeModifier(attribute, modifier.key());
      }
    }
  }

  /** Replaces every form of this key on the attribute with one saved modifier; false when the platform refused it. */
  public boolean set(LivingRef entity, Id attribute, double amount, AttributeOperation operation) {
    remove(entity, attribute);
    return entity.setModifier(attribute, new AttributeMod(key, amount, operation));
  }

  /** As {@link #set}, with a modifier the entity does not save. */
  public boolean setTransient(LivingRef entity, Id attribute, double amount, AttributeOperation operation) {
    remove(entity, attribute);
    return entity.setTransientModifier(attribute, new AttributeMod(key, amount, operation));
  }
}
