package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;

/**
 * Horizontal movement sampled from move events, summed within one tick. A sample only counts as speed on the tick it
 * was taken or the next one. The sample map belongs to the owning adaptation.
 */
public final class KineticsStride {
  private final Map<UUID, Movement> movement;

  public KineticsStride(Map<UUID, Movement> movement) {
    this.movement = movement;
  }

  /** Records the move, or forgets the player when {@code tracking} is false. */
  public void track(PlayerMoveEvent event, boolean tracking) {
    PlayerRef player = event.player();
    if (!tracking) {
      movement.remove(player.uuid());
      return;
    }
    if (!event.changedPosition()) {
      return;
    }
    Loc from = event.from();
    Loc to = event.to();
    int tick = player.ticksLived();
    Movement previous = movement.get(player.uuid());
    double x = to.x() - from.x();
    double z = to.z() - from.z();
    if (previous != null && previous.tick == tick) {
      x += previous.x;
      z += previous.z;
    }
    if (previous == null) {
      previous = new Movement();
      movement.put(player.uuid(), previous);
    }
    previous.tick = tick;
    previous.x = x;
    previous.z = z;
  }

  public void forget(UUID id) {
    movement.remove(id);
  }

  public void clear() {
    movement.clear();
  }

  /** Blocks per tick the player moved horizontally on the latest fresh sample, 0 without one. */
  public double speed(PlayerRef player) {
    Movement sample = movement.get(player.uuid());
    int age = sample == null ? -1 : player.ticksLived() - sample.tick;
    return age >= 0 && age <= 1 ? KineticsChargeLance.horizontalSpeed(sample.x, sample.z) : 0D;
  }

  public static final class Movement {
    private int tick;
    private double x;
    private double z;
  }
}
