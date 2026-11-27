package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** An orphan as {@link Orphans} lists it; {@code saveKey} is the key the line is stored under in the player's save. */
public record EngineOrphanedLine(String saveKey, Key line, Optional<Id> adaptation, String namespace, double xp,
                                 long knowledge, Map<Id, Integer> adaptations) implements OrphanedLine {
  public EngineOrphanedLine {
    Objects.requireNonNull(saveKey, "saveKey");
    Objects.requireNonNull(line, "line");
    Objects.requireNonNull(adaptation, "adaptation");
    Objects.requireNonNull(namespace, "namespace");
    adaptations = Map.copyOf(adaptations);
  }
}
