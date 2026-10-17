package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;

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

  public TextKey displayName() {
    return switch (this) {
      case GUARD -> GolemancyMessages.NODE_GUARD;
      case COLLECTION -> GolemancyMessages.NODE_COLLECTION;
      case DEPOSIT -> GolemancyMessages.NODE_DEPOSIT;
      case HARVEST -> GolemancyMessages.NODE_HARVEST;
      case LUMBER -> GolemancyMessages.NODE_LUMBER;
      case BREAKER -> GolemancyMessages.NODE_BREAKER;
      case PASTURE -> GolemancyMessages.NODE_PASTURE;
      case INPUT -> GolemancyMessages.NODE_INPUT;
      case FILTER -> GolemancyMessages.NODE_FILTER;
      case FISHING -> GolemancyMessages.NODE_FISHING;
    };
  }
}
