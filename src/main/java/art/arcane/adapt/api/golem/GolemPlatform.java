package art.arcane.adapt.api.golem;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Optional;
import java.util.UUID;

interface GolemPlatform {
  boolean isOnline(UUID owner);

  Optional<GolemRoster> roster(UUID owner);

  void rosterChanged(UUID owner);

  boolean owns(Entity entity);

  boolean runEntity(Entity entity, Runnable task, int delayTicks);

  boolean isChunkLoaded(UUID world, long chunkKey);

  AttributeInstance scaleOf(LivingEntity entity);

  long nowMillis();
}
