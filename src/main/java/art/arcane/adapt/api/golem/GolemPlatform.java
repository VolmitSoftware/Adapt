package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.NodeIndex;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

interface GolemPlatform {
  boolean isOnline(UUID owner);

  Collection<UUID> onlineOwners();

  Player player(UUID owner);

  Optional<GolemRoster> roster(UUID owner);

  boolean ownerReady(UUID owner);

  void rosterChanged(UUID owner);

  boolean owns(Entity entity);

  boolean owns(Location location);

  boolean runEntity(Entity entity, Runnable task, int delayTicks);

  boolean runEntity(Entity entity, Runnable task, Runnable retired);

  boolean isChunkLoaded(UUID world, long chunkKey);

  void sweepChunk(UUID world, long chunkKey, Consumer<Mob> boundGolem);

  AttributeInstance scaleOf(LivingEntity entity);

  AttributeInstance attribute(LivingEntity entity, GolemStat stat);

  int patinaStage(Mob golem);

  long nowMillis();

  List<NodeIndex.Entry> nodesNear(UUID world, double x, double y, double z, double range);

  Location targetLocation(GolemTarget target, World world);

  void signalFull(Mob golem);

  Adaptation<?> bindingPolicy();

  String encodeItem(ItemStack item);

  ItemStack decodeItem(String encoded);
}
