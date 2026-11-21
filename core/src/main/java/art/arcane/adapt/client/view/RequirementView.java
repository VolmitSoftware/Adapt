package art.arcane.adapt.client.view;

import java.util.Objects;

public record RequirementView(String adaptationId, int level) {
  public RequirementView {
    Objects.requireNonNull(adaptationId, "adaptationId");
  }
}
