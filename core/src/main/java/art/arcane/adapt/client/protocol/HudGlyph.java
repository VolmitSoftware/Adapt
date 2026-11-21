package art.arcane.adapt.client.protocol;

/** How the client draws a {@link ServerFrame.HudStatus}. */
public enum HudGlyph {
  /** Unusable until {@code startedAt + duration}: a sweep that empties, then a short ready pulse. */
  COOLDOWN,
  /** {@code stacks} of {@code maxStacks} lit as pips; a duration, when set, sweeps toward the next pip. */
  CHARGES,
  /** An effect running until {@code startedAt + duration} (open-ended when 0): a draining ring. */
  DURATION,
  /** An on or off state: lit when {@code lit}, dim otherwise. */
  TOGGLE
}
