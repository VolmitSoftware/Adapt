package art.arcane.adapt.api.hud;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/**
 * What an adaptation shows a player while it works: a line above the hotbar, and on native clients a glyph for its
 * cooldown, charges, duration or toggle.
 *
 * <p>Adapt arbitrates the action bar between everything that wants it, so call these instead of sending action bar
 * text yourself. Glyph calls return false, and do nothing, for a player without a native client; a
 * {@code Cooldowns.mark(player, millis)} already shows the cooldown glyph.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class HudStatus {
  private HudStatus() {
  }

  /** A short line above the hotbar, in the player's language, attributed to {@code source} for arbitration. */
  public static void show(PlayerRef player, Adaptation<?> source, Text message) {
    Engine.bridge().hud().actionBar(player, source, message.resolve(player));
  }

  /** A short line above the hotbar. */
  public static void show(PlayerRef player, String message) {
    Engine.bridge().hud().actionBar(player, null, message);
  }

  /** A low-priority line that stays until cleared or replaced, such as "Stealth: hidden". One per adaptation. */
  public static void ambient(PlayerRef player, Adaptation<?> source, String message) {
    ambient(player, source.getId().toString(), message);
  }

  /** Removes the ambient line of {@code source}. */
  public static void clearAmbient(PlayerRef player, Adaptation<?> source) {
    clearAmbient(player, source.getId().toString());
  }

  /**
   * An ambient line keyed by {@code purpose} instead of one adaptation, so several adaptations can share one line or
   * one adaptation can hold several. A new line with the same purpose replaces the old one.
   */
  public static void ambient(PlayerRef player, String purpose, String message) {
    Engine.bridge().hud().ambient(player, purpose, message);
  }

  /** Removes the ambient line kept under {@code purpose}. */
  public static void clearAmbient(PlayerRef player, String purpose) {
    Engine.bridge().hud().clearAmbient(player, purpose);
  }

  /** A cooldown that started now. Cooldowns under one second draw nothing and return false. */
  public static boolean cooldown(PlayerRef player, Adaptation<?> source, long durationMillis) {
    return cooldown(player.uuid(), source, System.currentTimeMillis(), durationMillis);
  }

  /** A cooldown that started at {@code startedAtMillis}, wall time. */
  public static boolean cooldown(UUID player, Adaptation<?> source, long startedAtMillis, long durationMillis) {
    return Engine.bridge().hud().glyph(player, source, HudGlyph.COOLDOWN, 0, 0, startedAtMillis, durationMillis, false);
  }

  /** {@code lit} of {@code max} charges, with no recharge shown. */
  public static boolean charges(UUID player, Adaptation<?> source, int lit, int max) {
    return charges(player, source, lit, max, 0L, 0L);
  }

  /** Charges with the time the next one takes to come back. */
  public static boolean charges(UUID player, Adaptation<?> source, int lit, int max, long rechargeStartedAtMillis,
                                long rechargeMillis) {
    return Engine.bridge().hud().glyph(player, source, HudGlyph.CHARGES, lit, max, rechargeStartedAtMillis,
        rechargeMillis, false);
  }

  /** A running effect; a duration of 0 is open-ended until cleared. */
  public static boolean duration(UUID player, Adaptation<?> source, long startedAtMillis, long durationMillis) {
    return duration(player, source, startedAtMillis, durationMillis, 0, 0);
  }

  /** A running effect that also counts something, such as {@code stacks} of {@code maxStacks} of a buff. */
  public static boolean duration(UUID player, Adaptation<?> source, long startedAtMillis, long durationMillis,
                                 int stacks, int maxStacks) {
    return Engine.bridge().hud().glyph(player, source, HudGlyph.DURATION, stacks, maxStacks, startedAtMillis,
        durationMillis, false);
  }

  /** An on or off state; {@code lit} is on. */
  public static boolean toggle(UUID player, Adaptation<?> source, boolean lit) {
    return Engine.bridge().hud().glyph(player, source, HudGlyph.TOGGLE, 0, 0, 0L, 0L, lit);
  }

  /** Removes the {@code glyph} that {@code source} shows the player. */
  public static void clear(UUID player, Adaptation<?> source, HudGlyph glyph) {
    Engine.bridge().hud().clearGlyph(player, source, glyph);
  }

  /** Whether the player's client draws glyphs. */
  public static boolean capable(UUID player) {
    return Engine.bridge().hud().capable(player);
  }
}
