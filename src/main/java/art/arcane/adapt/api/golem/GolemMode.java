package art.arcane.adapt.api.golem;

import java.util.Optional;

public enum GolemMode {
  WORK("work"),
  FOLLOW("follow"),
  STAY("stay"),
  HOME("home");

  private final String id;

  GolemMode(String id) {
    this.id = id;
  }

  public static Optional<GolemMode> fromId(String id) {
    for (GolemMode mode : values()) {
      if (mode.id.equals(id)) {
        return Optional.of(mode);
      }
    }
    return Optional.empty();
  }

  public static GolemMode defaultFor(GolemCoreType core) {
    return core == GolemCoreType.BLANK ? FOLLOW : WORK;
  }

  public String id() {
    return id;
  }
}
