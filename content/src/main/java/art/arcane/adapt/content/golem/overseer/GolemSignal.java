package art.arcane.adapt.content.golem.overseer;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemMode;

import java.util.Optional;

public enum GolemSignal {
  FOLLOW("follow", GolemMode.FOLLOW),
  HOLD("hold", GolemMode.STAY),
  HOME("home", GolemMode.HOME),
  WORK("work", GolemMode.WORK),
  SIC("sic", null);

  private final String id;
  private final GolemMode mode;

  GolemSignal(String id, GolemMode mode) {
    this.id = id;
    this.mode = mode;
  }

  public String id() {
    return id;
  }

  public Optional<GolemMode> mode() {
    return Optional.ofNullable(mode);
  }

  public Text displayName() {
    return switch (this) {
      case FOLLOW -> Text.key("adapt.golemancy.signal.follow");
      case HOLD -> Text.key("adapt.golemancy.signal.hold");
      case HOME -> Text.key("adapt.golemancy.signal.home");
      case WORK -> Text.key("adapt.golemancy.signal.work");
      case SIC -> Text.key("adapt.golemancy.signal.sic");
    };
  }
}
