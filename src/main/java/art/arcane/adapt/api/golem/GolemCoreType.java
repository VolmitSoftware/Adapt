package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Locale;
import java.util.Optional;

public enum GolemCoreType {
  BLANK,
  GUARDIAN,
  GATHERER,
  HARVESTER,
  LUMBER,
  BREAKER,
  RANCHER,
  SORTER,
  ANGLER;

  private final String id = name().toLowerCase(Locale.ROOT);

  public static Optional<GolemCoreType> fromId(String id) {
    for (GolemCoreType type : values()) {
      if (type.id.equals(id)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public String id() {
    return id;
  }

  public TextKey displayName() {
    return switch (this) {
      case BLANK -> GolemancyMessages.ROLE_COMPANION;
      case GUARDIAN -> GolemancyMessages.ROLE_GUARDIAN;
      case GATHERER -> GolemancyMessages.ROLE_GATHERER;
      case HARVESTER -> GolemancyMessages.ROLE_HARVESTER;
      case LUMBER -> GolemancyMessages.ROLE_LUMBER;
      case BREAKER -> GolemancyMessages.ROLE_BREAKER;
      case RANCHER -> GolemancyMessages.ROLE_RANCHER;
      case SORTER -> GolemancyMessages.ROLE_SORTER;
      case ANGLER -> GolemancyMessages.ROLE_ANGLER;
    };
  }
}
