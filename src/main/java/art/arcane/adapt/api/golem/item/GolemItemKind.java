package art.arcane.adapt.api.golem.item;

import java.util.Optional;

public enum GolemItemKind {
  CROOK("crook", 1),
  BLANK_CORE("blank_core", 16),
  CORE("core", 16),
  BLANK_NODE("blank_node", 64),
  NODE("node", 16),
  BLANK_RUNE("blank_rune", 16),
  RUNE("rune", 16),
  DORMANT_GOLEM("dormant_golem", 1),
  SHATTERED_CORE("shattered_core", 1),
  MASTERWORK_HEART("masterwork_heart", 1);

  private final String id;
  private final int maxStackSize;

  GolemItemKind(String id, int maxStackSize) {
    this.id = id;
    this.maxStackSize = maxStackSize;
  }

  public static Optional<GolemItemKind> fromId(String id) {
    for (GolemItemKind kind : values()) {
      if (kind.id.equals(id)) {
        return Optional.of(kind);
      }
    }
    return Optional.empty();
  }

  public String id() {
    return id;
  }

  public int maxStackSize() {
    return maxStackSize;
  }
}
