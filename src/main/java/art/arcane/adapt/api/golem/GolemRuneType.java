package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Optional;

public enum GolemRuneType {
  SWIFTNESS("swiftness"),
  MIGHT("might"),
  EMBER("ember"),
  TIDE("tide"),
  ORDER("order"),
  ENTROPY("entropy"),
  BULWARK("bulwark");

  public static final int MIN_TIER = 1;
  public static final int MAX_TIER = 3;

  private final String id;

  GolemRuneType(String id) {
    this.id = id;
  }

  public static Optional<GolemRuneType> fromId(String id) {
    for (GolemRuneType type : values()) {
      if (type.id.equals(id)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public static boolean isTier(int tier) {
    return tier >= MIN_TIER && tier <= MAX_TIER;
  }

  public String id() {
    return id;
  }

  public TextKey displayName() {
    return switch (this) {
      case SWIFTNESS -> GolemancyMessages.ASPECT_SWIFTNESS;
      case MIGHT -> GolemancyMessages.ASPECT_MIGHT;
      case EMBER -> GolemancyMessages.ASPECT_EMBER;
      case TIDE -> GolemancyMessages.ASPECT_TIDE;
      case ORDER -> GolemancyMessages.ASPECT_ORDER;
      case ENTROPY -> GolemancyMessages.ASPECT_ENTROPY;
      case BULWARK -> GolemancyMessages.ASPECT_BULWARK;
    };
  }
}
