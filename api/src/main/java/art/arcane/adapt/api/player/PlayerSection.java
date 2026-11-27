package art.arcane.adapt.api.player;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * A block of an addon's own data in each player's save, read with {@link PlayerProfile#section} and
 * {@link PlayerDirectory#peek}. Only {@link art.arcane.adapt.api.addon.ContentRegistrar#playerSection} makes one; Adapt
 * records the declaring addon as the section's owner and its value type.
 *
 * <p>The object is the permission to read the section: Adapt accepts only the instance a registrar handed out for an
 * installed addon, and refuses any other {@code PlayerSection}, such as one an addon implements itself to reach
 * another addon's data, naming the section, its owner and the caller. An addon that wants another addon to read its
 * section passes it the object.
 *
 * <p>The value is kept live with the player and written with the rest of the player file: its fields are stored as a
 * JSON object under the full {@link #id()}, so it should be a plain class of fields (numbers, strings, lists, maps and
 * other plain classes). A save without the section, or with it set to null, starts from the declared initial value.
 * Sections no installed addon declares stay in the save untouched. One id holds one value type: reading a live value
 * of another type throws.
 *
 * @param <T> the section's value type
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface PlayerSection<T> {
  /** The section's id, {@code <namespace>:<path>} of the declaring addon; it is file content and must never change. */
  Id id();

  /** The value class. */
  Class<T> type();
}
