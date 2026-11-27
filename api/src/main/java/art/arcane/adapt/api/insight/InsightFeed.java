package art.arcane.adapt.api.insight;

import art.arcane.adapt.api.internal.Engine;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;

/**
 * Sends Insight readouts to viewers whose Adapt client draws them. A feed remembers what it sent each viewer, so
 * {@link #show} only sends what changed: the whole target when it is new or changed shape, a vitals update when only
 * health moved or a second passed, marks when they moved by more than a twentieth of health. Keep one feed per
 * adaptation and call {@link #forget} when a viewer leaves.
 */
@ApiStatus.Experimental
public interface InsightFeed {
  /** The most marks one {@link #show} carries. */
  int MAX_MARKS = 16;

  /** A new feed over the client session. Needs a running core. */
  static InsightFeed create() {
    return Engine.bridge().viewers().insightFeed();
  }

  /** Whether the viewer's client draws Insight readouts. */
  boolean capable(UUID viewer);

  /**
   * Brings the viewer's client up to date; false when the viewer has no Insight client or the send failed.
   *
   * @param marks at most {@link #MAX_MARKS}
   * @throws IllegalArgumentException when {@code marks} holds more than {@link #MAX_MARKS}
   */
  boolean show(UUID viewer, InsightView view, List<InsightMark> marks);

  /** Tells the viewer's client the target is gone, when it was shown one. */
  void clear(UUID viewer);

  /** Forgets the viewer without sending, as when they leave. */
  void forget(UUID viewer);

  /** Forgets every viewer without sending. */
  void forgetAll();
}
