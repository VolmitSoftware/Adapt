package art.arcane.adapt.api.insight;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A nearby entity Insight tracks without focusing it. */
@ApiStatus.Experimental
public final class InsightMark {
  private final int entityId;
  private final InsightKind kind;
  private final double health;

  private InsightMark(int entityId, InsightKind kind, double health) {
    this.entityId = entityId;
    this.kind = Objects.requireNonNull(kind, "kind");
    if (!(health >= 0D && health <= 1D)) {
      throw new IllegalArgumentException("health must be a fraction from 0 to 1, was " + health);
    }
    this.health = health;
  }

  /**
   * A mark for one entity.
   *
   * @param entityId the entity's network id
   * @param health   its health as a fraction of its maximum, 0 to 1
   */
  public static InsightMark of(int entityId, InsightKind kind, double health) {
    return new InsightMark(entityId, kind, health);
  }

  /** The entity's network id. */
  public int entityId() {
    return entityId;
  }

  /** What the entity is. */
  public InsightKind kind() {
    return kind;
  }

  /** Its health as a fraction of its maximum, 0 to 1. */
  public double health() {
    return health;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InsightMark that && entityId == that.entityId && kind == that.kind
        && Double.compare(health, that.health) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(entityId, kind, health);
  }

  @Override
  public String toString() {
    return "InsightMark[" + entityId + " " + kind + " " + health + "]";
  }
}
