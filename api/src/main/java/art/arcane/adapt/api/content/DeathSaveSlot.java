package art.arcane.adapt.api.content;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A cooldown that death saves spend: once one save in a slot has saved a player, no save in that slot can save them
 * again until the cooldown runs out.
 *
 * <p>A slot is its id. Declare it as a constant and use it from every {@link DeathSave} that should share the
 * cooldown; a save of another addon shares it by building a slot with the same id. A save that should stand alone
 * gets a slot of its own, normally named after the adaptation.
 *
 * <pre>{@code
 * public static final DeathSaveSlot SLOT = DeathSaveSlot.of(Id.of("alchemist", "philosophers_stone"));
 * }</pre>
 *
 * <p>The deadline is saved on the player under {@code <namespace>:death_save_<path>} with the path's {@code /}
 * turned into {@code _}, so it survives restarts and is shared by every server on the same player data.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class DeathSaveSlot {
  private final Id id;
  private final DataKey<Long> deadline;

  private DeathSaveSlot(Id id) {
    this.id = id;
    this.deadline = DataKey.of(Id.of(id.namespace(), "death_save_" + id.path().replace('/', '_')), DataCodecs.LONG);
  }

  /** The slot named {@code id}; any two slots with equal ids are the same slot. */
  public static DeathSaveSlot of(Id id) {
    return new DeathSaveSlot(ContentIds.require(id, "death-save slot"));
  }

  /** The id that names this slot. */
  public Id id() {
    return id;
  }

  /** The player data key the deadline is saved under, as epoch milliseconds. */
  public DataKey<Long> deadlineKey() {
    return deadline;
  }

  /** Whether a save in this slot may act for {@code player} at {@code now}, in epoch milliseconds. */
  public boolean isReady(PlayerRef player, long now) {
    return remaining(player, now) == 0L;
  }

  /**
   * Milliseconds until the slot is ready again, 0 when it is. {@link Long#MAX_VALUE} when the saved deadline cannot
   * be read; the failure is logged, and the slot stays closed rather than granting a save it cannot record.
   */
  public long remaining(PlayerRef player, long now) {
    try {
      DataHolder data = player.data();
      if (!data.has(deadline) && data.hasAny(deadline.id())) {
        throw new IllegalArgumentException("The value stored under " + deadline.id() + " is not a LONG");
      }
      long until = data.getOrDefault(deadline, 0L);
      return until <= now ? 0L : until - now;
    } catch (RuntimeException failure) {
      report(player, failure);
      return Long.MAX_VALUE;
    }
  }

  /**
   * Starts the cooldown if the slot is ready, so the caller's save may go ahead.
   *
   * @param durationMillis the cooldown; negative counts as 0
   * @return false when the slot was not ready or the deadline could not be saved; the caller must then not save
   */
  public boolean activate(PlayerRef player, long now, long durationMillis) {
    if (!isReady(player, now)) {
      return false;
    }
    long duration = Math.max(0L, durationMillis);
    long until = now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
    try {
      player.data().set(deadline, until);
      return true;
    } catch (RuntimeException failure) {
      report(player, failure);
      return false;
    }
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DeathSaveSlot slot && id.equals(slot.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "DeathSaveSlot[" + id + "]";
  }

  private void report(PlayerRef player, RuntimeException failure) {
    AdaptApi.log().error("Could not use death-save slot " + id + " for " + player.uuid(), failure);
  }
}
