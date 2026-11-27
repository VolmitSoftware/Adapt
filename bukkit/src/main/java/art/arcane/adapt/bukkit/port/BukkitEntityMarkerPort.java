package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.engine.world.EntityMarkerPort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import org.bukkit.entity.Entity;

public final class BukkitEntityMarkerPort implements EntityMarkerPort {
  @Override
  public boolean marked(EntityRef entity, String marker) {
    Entity bukkit = entity == null ? null : BukkitRefs.entity(entity);
    return bukkit != null && bukkit.hasMetadata(marker);
  }
}
