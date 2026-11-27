package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** The timed attack-damage buff Hunter's Strength grants when struck, kept per owning adaptation. */
public final class HunterStrengthBuff {
  private static final Id STRENGTH = Id.minecraft("strength");
  private final Adaptation<?> owner;
  private final Map<UUID, Long> strengthUntil;

  public HunterStrengthBuff(Adaptation<?> owner, Map<UUID, Long> strengthUntil) {
    this.owner = owner;
    this.strengthUntil = strengthUntil;
  }

  public static double strengthDamageBonus(int level) {
    return 3.0D * (level + 1);
  }

  public static long buffDurationTicks(int baseTicksPerLevel, int level, long remainingTicks, boolean stackBuff) {
    long base = (long) baseTicksPerLevel * level;
    if (base <= 0L) {
      return 0L;
    }
    return base + (stackBuff ? Math.max(0L, remainingTicks) : 0L);
  }

  public boolean canApply(PlayerRef p, int level, int baseTicksPerLevel, boolean stackBuff) {
    if (p.hasEffect(STRENGTH)) {
      return false;
    }

    long remainingTicks = remainingTicks(p, System.currentTimeMillis());
    if (remainingTicks > 0L && !stackBuff) {
      return false;
    }

    return buffDurationTicks(baseTicksPerLevel, level, remainingTicks, stackBuff) > 0L;
  }

  /** Applies {@code damageBonus} attack damage for {@code baseTicksPerLevel * level} ticks when the buff may start. */
  public boolean apply(PlayerRef p, int level, int baseTicksPerLevel, boolean stackBuff, double damageBonus) {
    if (!canApply(p, level, baseTicksPerLevel, stackBuff)) {
      return false;
    }

    long now = System.currentTimeMillis();
    long durationTicks = buffDurationTicks(baseTicksPerLevel, level, remainingTicks(p, now), stackBuff);
    AdaptAttributeService.get().applyTimed(p, owner.getId().toString(), "strength", Attributes.ATTACK_DAMAGE, damageBonus,
        AttributeOperation.ADD_VALUE, durationTicks);
    strengthUntil.put(p.uuid(), now + (durationTicks * 50L));
    HudStatus.duration(p.uuid(), owner, now, durationTicks * 50L);
    return true;
  }

  public void remove(PlayerRef p) {
    AdaptAttributeService.get().remove(p, owner.getId().toString(), "strength", Attributes.ATTACK_DAMAGE);
  }

  public void forget(UUID playerId) {
    strengthUntil.remove(playerId);
    HudStatus.clear(playerId, owner, HudGlyph.DURATION);
  }

  private long remainingTicks(PlayerRef p, long now) {
    Long until = strengthUntil.get(p.uuid());
    return until == null ? 0L : Math.max(0L, (until - now) / 50L);
  }
}
