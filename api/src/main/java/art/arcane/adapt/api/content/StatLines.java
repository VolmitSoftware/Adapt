package art.arcane.adapt.api.content;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the stat lines of one adaptation level, shown under its description in every menu. Lines are finished
 * strings in the viewer's language with {@code TextFormat} codes.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class StatLines {
  private final List<String> lines = new ArrayList<>();

  /** Appends one line; null adds an empty line. */
  public StatLines addLore(String line) {
    lines.add(line == null ? "" : line);
    return this;
  }

  /** Appends each line in order; a null list adds nothing and a null line adds an empty one. */
  public StatLines addLore(List<String> more) {
    if (more != null) {
      for (String line : more) {
        addLore(line);
      }
    }
    return this;
  }

  /** The lines collected so far, as a read-only view. */
  public List<String> lines() {
    return Collections.unmodifiableList(lines);
  }

  /** Whether no line was added. */
  public boolean isEmpty() {
    return lines.isEmpty();
  }
}
