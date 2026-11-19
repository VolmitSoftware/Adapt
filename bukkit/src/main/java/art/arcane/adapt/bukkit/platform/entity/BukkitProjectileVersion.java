package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

final class BukkitProjectileVersion {
  private BukkitProjectileVersion() {
  }

  static Id basePotion(Arrow arrow) {
    PotionType base = arrow.getBasePotionType();
    return base == null ? null : BukkitResolve.id(base);
  }

  static void setBasePotion(Arrow arrow, Id potion) {
    arrow.setBasePotionType(potion == null ? null : BukkitResolve.potion(potion));
  }

  static boolean isInWorld(Projectile projectile) {
    return projectile.isInWorld();
  }

  static ItemStack weapon(AbstractArrow arrow) {
    return arrow.getWeapon();
  }

  static void setWeapon(AbstractArrow arrow, ItemStack weapon) {
    arrow.setWeapon(weapon);
  }
}
