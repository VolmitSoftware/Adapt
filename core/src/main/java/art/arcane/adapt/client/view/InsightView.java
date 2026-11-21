package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * The entity the viewer's Insight focuses. {@code entityId} is the network id, so the client follows the entity it
 * already tracks. {@code name} is the custom or player name, empty for the type's own name; {@code ownerName} is empty
 * when the owner is unknown or offline. {@code stableHand} marks a Taming Stable Hand bias.
 */
public record InsightView(int entityId, String typeId, String name, InsightKind kind, double health, double maxHealth,
                          double absorption, double armor, InsightBond bond, String ownerName, boolean stableHand,
                          List<InsightStat> stats, List<InsightEffect> effects) {
  public InsightView {
    Objects.requireNonNull(typeId, "typeId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(bond, "bond");
    Objects.requireNonNull(ownerName, "ownerName");
    stats = List.copyOf(stats);
    effects = List.copyOf(effects);
  }

  /** True when only health, absorption or effect durations differ, so a vitals frame is enough. */
  public boolean sameShape(InsightView other) {
    if (other == null || entityId != other.entityId || effects.size() != other.effects.size()) {
      return false;
    }
    for (int index = 0; index < effects.size(); index++) {
      InsightEffect mine = effects.get(index);
      InsightEffect theirs = other.effects.get(index);
      if (!mine.effectId().equals(theirs.effectId()) || mine.amplifier() != theirs.amplifier()
          || mine.harmful() != theirs.harmful()) {
        return false;
      }
    }
    return typeId.equals(other.typeId) && name.equals(other.name) && kind == other.kind
        && Double.compare(maxHealth, other.maxHealth) == 0 && Double.compare(armor, other.armor) == 0
        && bond == other.bond && ownerName.equals(other.ownerName) && stableHand == other.stableHand
        && stats.equals(other.stats);
  }
}
