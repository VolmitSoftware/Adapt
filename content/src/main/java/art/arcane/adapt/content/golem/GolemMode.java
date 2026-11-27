package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;

import java.util.List;
import java.util.Optional;

public enum GolemMode {
  WORK("work"),
  FOLLOW("follow"),
  STAY("stay"),
  HOME("home");

  private static final List<GolemMode> COMPANION_MODES = List.of(FOLLOW, STAY, HOME);
  private static final List<GolemMode> ALL_MODES = List.of(values());

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

  public static List<GolemMode> selectableFor(List<GolemCoreType> cores) {
    for (GolemCoreType core : cores) {
      if (core != GolemCoreType.BLANK) {
        return ALL_MODES;
      }
    }
    return COMPANION_MODES;
  }

  public static GolemMode keptOrDefault(List<GolemCoreType> cores, GolemMode current) {
    if (selectableFor(cores).contains(current)) {
      return current;
    }
    return defaultFor(cores.isEmpty() ? GolemCoreType.BLANK : cores.getFirst());
  }

  public String id() {
    return id;
  }

  public Text displayName() {
    return switch (this) {
      case WORK -> Text.key("adapt.golemancy.mode.work");
      case FOLLOW -> Text.key("adapt.golemancy.mode.follow");
      case STAY -> Text.key("adapt.golemancy.mode.stay");
      case HOME -> Text.key("adapt.golemancy.mode.home");
    };
  }
}
