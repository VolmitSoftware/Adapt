package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;

import java.util.Optional;

public enum NodeType {
  GUARD("guard"),
  COLLECTION("collection"),
  DEPOSIT("deposit"),
  HARVEST("harvest"),
  LUMBER("lumber"),
  BREAKER("breaker"),
  PASTURE("pasture"),
  INPUT("input"),
  FILTER("filter"),
  FISHING("fishing");

  private final String id;

  NodeType(String id) {
    this.id = id;
  }

  public static Optional<NodeType> fromId(String id) {
    for (NodeType type : values()) {
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
      case GUARD -> Text.key("adapt.golemancy.node.guard");
      case COLLECTION -> Text.key("adapt.golemancy.node.collection");
      case DEPOSIT -> Text.key("adapt.golemancy.node.deposit");
      case HARVEST -> Text.key("adapt.golemancy.node.harvest");
      case LUMBER -> Text.key("adapt.golemancy.node.lumber");
      case BREAKER -> Text.key("adapt.golemancy.node.breaker");
      case PASTURE -> Text.key("adapt.golemancy.node.pasture");
      case INPUT -> Text.key("adapt.golemancy.node.input");
      case FILTER -> Text.key("adapt.golemancy.node.filter");
      case FISHING -> Text.key("adapt.golemancy.node.fishing");
    };
  }
}
