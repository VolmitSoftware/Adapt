package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Objects;

/**
 * One skill's block in one slot.
 *
 * @param skill the skill's id, a single path segment
 * @param slot  the slot the block fills
 */
@ApiStatus.Experimental
public record BlockKey(Id skill, BlockSlot slot) {
  public BlockKey {
    ContentIds.requireSingleSegment(skill, "skill");
    Objects.requireNonNull(slot, "slot");
  }

  /** The id the block registers under as an adaptation: {@code <skill>/block/<slot>}. */
  public Id id() {
    return ContentIds.child(skill, "block/" + slot.name().toLowerCase(Locale.ROOT));
  }

  @Override
  public String toString() {
    return id().toString();
  }
}
