package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.text.Text;

import java.util.Locale;

/**
 * The message keys of the pair content Adapt ships: each signature's name, description and stat labels
 * ({@code mutated.signature.<lead>.<partner>.<recipe>.*}) and each hand-written pair text
 * ({@code mutated.override.<lead>.<partner>.<recipe>}). A Barter text starts with the {@code {gesture}} placeholder.
 */
public final class PairMessages {
  private PairMessages() {
  }

  /** The localization key of a built-in signature: {@code adapt.mutated.signature.<lead>.<partner>.<recipe>}. */
  public static String signatureKey(MutationSlot slot) {
    return "adapt.mutated.signature." + segments(slot);
  }

  /** A built-in pair text: {@code adapt.mutated.override.<lead>.<partner>.<recipe>}. */
  public static Text override(MutationSlot slot) {
    return Text.key("adapt.mutated.override." + segments(slot));
  }

  private static String segments(MutationSlot slot) {
    return slot.pair().lead().path() + "." + slot.pair().partner().path() + "."
        + slot.recipe().name().toLowerCase(Locale.ROOT);
  }
}
