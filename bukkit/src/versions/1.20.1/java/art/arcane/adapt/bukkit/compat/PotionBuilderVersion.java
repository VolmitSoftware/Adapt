package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

/** Potion meta on Paper 1.20.1 keeps its base potion as potion data, which carries the long or strong variant. */
final class PotionBuilderVersion {
  private PotionBuilderVersion() {
  }

  static Id basePotion(PotionMeta meta) {
    return BukkitResolve.id(meta.getBasePotionData());
  }

  static void setBasePotion(PotionMeta meta, Id potion) {
    PotionData data = BukkitResolve.potionData(potion);
    if (data != null) {
      meta.setBasePotionData(data);
    }
  }
}
