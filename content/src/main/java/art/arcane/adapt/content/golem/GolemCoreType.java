package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;

import java.util.Optional;

public enum GolemCoreType {
  BLANK("blank"),
  GUARDIAN("guardian"),
  GATHERER("gatherer"),
  HARVESTER("harvester"),
  LUMBER("lumber"),
  BREAKER("breaker"),
  RANCHER("rancher"),
  SORTER("sorter"),
  ANGLER("angler");

  private static final GolemCoreType[] VALUES = values();

  private final String id;

  GolemCoreType(String id) {
    this.id = id;
  }

  public static Optional<GolemCoreType> fromId(String id) {
    for (GolemCoreType type : VALUES) {
      if (type.id.equals(id)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public String id() {
    return id;
  }

  public Text displayName() {
    return switch (this) {
      case BLANK -> Text.key("adapt.golemancy.role.blank");
      case GUARDIAN -> Text.key("adapt.golemancy.role.guardian");
      case GATHERER -> Text.key("adapt.golemancy.role.gatherer");
      case HARVESTER -> Text.key("adapt.golemancy.role.harvester");
      case LUMBER -> Text.key("adapt.golemancy.role.lumber");
      case BREAKER -> Text.key("adapt.golemancy.role.breaker");
      case RANCHER -> Text.key("adapt.golemancy.role.rancher");
      case SORTER -> Text.key("adapt.golemancy.role.sorter");
      case ANGLER -> Text.key("adapt.golemancy.role.angler");
    };
  }
}
