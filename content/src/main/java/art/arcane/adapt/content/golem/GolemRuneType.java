package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;

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

  public Text displayName() {
    return switch (this) {
      case SWIFTNESS -> Text.key("adapt.golemancy.aspect.swiftness");
      case MIGHT -> Text.key("adapt.golemancy.aspect.might");
      case EMBER -> Text.key("adapt.golemancy.aspect.ember");
      case TIDE -> Text.key("adapt.golemancy.aspect.tide");
      case ORDER -> Text.key("adapt.golemancy.aspect.order");
      case ENTROPY -> Text.key("adapt.golemancy.aspect.entropy");
      case BULWARK -> Text.key("adapt.golemancy.aspect.bulwark");
    };
  }
}
