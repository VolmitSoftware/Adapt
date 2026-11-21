package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.volmlib.util.math.M;

import java.util.Map;
import java.util.UUID;

/**
 * Last-use gates keyed by player (or target) id. A gate owned by an adaptation shows its cooldown on the client HUD
 * whenever it is marked with its length, {@link #mark(UUID, long)}; {@link #mark(UUID)} stays silent, for input
 * debounce, proc gates and gates keyed by something other than the player.
 */
public final class Cooldowns {
  private final Map<UUID, Long> lastUse;
  private final Adaptation<?> owner;

  Cooldowns(Map<UUID, Long> lastUse, Adaptation<?> owner) {
    this.lastUse = lastUse;
    this.owner = owner;
  }

  public boolean isReady(UUID id, long cooldownMs) {
    Long last = lastUse.get(id);
    if (last == null) {
      return true;
    }
    if (M.ms() - last < cooldownMs) {
      return false;
    }
    lastUse.remove(id, last);
    return true;
  }

  public void mark(UUID id) {
    lastUse.put(id, M.ms());
  }

  /** Marks the gate and shows a {@code cooldownMs} cooldown glyph to {@code id} when it is a player with the client HUD. */
  public void mark(UUID id, long cooldownMs) {
    long now = M.ms();
    lastUse.put(id, now);
    if (owner != null) {
      AbilityGlyphs.cooldown(id, owner, now, cooldownMs);
    }
  }

  public long remaining(UUID id, long cooldownMs) {
    Long last = lastUse.get(id);
    if (last == null) {
      return 0;
    }
    return Math.max(0, cooldownMs - (M.ms() - last));
  }

  /** Opens the gate early; a shown glyph goes away with it. */
  public void clear(UUID id) {
    if (lastUse.remove(id) != null && owner != null) {
      AbilityGlyphs.clear(id, owner, HudGlyph.COOLDOWN);
    }
  }

  public void clearExpired(long cooldownMs) {
    long now = M.ms();
    lastUse.values().removeIf(last -> now - last >= cooldownMs);
  }
}
