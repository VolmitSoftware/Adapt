package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.event.entity.DamageCause;

/**
 * The last damage an entity took that was not cancelled, like Bukkit's last damage cause. {@code damager} is the
 * direct damaging entity (the arrow, not its shooter) when the damage came from an entity, otherwise null.
 * {@code finalDamage} is the amount after armour, effects, enchantments and absorption.
 */
public record LastDamage(DamageCause cause, EntityRef damager, double finalDamage) {
  public boolean byEntity() {
    return damager != null;
  }
}
