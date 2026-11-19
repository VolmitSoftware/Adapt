package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/** Projectile state on Paper 1.20.1: arrows carry potion data and no weapon, and a valid entity counts as in the world. */
final class BukkitProjectileVersion {
  private BukkitProjectileVersion() {
  }

  static Id basePotion(Arrow arrow) {
    return BukkitResolve.id(arrow.getBasePotionData());
  }

  static void setBasePotion(Arrow arrow, Id potion) {
    PotionData data = BukkitResolve.potionData(potion);
    arrow.setBasePotionData(data == null ? new PotionData(PotionType.UNCRAFTABLE) : data);
  }

  static boolean isInWorld(Projectile projectile) {
    return projectile.isValid();
  }

  static ItemStack weapon(AbstractArrow arrow) {
    return null;
  }

  static void setWeapon(AbstractArrow arrow, ItemStack weapon) {
  }
}
