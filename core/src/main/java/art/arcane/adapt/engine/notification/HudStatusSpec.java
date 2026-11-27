package art.arcane.adapt.engine.notification;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.view.IconRef;

import java.util.Objects;

/**
 * An ability status for the client HUD, drawn as a {@link HudGlyph}: timed from {@code startedAtMillis} (epoch) for
 * {@code durationMillis}, 0 for an open-ended state. {@code maxStacks} is 0 when the stacks have no cap. {@code skillId}
 * groups and colours the glyph (empty for none). Label is Adapt markup; the client shows it only in its HUD editor.
 */
public record HudStatusSpec(HudGlyph glyph, String skillId, IconRef icon, String label, long startedAtMillis,
                            long durationMillis, int stacks, int maxStacks, boolean lit) {
  public HudStatusSpec {
    Objects.requireNonNull(glyph, "glyph");
    Objects.requireNonNull(skillId, "skillId");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(label, "label");
    if (durationMillis < 0L) {
      throw new IllegalArgumentException("durationMillis must not be negative: " + durationMillis);
    }
    if (stacks < 0 || maxStacks < 0) {
      throw new IllegalArgumentException("stacks must not be negative: " + stacks + "/" + maxStacks);
    }
  }

  public static HudStatusSpec cooldown(String skillId, IconRef icon, String label, long startedAtMillis, long durationMillis) {
    return new HudStatusSpec(HudGlyph.COOLDOWN, skillId, icon, label, startedAtMillis, durationMillis, 0, 0, false);
  }

  /** {@code lit} of {@code max} pips (no cap when {@code max} is 0), with no recharge running. */
  public static HudStatusSpec charges(String skillId, IconRef icon, String label, int lit, int max) {
    return charges(skillId, icon, label, lit, max, 0L, 0L);
  }

  /** {@code lit} of {@code max} pips; a recharge from {@code rechargeStartedAtMillis} sweeps toward the next pip. */
  public static HudStatusSpec charges(String skillId, IconRef icon, String label, int lit, int max,
                                      long rechargeStartedAtMillis, long rechargeMillis) {
    return new HudStatusSpec(HudGlyph.CHARGES, skillId, icon, label, rechargeStartedAtMillis, rechargeMillis,
        max <= 0 ? Math.max(0, lit) : Math.max(0, Math.min(lit, max)), Math.max(0, max), false);
  }

  /** A running effect; {@code durationMillis} 0 is open-ended. {@code stacks} 0 shows no count. */
  public static HudStatusSpec duration(String skillId, IconRef icon, String label, long startedAtMillis, long durationMillis,
                                       int stacks, int maxStacks) {
    return new HudStatusSpec(HudGlyph.DURATION, skillId, icon, label, startedAtMillis, durationMillis, stacks, maxStacks, false);
  }

  public static HudStatusSpec toggle(String skillId, IconRef icon, String label, boolean lit) {
    return new HudStatusSpec(HudGlyph.TOGGLE, skillId, icon, label, 0L, 0L, 0, 0, lit);
  }
}
