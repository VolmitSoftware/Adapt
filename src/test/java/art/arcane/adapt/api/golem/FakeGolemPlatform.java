package art.arcane.adapt.api.golem;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class FakeGolemPlatform implements GolemPlatform {
  final Set<UUID> online = new HashSet<>();
  final Map<UUID, GolemRoster> rosters = new HashMap<>();
  final Map<UUID, Integer> rosterChanges = new HashMap<>();
  final Set<String> loadedChunks = new HashSet<>();
  final List<Delayed> delayed = new ArrayList<>();
  final Map<UUID, List<AttributeModifier>> scaleModifiers = new HashMap<>();
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

  List<AttributeModifier> scale(Entity entity) {
    return scaleModifiers.computeIfAbsent(entity.getUniqueId(), ignored -> new ArrayList<>());
  }

  int changes(UUID owner) {
    return rosterChanges.getOrDefault(owner, 0);
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

  @Override
  public Optional<GolemRoster> roster(UUID owner) {
    return online.contains(owner) ? Optional.ofNullable(rosters.get(owner)) : Optional.empty();
  }

  @Override
  public void rosterChanged(UUID owner) {
    rosterChanges.merge(owner, 1, Integer::sum);
  }

  @Override
  public boolean owns(Entity entity) {
    return true;
  }

  @Override
  public boolean runEntity(Entity entity, Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      task.run();
      return true;
    }
    delayed.add(new Delayed(task, delayTicks));
    return true;
  }

  @Override
  public boolean isChunkLoaded(UUID world, long chunkKey) {
    return loadedChunks.contains(world + ":" + chunkKey);
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
  public long nowMillis() {
    return now;
  }

  record Delayed(Runnable task, int delayTicks) {
  }
}
