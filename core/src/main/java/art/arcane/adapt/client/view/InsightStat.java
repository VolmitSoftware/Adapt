package art.arcane.adapt.client.view;

import java.util.Objects;

public record InsightStat(InsightStatKind kind, double value) {
  public InsightStat {
    Objects.requireNonNull(kind, "kind");
  }
}
