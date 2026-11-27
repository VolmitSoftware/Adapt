package art.arcane.adapt.api.viewer;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * One claim on a player's private view of an entity's glow. Several layers can colour the same entity for the same
 * viewer; the one with the highest priority shows, and removing it reveals the next. Equal priorities are ordered by
 * id.
 *
 * @param id       names the layer, normally after the adaptation that claims it
 * @param priority higher shows over lower
 */
@ApiStatus.Experimental
public record GlowLayer(Id id, int priority) {
  public GlowLayer {
    Objects.requireNonNull(id, "id");
  }

  /** Whether this layer shows over {@code other} when both colour the same entity. */
  public boolean outranks(GlowLayer other) {
    if (priority != other.priority) {
      return priority > other.priority;
    }
    return id.toString().compareTo(other.id.toString()) > 0;
  }
}
