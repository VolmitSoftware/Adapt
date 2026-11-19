package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

final class PotionBuilderVersion {
  private PotionBuilderVersion() {
  }

  static Id basePotion(PotionMeta meta) {
    PotionType type = meta.getBasePotionType();
    return type == null ? null : BukkitResolve.id(type);
  }

  static void setBasePotion(PotionMeta meta, Id potion) {
    meta.setBasePotionType(BukkitResolve.potion(potion));
  }
}
