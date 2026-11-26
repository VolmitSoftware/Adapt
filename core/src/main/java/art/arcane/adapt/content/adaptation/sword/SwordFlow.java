package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Map;
import java.util.UUID;

/**
 * Attack-speed stacks built by consecutive sword hits. Each hit adds a stack up to a cap and refreshes the window;
 * stacks lapse once the window passes without a hit. The stack maps belong to the owning adaptation.
 */
public final class SwordFlow {
  static final double ATTACK_SPEED_PER_STACK = 0.10;
  private static final String FLOW_SLOT = "flow";

  private final Adaptation<?> owner;
  private final Map<UUID, Integer> stacks;
  private final Map<UUID, Long> expireAt;

  public SwordFlow(Adaptation<?> owner, Map<UUID, Integer> stacks, Map<UUID, Long> expireAt) {
    this.owner = owner;
    this.stacks = stacks;
    this.expireAt = expireAt;
  }

  /** The stacks before and after one hit. */
  public record Step(int previous, int stacks) {
  }

  /** Adds one stack, refreshes the window, and applies the matching attack speed for the window. */
  public Step build(PlayerRef player, int cap, long windowMillis, long now) {
    UUID id = player.uuid();
    int current = stacks.getOrDefault(id, 0);
    if (now > expireAt.getOrDefault(id, 0L)) {
      current = 0;
    }
    int next = Math.min(cap, current + 1);
    stacks.put(id, next);
    expireAt.put(id, now + windowMillis);
    AbilityGlyphs.duration(id, owner, now, windowMillis, next, cap);
    AdaptAttributeService.get().applyTimed(player, owner.getName(), FLOW_SLOT, Attributes.ATTACK_SPEED,
        attackSpeedBonus(next), AttributeOperation.ADD_MULTIPLIED_BASE, effectDurationTicks(windowMillis));
    return new Step(current, next);
  }

  /** Stacks held now, 0 once the window lapsed. */
  public int stacks(UUID id, long now) {
    return now > expireAt.getOrDefault(id, 0L) ? 0 : stacks.getOrDefault(id, 0);
  }

  public boolean holds(UUID id) {
    return stacks.getOrDefault(id, 0) > 0;
  }

  public boolean expired(UUID id, long now) {
    return now > expireAt.getOrDefault(id, 0L);
  }

  public void clear(PlayerRef player) {
    UUID id = player.uuid();
    stacks.remove(id);
    expireAt.remove(id);
    AbilityGlyphs.clear(id, owner, HudGlyph.DURATION);
    AdaptAttributeService.get().remove(player, owner.getName(), FLOW_SLOT, Attributes.ATTACK_SPEED);
  }

  static int effectDurationTicks(long windowMillis) {
    long durationTicks = Math.max(20L, (Math.max(0L, windowMillis) + 49L) / 50L);
    return (int) Math.min(Integer.MAX_VALUE, durationTicks);
  }

  static double attackSpeedBonus(int stacks) {
    return Math.max(0, stacks) * ATTACK_SPEED_PER_STACK;
  }
}
