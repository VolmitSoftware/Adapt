package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

interface GolemPlatform {
  boolean isOnline(UUID owner);

  Collection<UUID> onlineOwners();

  PlayerRef player(UUID owner);

  Optional<GolemRoster> roster(UUID owner);

  boolean ownerReady(UUID owner);

  boolean keepsBoneGolems(UUID owner);

  void rosterChanged(UUID owner);

  CompletableFuture<Void> checkpoint(UUID owner);

  boolean owns(EntityRef entity);

  boolean owns(Loc location);

  boolean runEntity(EntityRef entity, Runnable task, int delayTicks);

  boolean runEntity(EntityRef entity, Runnable task, Runnable retired);

  boolean runAt(Loc location, Runnable task);

  boolean isChunkLoaded(UUID world, long chunkKey);

  void sweepChunk(UUID world, long chunkKey, Consumer<MobRef> boundGolem);

  GolemAttribute scaleOf(LivingRef entity);

  GolemAttribute attribute(LivingRef entity, GolemStat stat);

  int patinaStage(MobRef golem);

  boolean waxed(MobRef golem);

  double heal(LivingRef entity, double amount);

  void dropOwnerBound(Loc at, ItemRef item, UUID owner);

  void returnToOwner(UUID owner, Loc at, ItemRef item);

  void upkeep(UUID owner, Set<UUID> golems);

  void crumble(MobRef body);

  MobRef spawn(Loc at, Id type, Consumer<MobRef> prepare);

  long nowMillis();

  List<NodeIndex.Entry> nodesNear(UUID world, double x, double y, double z, double range);

  Loc targetLocation(GolemTarget target, WorldRef world);

  Optional<EntityRef> targetEntity(GolemTarget target, WorldRef world);

  Optional<EntityRef> ownedEntity(UUID entity, WorldRef world);

  void signalFull(MobRef golem);

  void visitLid(BlockRef container, int holdTicks);

  Adaptation<?> bindingPolicy();

  String encodeItem(ItemRef item);

  ItemRef decodeItem(String encoded);

  GolemNameMode nameMode();
}
