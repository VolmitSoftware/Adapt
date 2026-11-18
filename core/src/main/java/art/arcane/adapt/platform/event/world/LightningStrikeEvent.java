package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;

/** Lightning strikes. Mutable: cancelled. */
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

  public WorldRef world() {
    return world;
  }

  public EntityRef lightning() {
    return lightning;
  }

  public Loc location() {
    return location;
  }

  public Cause cause() {
    return cause;
  }

  public enum Cause {
    WEATHER,
    TRIDENT,
    COMMAND,
    SPAWNER,
    TRAP,
    ENCHANTMENT,
    CUSTOM,
    UNKNOWN
  }
}
