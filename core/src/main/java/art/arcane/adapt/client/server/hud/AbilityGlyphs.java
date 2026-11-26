package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.notification.HudStatusSpec;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.view.ViewIcons;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Locale;
import java.util.UUID;

/**
 * Ability glyphs for players with the client HUD: an adaptation's cooldown, charges, running effect or toggle, sent
 * through {@link AdaptHud#status(UUID, String, HudStatusSpec)} with the adaptation's icon and skill. Every call is a
 * cheap capability check for everyone else and changes nothing they see, so callers keep their own feedback. Each
 * adaptation owns one glyph per {@link HudGlyph} kind, purpose {@code adapt:status:<adaptation>.<kind>}, apart from
 * the adaptation's own text status ({@code adapt:status:<adaptation>}).
 */
public final class AbilityGlyphs {
  /** Shorter cooldowns are input debounce or proc gates and would only flicker. */
  public static final long MIN_COOLDOWN_MILLIS = 1000L;

  private AbilityGlyphs() {
  }

  /** A cooldown that started at {@code startedAtMillis} (epoch) and lasts {@code durationMillis}. */
  public static boolean cooldown(UUID playerId, Adaptation<?> adaptation, long startedAtMillis, long durationMillis) {
    if (durationMillis < MIN_COOLDOWN_MILLIS || !capable(playerId)) {
      return false;
    }
    return AdaptHud.status(playerId, purpose(adaptation, HudGlyph.COOLDOWN), HudStatusSpec.cooldown(skillId(adaptation),
        ViewIcons.adaptation(adaptation), adaptation.getDisplayName(), startedAtMillis, durationMillis));
  }

  /** A cooldown of {@code durationMillis} starting now. */
  public static boolean cooldown(PlayerRef player, Adaptation<?> adaptation, long durationMillis) {
    return cooldown(player.uuid(), adaptation, System.currentTimeMillis(), durationMillis);
  }

  /** {@code lit} of {@code max} charges, minions or stacks, with no recharge running. */
  public static boolean charges(UUID playerId, Adaptation<?> adaptation, int lit, int max) {
    return charges(playerId, adaptation, lit, max, 0L, 0L);
  }

  /** {@code lit} of {@code max}; a recharge from {@code rechargeStartedAtMillis} sweeps toward the next one. */
  public static boolean charges(UUID playerId, Adaptation<?> adaptation, int lit, int max, long rechargeStartedAtMillis,
                                long rechargeMillis) {
    if (!capable(playerId)) {
      return false;
    }
    int cap = Math.max(0, max);
    return AdaptHud.status(playerId, purpose(adaptation, HudGlyph.CHARGES), HudStatusSpec.charges(skillId(adaptation),
        ViewIcons.adaptation(adaptation), adaptation.getDisplayName(), lit, cap,
        rechargeStartedAtMillis, Math.max(0L, rechargeMillis)));
  }

  /** An effect running from {@code startedAtMillis} for {@code durationMillis}, 0 for open-ended. */
  public static boolean duration(UUID playerId, Adaptation<?> adaptation, long startedAtMillis, long durationMillis) {
    return duration(playerId, adaptation, startedAtMillis, durationMillis, 0, 0);
  }

  /** A running effect that also counts something, such as stacks of a buff. */
  public static boolean duration(UUID playerId, Adaptation<?> adaptation, long startedAtMillis, long durationMillis,
                                 int stacks, int maxStacks) {
    if (!capable(playerId)) {
      return false;
    }
    return AdaptHud.status(playerId, purpose(adaptation, HudGlyph.DURATION), HudStatusSpec.duration(skillId(adaptation),
        ViewIcons.adaptation(adaptation), adaptation.getDisplayName(), startedAtMillis, Math.max(0L, durationMillis),
        Math.max(0, stacks), Math.max(0, maxStacks)));
  }

  public static boolean toggle(UUID playerId, Adaptation<?> adaptation, boolean lit) {
    if (!capable(playerId)) {
      return false;
    }
    return AdaptHud.status(playerId, purpose(adaptation, HudGlyph.TOGGLE), HudStatusSpec.toggle(skillId(adaptation),
        ViewIcons.adaptation(adaptation), adaptation.getDisplayName(), lit));
  }

  /** Removes the adaptation's glyph of this kind, for example when a cooldown is reset or the last minion dies. */
  public static void clear(UUID playerId, Adaptation<?> adaptation, HudGlyph glyph) {
    AdaptHud.clearStatus(playerId, purpose(adaptation, glyph));
  }

  public static boolean capable(UUID playerId) {
    return playerId != null && ClientHud.capable(playerId, ClientProtocol.HUD);
  }

  /** The status purpose without the {@code adapt:status:} prefix: {@code <adaptation>.<kind>}. */
  public static String purpose(Adaptation<?> adaptation, HudGlyph glyph) {
    return adaptation.getName() + "." + glyph.name().toLowerCase(Locale.ROOT);
  }

  private static String skillId(Adaptation<?> adaptation) {
    return adaptation.getSkill() == null ? "" : adaptation.getSkill().getName();
  }
}
