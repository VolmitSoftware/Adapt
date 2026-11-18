package art.arcane.adapt.api.golem.item;

import java.util.Objects;
import java.util.UUID;

public record PackedGolem(UUID golemId, UUID owner, String state) {
  public PackedGolem {
    Objects.requireNonNull(golemId);
    Objects.requireNonNull(owner);
    Objects.requireNonNull(state);
  }
}
