package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.entity.EntityRef;

/** Markers other plugins attach to entities (Bukkit metadata such as NPC, shopitem, hologram). */
public interface EntityMarkerPort {
  EntityMarkerPort NONE = new EntityMarkerPort() {
  };

  default boolean marked(EntityRef entity, String marker) {
    return false;
  }
}
