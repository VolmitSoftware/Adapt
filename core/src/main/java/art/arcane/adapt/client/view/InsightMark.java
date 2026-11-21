package art.arcane.adapt.client.view;

import java.util.Objects;

/** A nearby entity Insight tracks without focusing it: only its kind and health fraction (0..1). */
public record InsightMark(int entityId, InsightKind kind, double health) {
  public InsightMark {
    Objects.requireNonNull(kind, "kind");
  }
}
