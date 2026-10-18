package art.arcane.adapt.api.golem.overseer;

import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;

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

  public TextKey displayName() {
    return switch (this) {
      case FOLLOW -> GolemancyMessages.SIGNAL_FOLLOW;
      case HOLD -> GolemancyMessages.SIGNAL_HOLD;
      case HOME -> GolemancyMessages.SIGNAL_HOME;
      case WORK -> GolemancyMessages.SIGNAL_WORK;
      case SIC -> GolemancyMessages.SIGNAL_SIC;
    };
  }
}
