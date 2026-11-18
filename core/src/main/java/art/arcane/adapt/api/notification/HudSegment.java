package art.arcane.adapt.api.notification;

import java.util.List;
import java.util.Objects;

/**
 * One purpose's text on the shared action bar. A newer segment with the same purpose replaces the older one; the
 * segment disappears {@code ttlMillis} after it was last published. Text is legacy section text.
 */
public record HudSegment(String purpose, int priority, long ttlMillis, List<HudSlot> slots, String text) {
  public HudSegment {
    Objects.requireNonNull(purpose, "purpose");
    Objects.requireNonNull(slots, "slots");
    Objects.requireNonNull(text, "text");
    if (purpose.isBlank()) {
      throw new IllegalArgumentException("purpose must not be blank");
    }
    if (ttlMillis <= 0L) {
      throw new IllegalArgumentException("ttlMillis must be positive: " + ttlMillis);
    }
    if (slots.isEmpty()) {
      throw new IllegalArgumentException("slots must not be empty");
    }
    slots = List.copyOf(slots);
    text = text.replace('\n', ' ').replace('\r', ' ');
  }
}
