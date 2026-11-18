package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.attribute.AdaptAttributeResolver;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.RuntimeAttribute;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BukkitAttributeResolver implements AdaptAttributeResolver {
  @Override
  public boolean setTransientModifier(LivingRef entity, Id attribute, AttributeMod modifier) {
    IAttribute handle = resolve(entity, attribute);
    if (handle == null) {
      return false;
    }
    NamespacedKey key = BukkitRefs.key(modifier.key());
    handle.setTransientModifier(legacyUuid(key), key, modifier.amount(),
        BukkitConversions.operation(modifier.operation()));
    return true;
  }

  @Override
  public void removeModifier(LivingRef entity, Id attribute, Id key) {
    IAttribute handle = resolve(entity, attribute);
    if (handle != null) {
      NamespacedKey bukkitKey = BukkitRefs.key(key);
      handle.removeModifier(legacyUuid(bukkitKey), bukkitKey);
    }
  }

  @Override
  public int removeAllInNamespace(LivingRef entity, Id attribute, String namespace) {
    IAttribute handle = resolve(entity, attribute);
    return handle == null ? 0 : handle.removeAllInNamespace(namespace);
  }

  private static IAttribute resolve(LivingRef entity, Id attribute) {
    LivingEntity living = BukkitRefs.living(entity);
    Attribute bukkitAttribute = attribute == null ? null : BukkitRefs.attribute(attribute);
    if (living == null || bukkitAttribute == null) {
      return null;
    }
    AttributeInstance instance = living.getAttribute(bukkitAttribute);
    return instance == null ? null : new RuntimeAttribute(instance);
  }

  private static UUID legacyUuid(NamespacedKey key) {
    return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
  }
}
