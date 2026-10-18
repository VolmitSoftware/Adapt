package art.arcane.adapt.api.golem;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

interface GolemPlatform {
  boolean isOnline(UUID owner);

  Collection<UUID> onlineOwners();

  Player player(UUID owner);

  Optional<GolemRoster> roster(UUID owner);

  void rosterChanged(UUID owner);

  boolean owns(Entity entity);

  boolean runEntity(Entity entity, Runnable task, int delayTicks);

  boolean isChunkLoaded(UUID world, long chunkKey);

  void sweepChunk(UUID world, long chunkKey, Consumer<Mob> boundGolem);

  AttributeInstance scaleOf(LivingEntity entity);

  long nowMillis();
}
