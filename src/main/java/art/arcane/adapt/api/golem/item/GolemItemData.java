package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.NodeType;

import java.util.UUID;

final class GolemItemData {
  static final GolemItemData EMPTY = new GolemItemData();

  private GolemCoreType role;
  private NodeType type;
  private GolemRuneType aspect;
  private int tier;
  private UUID golem;
  private UUID owner;
  private String state;

  private GolemItemData() {
  }

  static GolemItemData role(GolemCoreType role) {
    GolemItemData data = new GolemItemData();
    data.role = role;
    return data;
  }

  static GolemItemData node(NodeType type) {
    GolemItemData data = new GolemItemData();
    data.type = type;
    return data;
  }

  static GolemItemData rune(GolemRune rune) {
    GolemItemData data = new GolemItemData();
    data.aspect = rune.aspect();
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
    return role;
  }

  NodeType type() {
    return type;
  }

  GolemRuneType aspect() {
    return aspect;
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
}
