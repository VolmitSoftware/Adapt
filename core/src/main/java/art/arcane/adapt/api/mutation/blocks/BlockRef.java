package art.arcane.adapt.api.mutation.blocks;

import java.util.Locale;

public record BlockRef(String skillId, BlockSlot slot) {
  @Override
  public String toString() {
    return skillId + ":" + slot.name().toLowerCase(Locale.ROOT);
  }
}
