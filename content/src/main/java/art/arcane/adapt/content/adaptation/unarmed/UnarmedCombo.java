package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Map;
import java.util.UUID;

/** Bare-hand combo stacks shared by Combo Chain and Unbroken Combo. */
public final class UnarmedCombo {
  private final Adaptation<?> owner;
  private final Map<UUID, State> combos;

  public UnarmedCombo(Adaptation<?> owner, Map<UUID, State> combos) {
    this.owner = owner;
    this.combos = combos;
  }

  /** Adds a stack, starting over when the last hit is older than {@code windowMillis}. Returns the new stack count. */
  public int build(PlayerRef p, int maxStacks, long windowMillis, long now) {
    State state = combos.computeIfAbsent(p.uuid(), (UUID id) -> new State());
    if (now - state.lastHitMillis > windowMillis) {
      state.stacks = 0;
    }

    state.lastHitMillis = now;
    state.stacks = Math.min(maxStacks, state.stacks + 1);
    HudStatus.duration(p.uuid(), owner, now, windowMillis, state.stacks, maxStacks);
    return state.stacks;
  }

  /** Live stacks: 0 when there is no combo or its last hit is older than {@code windowMillis}. */
  public int stacks(PlayerRef p, long windowMillis, long now) {
    State state = combos.get(p.uuid());
    return state == null || now - state.lastHitMillis > windowMillis ? 0 : state.stacks;
  }

  /** Restarts the window of a live combo without adding a stack. */
  public void hold(PlayerRef p, int maxStacks, long windowMillis, long now) {
    State state = combos.get(p.uuid());
    if (state == null || now - state.lastHitMillis > windowMillis) {
      return;
    }
    state.lastHitMillis = now;
    HudStatus.duration(p.uuid(), owner, now, windowMillis, state.stacks, maxStacks);
  }

  /** Drops the combo when a swing missed more than {@code graceMillis} after the last hit. */
  public void missed(PlayerRef p, long graceMillis, long now) {
    State state = combos.get(p.uuid());
    if (state != null && now - state.lastHitMillis > graceMillis) {
      drop(p);
    }
  }

  /** Ends the combo without the broken-combo effect, so the next hit starts a new one. */
  public void spend(PlayerRef p) {
    if (combos.remove(p.uuid()) != null) {
      HudStatus.clear(p.uuid(), owner, HudGlyph.DURATION);
    }
  }

  public void drop(PlayerRef p) {
    State state = combos.remove(p.uuid());
    if (state != null) {
      HudStatus.clear(p.uuid(), owner, HudGlyph.DURATION);
    }
    if (state == null || state.stacks < 3) {
      return;
    }

    UnarmedComboChain.brokenFx(owner, p.location().add(0, 1, 0));
  }

  public static final class State {
    private int stacks;
    private long lastHitMillis;
  }
}
