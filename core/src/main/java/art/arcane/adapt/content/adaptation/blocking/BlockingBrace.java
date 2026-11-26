package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A knockback brace held through attribute modifiers for as long as a stance lasts. Each session refreshes on the
 * player's thread every 2 ticks, applying the brace while the stance asks for one and clearing it otherwise.
 */
public final class BlockingBrace {
  private static final String SLOT_KNOCKBACK = "knockback";
  private static final String SLOT_EXPLOSION = "explosion";
  private static final int REFRESH_TICKS = 2;
  private final Adaptation<?> owner;
  private final Map<UUID, State> states;
  private final Id explosionResistance;
  private final Stance stance;

  /**
   * {@code explosionResistance} is the explosion knockback attribute, passed in by an owner that declares the
   * platform need for it.
   */
  public BlockingBrace(Adaptation<?> owner, Map<UUID, State> states, Id explosionResistance, Stance stance) {
    this.owner = owner;
    this.states = states;
    this.explosionResistance = explosionResistance;
    this.stance = stance;
  }

  public boolean active(PlayerRef p) {
    return states.containsKey(p.uuid());
  }

  public void start(PlayerRef p) {
    State state = states.computeIfAbsent(p.uuid(), key -> new State());
    if (state.refreshScheduled.compareAndSet(false, true)
        && !Tasks.runEntity(p, () -> refresh(p, state), 1)) {
      end(p);
    }
  }

  public void end(PlayerRef p) {
    if (p == null) {
      return;
    }

    State state = states.remove(p.uuid());
    if (state == null) {
      return;
    }

    state.refreshScheduled.set(false);
    release(p, state);
  }

  /** Removes every brace this owner applied and forgets all sessions. */
  public void clear() {
    for (UUID playerId : states.keySet()) {
      PlayerRef p = AdaptCore.platform().host().player(playerId);
      if (p != null) {
        AdaptAttributeService.get().removeAll(p, owner.getName());
      }
    }
    states.clear();
  }

  private void refresh(PlayerRef p, State state) {
    if (states.get(p.uuid()) != state || !p.isOnline()) {
      state.refreshScheduled.set(false);
      return;
    }

    int level = owner.getActiveLevel(p);
    if (!stance.holds(p, level)) {
      end(p);
      return;
    }

    apply(p, state, stance.amount(p, level));

    if (!Tasks.runEntity(p, () -> refresh(p, state), REFRESH_TICKS)) {
      end(p);
    }
  }

  private void apply(PlayerRef p, State state, double amount) {
    if (amount <= 0) {
      release(p, state);
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.apply(p, owner.getName(), SLOT_KNOCKBACK, Attributes.KNOCKBACK_RESISTANCE, amount, AttributeOperation.ADD_VALUE);
    attributes.apply(p, owner.getName(), SLOT_EXPLOSION, explosionResistance, amount, AttributeOperation.ADD_VALUE);
    if (!state.braced) {
      AbilityGlyphs.toggle(p.uuid(), owner, true);
      state.braced = true;
      stance.braced(p);
    }
  }

  private void release(PlayerRef p, State state) {
    if (!state.braced) {
      return;
    }

    state.braced = false;
    AbilityGlyphs.clear(p.uuid(), owner, HudGlyph.TOGGLE);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, owner.getName(), SLOT_KNOCKBACK, Attributes.KNOCKBACK_RESISTANCE);
    attributes.remove(p, owner.getName(), SLOT_EXPLOSION, explosionResistance);
    stance.released(p);
  }

  /** When a session lasts and how strong its brace is. */
  public interface Stance {
    /** False ends the session. */
    boolean holds(PlayerRef p, int level);

    /** Knockback resistance to add, 0 to 1; 0 or less releases the brace for this refresh. */
    double amount(PlayerRef p, int level);

    default void braced(PlayerRef p) {
    }

    default void released(PlayerRef p) {
    }
  }

  public static final class State {
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();
    private boolean braced;
  }
}
