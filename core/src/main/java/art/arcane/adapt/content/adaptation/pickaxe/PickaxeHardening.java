package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Mining stacks that expire unless renewed; every full set of stacks adds one tier of resistance. */
public final class PickaxeHardening {
  private static final Id RESISTANCE = Id.minecraft("resistance");

  private final Adaptation<?> owner;
  private final Map<UUID, StackState> stacks;

  public PickaxeHardening(Adaptation<?> owner, Map<UUID, StackState> stacks) {
    this.owner = owner;
    this.stacks = stacks;
  }

  /** Adds a stack and applies the resulting resistance tier; returns the stacks before and after. */
  public Step harden(PlayerRef p, int tierCap, int blocksPerStack, long stackDurationMs, int effectDurationTicks) {
    long now = System.currentTimeMillis();
    UUID id = p.uuid();
    StackState state = stacks.get(id);
    int current = state == null || now > state.expiresAt() ? 0 : state.stacks();
    int perStack = Math.max(1, blocksPerStack);
    int maxStacks = tierCap * perStack;
    int next = Math.min(current + 1, maxStacks);
    stacks.put(id, new StackState(next, now + stackDurationMs));
    AbilityGlyphs.duration(id, owner, now, stackDurationMs, next, maxStacks);

    int tier = Math.min(next / perStack, tierCap);
    int prevTier = Math.min(current / perStack, tierCap);
    Step step = new Step(current, next, prevTier, tier, tierCap);
    if (tier <= 0) {
      return step;
    }

    p.addEffect(new EffectInstance(RESISTANCE, effectDurationTicks, tier - 1, false, false, true));
    return step;
  }

  public record Step(int previous, int stacks, int previousTier, int tier, int tierCap) {
    public boolean gained() {
      return stacks > previous;
    }

    public boolean rose() {
      return tier > previousTier;
    }

    public boolean reachedCap() {
      return previousTier < tierCap && tier >= tierCap;
    }
  }

  public record StackState(int stacks, long expiresAt) {
  }
}
