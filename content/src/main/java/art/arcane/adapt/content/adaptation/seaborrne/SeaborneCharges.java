package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Hydro Jet's regenerating charge pool and its HUD glyph, owned by the calling adaptation. */
public final class SeaborneCharges {
  private final Adaptation<?> owner;
  private final Map<UUID, Charge> charges;
  private final LongSupplier regenMillis;

  public SeaborneCharges(Adaptation<?> owner, Map<UUID, Charge> charges, LongSupplier regenMillis) {
    this.owner = owner;
    this.charges = charges;
    this.regenMillis = regenMillis;
  }

  /** Spends one charge out of {@code max}; false when none is ready. */
  public boolean spend(PlayerRef player, int max) {
    long now = System.currentTimeMillis();
    Charge state = charges.computeIfAbsent(player.uuid(), (UUID key) -> new Charge(max, now));
    state.refill(max, regenMillis.getAsLong(), now);
    if (state.value < 1D) {
      publish(player, state, max, now);
      return false;
    }
    state.value -= 1D;
    publish(player, state, max, now);
    return true;
  }

  public void forget(UUID playerId) {
    charges.remove(playerId);
  }

  private void publish(PlayerRef player, Charge state, int max, long now) {
    UUID id = player.uuid();
    long regen = regenMillis.getAsLong();
    int lit = (int) Math.floor(state.value);
    if (lit >= max || regen <= 0L) {
      HudStatus.clear(id, owner, HudGlyph.CHARGES);
      return;
    }
    long rechargeStartedAt = now - (long) ((state.value - lit) * regen);
    if (!HudStatus.charges(id, owner, lit, max, rechargeStartedAt, regen) || state.refreshPending) {
      return;
    }
    int waitTicks = (int) Math.max(1L, (rechargeStartedAt + regen - now + 49L) / 50L);
    state.refreshPending = AdaptApi.scheduler().runEntity(player, () -> {
      state.refreshPending = false;
      if (charges.get(id) != state) {
        return;
      }
      long at = System.currentTimeMillis();
      state.refill(max, regenMillis.getAsLong(), at);
      publish(player, state, max, at);
    }, waitTicks);
  }

  public static int maxCharges(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  public static double refillCharges(double current, long elapsedMillis, long regenMillis, double max) {
    if (regenMillis <= 0L) {
      return max;
    }
    double refilled = current + ((double) Math.max(0L, elapsedMillis) / regenMillis);
    return Math.min(max, refilled);
  }

  public static final class Charge {
    private double value;
    private long lastRefillAt;
    private boolean refreshPending;

    private Charge(double value, long lastRefillAt) {
      this.value = value;
      this.lastRefillAt = lastRefillAt;
    }

    private void refill(double max, long regenMillis, long now) {
      value = refillCharges(value, now - lastRefillAt, regenMillis, max);
      lastRefillAt = now;
    }
  }
}
