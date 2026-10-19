package art.arcane.adapt.api.golem.node;

import java.util.Optional;

public enum RedstoneMode {
  IGNORE("ignore"),
  POWERED("powered"),
  UNPOWERED("unpowered");

  private final String id;

  RedstoneMode(String id) {
    this.id = id;
  }

  public static Optional<RedstoneMode> fromId(String id) {
    for (RedstoneMode mode : values()) {
      if (mode.id.equals(id)) {
        return Optional.of(mode);
      }
    }
    return Optional.empty();
  }

  public String id() {
    return id;
  }
}
