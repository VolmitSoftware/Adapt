package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Lightning strikes. Mutable: cancelled (the bolt does not strike). */
@ApiStatus.AvailableSince("1.0.0")
public class LightningStrikeEvent extends CancellableEvent {
  private final WorldRef world;
  private final EntityRef lightning;
  private final Loc location;
  private final Cause cause;

  public LightningStrikeEvent(WorldRef world, EntityRef lightning, Loc location, Cause cause) {
    this.world = Objects.requireNonNull(world, "world");
    this.lightning = Objects.requireNonNull(lightning, "lightning");
    this.location = Objects.requireNonNull(location, "location");
    this.cause = cause == null ? Cause.UNKNOWN : cause;
  }

  /** The world it strikes in. */
  public WorldRef world() {
    return world;
  }

  /** The lightning bolt entity. */
  public EntityRef lightning() {
    return lightning;
  }

  /** Where the bolt strikes. */
  public Loc location() {
    return location;
  }

  /** What summoned the bolt; {@link Cause#UNKNOWN} when the platform did not say. */
  public Cause cause() {
    return cause;
  }

  /**
   * What summoned a lightning bolt.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Cause {
    WEATHER,
    TRIDENT,
    COMMAND,
    SPAWNER,
    TRAP,
    ENCHANTMENT,
    /** A plugin. */
    CUSTOM,
    /** The platform did not say. */
    UNKNOWN
  }
}
