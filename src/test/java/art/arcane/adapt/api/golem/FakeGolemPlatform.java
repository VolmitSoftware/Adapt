package art.arcane.adapt.api.golem;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class FakeGolemPlatform implements GolemPlatform {
  final Set<UUID> online = new HashSet<>();
  final Map<UUID, Player> players = new HashMap<>();
  final Map<UUID, GolemRoster> rosters = new HashMap<>();
  final Map<UUID, Integer> rosterChanges = new HashMap<>();
  final Set<String> loadedChunks = new HashSet<>();
  final List<Delayed> delayed = new ArrayList<>();
  final Map<UUID, List<AttributeModifier>> scaleModifiers = new HashMap<>();
  final Map<String, List<Mob>> chunkMobs = new HashMap<>();
  final List<String> sweeps = new ArrayList<>();
  final Map<String, FakeAttribute> stats = new HashMap<>();
  final Map<UUID, Integer> patina = new HashMap<>();
  boolean ownsEntities = true;
  long now = 1_000_000L;

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

  FakeAttribute stat(Entity entity, GolemStat stat, double base) {
    return stats.computeIfAbsent(entity.getUniqueId() + ":" + stat.id(), ignored -> new FakeAttribute(base));
  }

  FakeAttribute stat(Entity entity, GolemStat stat) {
    return stats.get(entity.getUniqueId() + ":" + stat.id());
  }

  List<AttributeModifier> scale(Entity entity) {
    return scaleModifiers.computeIfAbsent(entity.getUniqueId(), ignored -> new ArrayList<>());
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

  void loadChunk(UUID world, long chunkKey, Mob... mobs) {
    loadedChunks.add(world + ":" + chunkKey);
    chunkMobs.computeIfAbsent(world + ":" + chunkKey, ignored -> new ArrayList<>()).addAll(List.of(mobs));
  }

  @Override
  public Collection<UUID> onlineOwners() {
    return List.copyOf(online);
  }

  @Override
  public Player player(UUID owner) {
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
  public void rosterChanged(UUID owner) {
    rosterChanges.merge(owner, 1, Integer::sum);
  }

  @Override
  public boolean owns(Entity entity) {
    return ownsEntities;
  }

  @Override
  public boolean runEntity(Entity entity, Runnable task, int delayTicks) {
    if (delayTicks <= 0 && ownsEntities) {
      task.run();
      return true;
    }
    delayed.add(new Delayed(task, delayTicks, null));
    return true;
  }

  @Override
  public boolean runEntity(Entity entity, Runnable task, Runnable retired) {
    if (ownsEntities) {
      task.run();
      return true;
    }
    delayed.add(new Delayed(task, 0, retired));
    return true;
  }

  @Override
  public boolean isChunkLoaded(UUID world, long chunkKey) {
    return loadedChunks.contains(world + ":" + chunkKey);
  }

  @Override
  public void sweepChunk(UUID world, long chunkKey, Consumer<Mob> boundGolem) {
    String key = world + ":" + chunkKey;
    sweeps.add(key);
    if (!loadedChunks.contains(key)) {
      return;
    }
    for (Mob mob : chunkMobs.getOrDefault(key, List.of())) {
      if (GolemRegistry.adoptable(mob)) {
        boundGolem.accept(mob);
      }
    }
  }

  @Override
  public AttributeInstance scaleOf(LivingEntity entity) {
    List<AttributeModifier> modifiers = scale(entity);
    AttributeInstance instance = mock(AttributeInstance.class);
    when(instance.getModifiers()).thenAnswer(invocation -> List.copyOf(modifiers));
    doAnswer(invocation -> modifiers.add(invocation.getArgument(0))).when(instance).addModifier(any(AttributeModifier.class));
    doAnswer(invocation -> modifiers.remove(invocation.<AttributeModifier>getArgument(0))).when(instance).removeModifier(any(AttributeModifier.class));
    return instance;
  }

  @Override
  public AttributeInstance attribute(LivingEntity entity, GolemStat stat) {
    FakeAttribute attribute = stat(entity, stat);
    return attribute == null ? null : attribute.instance;
  }

  @Override
  public int patinaStage(Mob golem) {
    return patina.getOrDefault(golem.getUniqueId(), 0);
  }

  @Override
  public long nowMillis() {
    return now;
  }

  record Delayed(Runnable task, int delayTicks, Runnable retired) {
  }
}
