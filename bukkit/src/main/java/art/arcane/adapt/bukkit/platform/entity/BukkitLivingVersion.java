package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;

final class BukkitLivingVersion {
  private BukkitLivingVersion() {
  }

  /** Damages with the given damage type; false, without damaging, when the type is unknown. */
  static boolean damage(LivingEntity living, double amount, Id damageType, Entity attacker) {
    DamageType type = damageType == null ? null : Registry.DAMAGE_TYPE.get(BukkitRefs.key(damageType));
    if (type == null) {
      return false;
    }
    DamageSource.Builder builder = DamageSource.builder(type);
    if (attacker != null) {
      builder.withCausingEntity(attacker).withDirectEntity(attacker);
    }
    living.damage(amount, builder.build());
    return true;
  }

  static void heal(LivingEntity living, double amount) {
    PaperApi.heal(living, amount);
  }

  static Attribute attribute(LivingEntity living, Id attribute) {
    return BukkitResolve.attribute(attribute);
  }

  static AttributeModifier modifier(NamespacedKey key, double amount, AttributeModifier.Operation operation) {
    return new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY);
  }

  static AttributeModifier modifier(AttributeInstance instance, NamespacedKey key) {
    return PaperApi.modifier(instance, key);
  }

  static void removeModifier(AttributeInstance instance, NamespacedKey key) {
    PaperApi.removeModifier(instance, key);
  }

  static Id id(AttributeModifier modifier) {
    return BukkitConversions.id(modifier.getKey());
  }
}
