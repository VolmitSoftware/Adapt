package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.NodeType;

import java.util.Objects;
import java.util.UUID;

final class GolemItemData {
  static final GolemItemData EMPTY = new GolemItemData();

  private String role;
  private String type;
  private String aspect;
  private int tier;
  private UUID golem;
  private UUID owner;
  private String state;

  private GolemItemData() {
  }

  static GolemItemData role(GolemCoreType role) {
    GolemItemData data = new GolemItemData();
    data.role = role.id();
    return data;
  }

  static GolemItemData node(NodeType type) {
    GolemItemData data = new GolemItemData();
    data.type = type.id();
    return data;
  }

  static GolemItemData rune(GolemRune rune) {
    GolemItemData data = new GolemItemData();
    data.aspect = rune.aspect().id();
    data.tier = rune.tier();
    return data;
  }

  static GolemItemData packed(PackedGolem golem) {
    GolemItemData data = new GolemItemData();
    data.golem = golem.golemId();
    data.owner = golem.owner();
    data.state = golem.state();
    return data;
  }

  GolemCoreType role() {
    return GolemCoreType.fromId(role).orElse(null);
  }

  NodeType type() {
    return NodeType.fromId(type).orElse(null);
  }

  GolemRuneType aspect() {
    return GolemRuneType.fromId(aspect).orElse(null);
  }

  int tier() {
    return tier;
  }

  UUID golem() {
    return golem;
  }

  UUID owner() {
    return owner;
  }

  String state() {
    return state;
  }

  boolean isEmpty() {
    return role == null && type == null && aspect == null && tier == 0 && golem == null && owner == null && state == null;
  }

  boolean sameVariant(GolemItemData other) {
    return Objects.equals(role, other.role) && Objects.equals(type, other.type) && Objects.equals(aspect, other.aspect) && tier == other.tier;
  }
}
