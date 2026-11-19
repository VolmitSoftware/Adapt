package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Damage, healing and attribute modifiers on Paper 1.20.1: damage types have no API, healing sets health directly,
 * and a modifier is identified by a UUID derived from its key and named after the key.
 */
final class BukkitLivingVersion {
  private BukkitLivingVersion() {
  }

  static boolean damage(LivingEntity living, double amount, Id damageType, Entity attacker) {
    return false;
  }

  static void heal(LivingEntity living, double amount) {
    if (amount <= 0 || living.isDead()) {
      return;
    }
    AttributeInstance maximum = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
    double cap = maximum == null ? living.getHealth() : maximum.getValue();
    living.setHealth(Math.min(cap, living.getHealth() + amount));
  }

  /** {@code jump_strength} was {@code horse.jump_strength} on 1.20.1, carried by horses only. */
  static Attribute attribute(LivingEntity living, Id attribute) {
    Attribute resolved = BukkitResolve.attribute(attribute);
    if (resolved != null || !(living instanceof AbstractHorse)) {
      return resolved;
    }
    Id mount = ContentAliases.mountAttribute(attribute);
    return mount == null ? null : BukkitResolve.attribute(mount);
  }

  static AttributeModifier modifier(NamespacedKey key, double amount, AttributeModifier.Operation operation) {
    return new AttributeModifier(uuid(key), key.toString(), amount, operation);
  }

  static AttributeModifier modifier(AttributeInstance instance, NamespacedKey key) {
    UUID id = uuid(key);
    for (AttributeModifier modifier : instance.getModifiers()) {
      if (id.equals(modifier.getUniqueId())) {
        return modifier;
      }
    }
    return null;
  }

  static void removeModifier(AttributeInstance instance, NamespacedKey key) {
    UUID id = uuid(key);
    for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
      if (id.equals(modifier.getUniqueId())) {
        instance.removeModifier(modifier);
      }
    }
  }

  static Id id(AttributeModifier modifier) {
    Id named = Id.tryParse(modifier.getName());
    return named != null && modifier.getUniqueId().equals(uuid(new NamespacedKey(named.namespace(), named.path())))
        ? named : Id.of("minecraft", modifier.getUniqueId().toString());
  }

  private static UUID uuid(NamespacedKey key) {
    return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
  }
}
