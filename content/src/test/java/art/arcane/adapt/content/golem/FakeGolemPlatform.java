package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class FakeGolemPlatform implements GolemPlatform {
  final Set<UUID> online = new HashSet<>();
  final Map<UUID, PlayerRef> players = new HashMap<>();
  final Map<UUID, GolemRoster> rosters = new HashMap<>();
  final Map<UUID, Integer> rosterChanges = new HashMap<>();
  final Set<String> loadedChunks = new HashSet<>();
  final List<Delayed> delayed = new ArrayList<>();
  final Map<UUID, List<AttributeMod>> scaleModifiers = new HashMap<>();
  final Map<String, List<MobRef>> chunkMobs = new HashMap<>();
  final List<String> sweeps = new ArrayList<>();
  final Map<String, FakeAttribute> stats = new HashMap<>();
  final Map<UUID, Integer> patina = new HashMap<>();
  final Map<NodeKey, NodeRecord> nodes = new HashMap<>();
  final Map<String, ItemRef> encoded = new HashMap<>();
  final Map<UUID, EntityRef> entities = new HashMap<>();
  final Set<EntityRef> refusing = new HashSet<>();
  final Set<EntityRef> deferring = new HashSet<>();
  final List<RegionTask> regionTasks = new ArrayList<>();
  final Set<UUID> waxedGolems = new HashSet<>();
  final List<OwnerBoundDrop> ownerBoundDrops = new ArrayList<>();
  final List<Returned> returned = new ArrayList<>();
  final List<Double> heals = new ArrayList<>();
  final List<MobRef> spawns = new ArrayList<>();
  final List<Loc> spawnedAt = new ArrayList<>();
  final List<Id> spawnedTypes = new ArrayList<>();
  final Map<UUID, Set<UUID>> upkeep = new HashMap<>();
  final List<MobRef> crumbled = new ArrayList<>();
  final List<String> lidVisits = new ArrayList<>();
  Consumer<MobRef> spawnListener = spawned -> {
  };
  Consumer<UUID> rosterListener = owner -> {
  };
  Runnable dropListener = () -> {
  };
  boolean ownsEntities = true;
  boolean refuseRetiring;
  boolean refuseRegions;
  boolean ownsLocations = true;
  boolean keepsBoneGolems = true;
  int fullSignals;
  Adaptation<?> bindingPolicy;
  GolemNameMode nameMode = GolemNameMode.VANILLA;
  long now = 1_000_000L;
  CompletableFuture<Void> checkpoint = CompletableFuture.completedFuture(null);

  void join(UUID owner, GolemRoster roster) {
    online.add(owner);
    if (roster != null) {
      rosters.put(owner, roster);
    }
  }

  void quit(UUID owner) {
    online.remove(owner);
    rosters.remove(owner);
  }

  FakeAttribute stat(EntityRef entity, GolemStat stat, double base) {
    return stats.computeIfAbsent(entity.uuid() + ":" + stat.id(), ignored -> new FakeAttribute(base));
  }

  FakeAttribute stat(EntityRef entity, GolemStat stat) {
    return stats.get(entity.uuid() + ":" + stat.id());
  }

  List<AttributeMod> scale(EntityRef entity) {
    return scaleModifiers.computeIfAbsent(entity.uuid(), ignored -> new ArrayList<>());
  }

  int changes(UUID owner) {
    return rosterChanges.getOrDefault(owner, 0);
  }

  long retirable() {
    return delayed.stream().filter(task -> task.retired() != null).count();
  }

  void retireDelayed() {
    List<Delayed> retiring = delayed.stream().filter(task -> task.retired() != null).toList();
    delayed.removeAll(retiring);
    for (Delayed task : retiring) {
      task.retired().run();
    }
  }

  void runRegionTasks() {
    List<RegionTask> due = List.copyOf(regionTasks);
    regionTasks.clear();
    for (RegionTask task : due) {
      task.task().run();
    }
  }

  void runDelayed() {
    List<Delayed> due = List.copyOf(delayed);
    delayed.clear();
    for (Delayed task : due) {
      task.task().run();
    }
  }

  @Override
  public boolean isOnline(UUID owner) {
    return online.contains(owner);
  }

  void loadChunk(UUID world, long chunkKey, MobRef... mobs) {
    loadedChunks.add(world + ":" + chunkKey);
    chunkMobs.computeIfAbsent(world + ":" + chunkKey, ignored -> new ArrayList<>()).addAll(List.of(mobs));
  }

  @Override
  public Collection<UUID> onlineOwners() {
    return List.copyOf(online);
  }

  @Override
  public PlayerRef player(UUID owner) {
    return players.get(owner);
  }

  @Override
  public Optional<GolemRoster> roster(UUID owner) {
    return online.contains(owner) ? Optional.ofNullable(rosters.get(owner)) : Optional.empty();
  }

  @Override
  public boolean ownerReady(UUID owner) {
    return online.contains(owner) && rosters.containsKey(owner);
  }

  @Override
  public boolean keepsBoneGolems(UUID owner) {
    return keepsBoneGolems;
  }

  @Override
  public void rosterChanged(UUID owner) {
    rosterListener.accept(owner);
    rosterChanges.merge(owner, 1, Integer::sum);
  }

  @Override
  public CompletableFuture<Void> checkpoint(UUID owner) {
    return checkpoint;
  }

  @Override
  public boolean owns(EntityRef entity) {
    return ownsEntities;
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, int delayTicks) {
    if (refusing.contains(entity)) {
      return false;
    }
    if (delayTicks <= 0 && ownsEntities && !deferring.contains(entity)) {
      task.run();
      return true;
    }
    delayed.add(new Delayed(task, delayTicks, null));
    return true;
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, Runnable retired) {
    if (refuseRetiring || refusing.contains(entity)) {
      retired.run();
      return false;
    }
    if (ownsEntities && !deferring.contains(entity)) {
      task.run();
      return true;
    }
    delayed.add(new Delayed(task, 0, retired));
    return true;
  }

  @Override
  public boolean runAt(Loc location, Runnable task) {
    if (refuseRegions) {
      return false;
    }
    regionTasks.add(new RegionTask(location, task));
    return true;
  }

  @Override
  public boolean isChunkLoaded(UUID world, long chunkKey) {
    return loadedChunks.contains(world + ":" + chunkKey);
  }

  @Override
  public void sweepChunk(UUID world, long chunkKey, Consumer<MobRef> boundGolem) {
    String key = world + ":" + chunkKey;
    sweeps.add(key);
    if (!loadedChunks.contains(key)) {
      return;
    }
    for (MobRef mob : chunkMobs.getOrDefault(key, List.of())) {
      if (GolemRegistry.adoptable(mob)) {
        boundGolem.accept(mob);
      }
    }
  }

  @Override
  public GolemAttribute scaleOf(LivingRef entity) {
    return new FakeAttribute(1D, scale(entity));
  }

  @Override
  public GolemAttribute attribute(LivingRef entity, GolemStat stat) {
    return stat(entity, stat);
  }

  @Override
  public int patinaStage(MobRef golem) {
    return patina.getOrDefault(golem.uuid(), 0);
  }

  @Override
  public boolean waxed(MobRef golem) {
    return waxedGolems.contains(golem.uuid());
  }

  @Override
  public double heal(LivingRef entity, double amount) {
    heals.add(amount);
    return amount;
  }

  @Override
  public void dropOwnerBound(Loc at, ItemRef item, UUID owner) {
    dropListener.run();
    ownerBoundDrops.add(new OwnerBoundDrop(at, item, owner));
  }

  @Override
  public void returnToOwner(UUID owner, Loc at, ItemRef item) {
    returned.add(new Returned(owner, at, item));
  }

  @Override
  public MobRef spawn(Loc at, Id type, Consumer<MobRef> prepare) {
    if (spawns.isEmpty()) {
      throw new IllegalStateException("No body queued to spawn at " + at);
    }
    MobRef spawned = spawns.removeFirst();
    spawnedAt.add(at);
    spawnedTypes.add(type);
    spawnListener.accept(spawned);
    prepare.accept(spawned);
    return spawned;
  }

  @Override
  public void upkeep(UUID owner, Set<UUID> golems) {
    upkeep.put(owner, Set.copyOf(golems));
  }

  @Override
  public void crumble(MobRef body) {
    crumbled.add(body);
  }

  @Override
  public long nowMillis() {
    return now;
  }

  @Override
  public boolean owns(Loc location) {
    return ownsLocations;
  }

  @Override
  public List<NodeIndex.Entry> nodesNear(UUID world, double x, double y, double z, double range) {
    List<NodeIndex.Entry> near = new ArrayList<>();
    for (Map.Entry<NodeKey, NodeRecord> node : nodes.entrySet()) {
      NodeKey key = node.getKey();
      if (key.world().equals(world) && key.distanceSquared(x, y, z) <= range * range) {
        near.add(new NodeIndex.Entry(key, node.getValue()));
      }
    }
    return near;
  }

  @Override
  public Loc targetLocation(GolemTarget target, WorldRef world) {
    return target.location(world);
  }

  @Override
  public Optional<EntityRef> targetEntity(GolemTarget target, WorldRef world) {
    return target.kind() == GolemTarget.Kind.BLOCK ? Optional.empty() : ownedEntity(target.entity(), world);
  }

  @Override
  public Optional<EntityRef> ownedEntity(UUID entity, WorldRef world) {
    return Optional.ofNullable(entities.get(entity));
  }

  @Override
  public void signalFull(MobRef golem) {
    fullSignals++;
  }

  @Override
  public void visitLid(BlockRef container, int holdTicks) {
    lidVisits.add(container.x() + "," + container.y() + "," + container.z() + " " + holdTicks);
  }

  @Override
  public Adaptation<?> bindingPolicy() {
    return bindingPolicy;
  }

  @Override
  public String encodeItem(ItemRef item) {
    String key = "item-" + encoded.size();
    encoded.put(key, item.copy());
    return key;
  }

  @Override
  public GolemNameMode nameMode() {
    return nameMode;
  }

  @Override
  public ItemRef decodeItem(String key) {
    ItemRef item = encoded.get(key);
    if (item == null) {
      throw new IllegalArgumentException("Unknown encoded item " + key);
    }
    return item.copy();
  }

  record Delayed(Runnable task, int delayTicks, Runnable retired) {
  }

  record RegionTask(Loc location, Runnable task) {
  }

  record OwnerBoundDrop(Loc location, ItemRef item, UUID owner) {
  }

  record Returned(UUID owner, Loc location, ItemRef item) {
  }
}
