package art.arcane.adapt.bukkit.platform.entity;

import org.bukkit.Tag;
import org.bukkit.entity.Entity;

final class BukkitEntityVersion {
  private BukkitEntityVersion() {
  }

  static boolean undead(Entity entity) {
    return Tag.ENTITY_TYPES_UNDEAD.isTagged(entity.getType());
  }
}
