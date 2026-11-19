package art.arcane.adapt.bukkit.platform.entity;

import org.bukkit.entity.MushroomCow;

final class BukkitAnimalVersion {
  private BukkitAnimalVersion() {
  }

  static boolean hasStewEffect(MushroomCow mooshroom) {
    return mooshroom.getStewEffectType() != null;
  }
}
