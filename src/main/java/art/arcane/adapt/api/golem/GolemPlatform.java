package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.NodeIndex;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

  boolean runAt(Location location, Runnable task);

  boolean isChunkLoaded(UUID world, long chunkKey);

  void sweepChunk(UUID world, long chunkKey, Consumer<Mob> boundGolem);

  AttributeInstance scaleOf(LivingEntity entity);

  AttributeInstance attribute(LivingEntity entity, GolemStat stat);

  int patinaStage(Mob golem);

  boolean waxed(Mob golem);

  double heal(LivingEntity entity, double amount);

  void dropOwnerBound(Location at, ItemStack item, UUID owner);

  void upkeep(UUID owner, Set<UUID> golems);

  void crumble(Mob body);

  <T extends Mob> T spawn(Location at, Class<T> type, Consumer<? super T> prepare);

  long nowMillis();

  List<NodeIndex.Entry> nodesNear(UUID world, double x, double y, double z, double range);

  Location targetLocation(GolemTarget target, World world);

  Optional<Entity> targetEntity(GolemTarget target, World world);

  Optional<Entity> ownedEntity(UUID entity, World world);

  void signalFull(Mob golem);

  void visitLid(Block container, int holdTicks);

  Adaptation<?> bindingPolicy();

  String encodeItem(ItemStack item);

  ItemStack decodeItem(String encoded);

  GolemNameMode nameMode();
}
