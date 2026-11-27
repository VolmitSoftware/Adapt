package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** Shadowmeld's short, quietly refreshed invisibility. */
public final class StealthVeil {
  private static final int INVISIBILITY_REFRESH_TICKS = 40;
  private static final Id INVISIBILITY = Id.minecraft("invisibility");

  private StealthVeil() {
  }

  /** Renews the invisibility when it runs low; true when this call applied it. */
  public static boolean veil(PlayerRef player) {
    EffectInstance current = player.effect(INVISIBILITY);
    if (current != null && current.durationTicks() > INVISIBILITY_REFRESH_TICKS + 5) {
      return false;
    }
    player.addEffect(EffectInstance.of(INVISIBILITY, INVISIBILITY_REFRESH_TICKS, 0).withParticles(false).withIcon(false));
    return true;
  }

  /** Removes the veil, leaving longer or stronger invisibility from other sources alone. */
  public static void unveil(PlayerRef player) {
    EffectInstance current = player.effect(INVISIBILITY);
    if (current != null && current.amplifier() == 0
        && current.durationTicks() <= INVISIBILITY_REFRESH_TICKS + 5) {
      player.removeEffect(INVISIBILITY);
    }
  }
}
