package art.arcane.adapt.api.world;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class FencedSnapshotSelector {
  private FencedSnapshotSelector() {
  }

  static Selection select(UUID playerId, UUID predecessorToken, long predecessorEpoch,
                          long committedSequence, String committedJson,
                          List<FencedPlayerSnapshot> candidates) {
    Objects.requireNonNull(playerId);
    FencedPlayerSnapshot selected = null;
    for (FencedPlayerSnapshot candidate : List.copyOf(Objects.requireNonNull(candidates))) {
      if (candidate == null || predecessorToken == null
          || !playerId.equals(candidate.playerId())
          || !candidate.belongsTo(predecessorToken, predecessorEpoch)) {
        continue;
      }
      if (candidate.sequence() < committedSequence) {
        continue;
      }
      if (candidate.sequence() == committedSequence) {
        if (committedJson != null && !committedJson.equals(candidate.json())) {
          throw new IllegalStateException("Conflicting player snapshots reuse committed sequence "
              + committedSequence);
        }
        continue;
      }
      if (selected == null || candidate.sequence() > selected.sequence()) {
        selected = candidate;
        continue;
      }
      if (candidate.sequence() == selected.sequence() && !candidate.json().equals(selected.json())) {
        throw new IllegalStateException("Conflicting player snapshots reuse pending sequence "
            + candidate.sequence());
      }
    }
    return selected == null
        ? new Selection(committedJson, null)
        : new Selection(selected.json(), selected);
  }

  record Selection(String json, FencedPlayerSnapshot source) {
  }
}
