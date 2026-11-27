package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.attribute.LegacyModifierKey;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Stacking armor shred for Sunder and Sundering Cleave. Each hit adds a stack up to a cap and lowers the target's
 * armor and toughness under the owner's modifier key; the layer fades once no new stack lands for its duration.
 */
public final class AxeSunderMarks {
  private static final int MAX_TRACKED = 8_192;
  private final Map<UUID, SunderState> targets = new ConcurrentHashMap<>();
  private final Adaptation<?> owner;
  private final LegacyModifierKey modifierKey;
  private final Consumer<LivingRef> onShred;
  private final Consumer<PlayerRef> onApplied;

  /**
   * {@code onShred} runs on the target's thread once a stack took hold; {@code onApplied} then runs on the attacker's
   * thread while the attacker is online.
   */
  public AxeSunderMarks(Adaptation<?> owner, Id modifierKey, Consumer<LivingRef> onShred, Consumer<PlayerRef> onApplied) {
    this.owner = owner;
    this.modifierKey = LegacyModifierKey.of(modifierKey);
    this.onShred = onShred;
    this.onApplied = onApplied;
  }

  /** Adds a stack on the target, which the attacker must be allowed to damage. */
  public void apply(PlayerRef attacker, LivingRef target, int maxStacks, double shred, double toughnessRatio,
                    int durationTicks) {
    if (target.uuid().equals(attacker.uuid()) || !owner.canDamageTarget(attacker, target)) {
      return;
    }

    UUID targetId = target.uuid();
    SunderState state = targets.get(targetId);
    if (state == null) {
      if (targets.size() >= MAX_TRACKED) {
        return;
      }
      state = new SunderState();
      SunderState existing = targets.putIfAbsent(targetId, state);
      if (existing != null) {
        state = existing;
      }
    }

    int stacks;
    long generation;
    synchronized (state) {
      state.stacks = Math.min(state.stacks + 1, maxStacks);
      state.generation++;
      stacks = state.stacks;
      generation = state.generation;
    }

    if (!target.hasAttribute(Attributes.ARMOR)) {
      targets.remove(targetId, state);
      return;
    }

    modifierKey.setTransient(target, Attributes.ARMOR, -(stacks * shred), AttributeOperation.ADD_VALUE);

    boolean toughness = target.hasAttribute(Attributes.ARMOR_TOUGHNESS);
    double toughnessShred = shred * toughnessRatio;
    if (toughnessShred > 0D && toughness) {
      modifierKey.setTransient(target, Attributes.ARMOR_TOUGHNESS, -(stacks * toughnessShred), AttributeOperation.ADD_VALUE);
    }

    synchronized (state) {
      state.target = target;
      state.armor = true;
      state.toughness = toughness;
    }

    SunderState tracked = state;
    long capturedGeneration = generation;
    boolean scheduled = AdaptApi.scheduler().runEntity(
        target,
        () -> expireOwned(targetId, tracked, capturedGeneration),
        durationTicks,
        () -> targets.remove(targetId, tracked)
    );
    if (!scheduled) {
      expireOwned(targetId, tracked, capturedGeneration);
      return;
    }

    onShred.accept(target);

    AdaptApi.scheduler().runEntity(attacker, () -> {
      if (attacker.isOnline()) {
        onApplied.accept(attacker);
      }
    });
  }

  /** Lifts every tracked layer from targets that are still loaded and forgets them all. */
  public void clear() {
    for (SunderState state : targets.values()) {
      LivingRef target;
      boolean armor;
      boolean toughness;
      synchronized (state) {
        target = state.target;
        armor = state.armor;
        toughness = state.toughness;
      }
      if (target == null || !target.isValid()) {
        continue;
      }
      removeModifiers(target, armor, toughness);
    }
    targets.clear();
  }

  private void expireOwned(UUID targetId, SunderState state, long capturedGeneration) {
    LivingRef target;
    boolean armor;
    boolean toughness;
    synchronized (state) {
      if (state.generation != capturedGeneration) {
        return;
      }
      target = state.target;
      armor = state.armor;
      toughness = state.toughness;
    }

    removeModifiers(target, armor, toughness);
    targets.remove(targetId, state);
  }

  private void removeModifiers(LivingRef target, boolean armor, boolean toughness) {
    if (target == null) {
      return;
    }
    if (armor) {
      modifierKey.remove(target, Attributes.ARMOR);
    }
    if (toughness) {
      modifierKey.remove(target, Attributes.ARMOR_TOUGHNESS);
    }
  }

  private static final class SunderState {
    private int stacks;
    private long generation;
    private LivingRef target;
    private boolean armor;
    private boolean toughness;
  }
}
