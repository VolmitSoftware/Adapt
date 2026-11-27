package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One structure sweep per player over Sixth Sense's generated-structure search. */
public final class StructureSense {
  private final GeneratedStructureSearch search = new GeneratedStructureSearch();
  private final Map<UUID, GeneratedStructureSearch.Sweep> sweeps = new ConcurrentHashMap<>();

  public void tick() {
    search.tick();
  }

  public boolean hasWork() {
    return search.hasWork();
  }

  public boolean hasSweeps() {
    return !sweeps.isEmpty();
  }

  public Set<UUID> sweepingPlayers() {
    return Set.copyOf(sweeps.keySet());
  }

  /** Whether the player's sweep is still running in {@code worldId}; a sweep left behind in another world is cancelled. */
  public boolean running(UUID playerId, UUID worldId) {
    GeneratedStructureSearch.Sweep sweep = sweeps.get(playerId);
    if (sweep != null && !sweep.accepts(worldId)) {
      cancel(playerId);
      return false;
    }
    return sweep != null && sweep.running();
  }

  /** Starts a new sweep for the player; each structure found reaches {@code found} on the player's thread. */
  public void begin(PlayerRef player, Loc origin, double range, List<Id> types, Consumer<Sensed> found) {
    UUID playerId = player.uuid();
    cancel(playerId);
    WorldRef world = origin.world();
    GeneratedStructureSearch.Sweep sweep = search.begin(new GeneratedStructureSearch.Request(
        UUID.randomUUID(), world, world.uid(), origin.x(), origin.z(), range, types,
        (GeneratedStructureSearch.Candidate candidate) -> AdaptApi.scheduler().runEntity(player, () -> {
          GeneratedStructureSearch.Sweep current = sweeps.get(playerId);
          if (current != null && current.owns(candidate)) {
            found.accept(new Sensed(candidate.worldId(), candidate.x(), candidate.z(), candidate.name(),
                candidate.symbol(), candidate.bounds()));
          }
        })));
    sweeps.put(playerId, sweep);
  }

  public void cancel(UUID playerId) {
    GeneratedStructureSearch.Sweep sweep = sweeps.remove(playerId);
    if (sweep != null) {
      sweep.cancel();
    }
  }

  public void close() {
    search.close();
    for (GeneratedStructureSearch.Sweep sweep : sweeps.values()) {
      sweep.cancel();
    }
    sweeps.clear();
  }

  public record Sensed(UUID worldId, double x, double z, String name, String symbol, Box bounds) {
  }
}
