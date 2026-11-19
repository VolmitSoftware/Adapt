package art.arcane.adapt.bukkit.platform.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.LivingEntity;

final class BukkitEntityVersion {
  private BukkitEntityVersion() {
  }

  @SuppressWarnings("deprecation")
  static boolean undead(Entity entity) {
    return entity instanceof LivingEntity living && living.getCategory() == EntityCategory.UNDEAD;
  }
}
