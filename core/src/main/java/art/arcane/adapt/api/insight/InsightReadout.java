package art.arcane.adapt.api.insight;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.client.view.InsightBond;
import art.arcane.adapt.client.view.InsightEffect;
import art.arcane.adapt.client.view.InsightKind;
import art.arcane.adapt.client.view.InsightMark;
import art.arcane.adapt.client.view.InsightStat;
import art.arcane.adapt.client.view.InsightStatKind;
import art.arcane.adapt.client.view.InsightView;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Reads what Insight reveals about a creature. Call on the target's thread. */
public final class InsightReadout {
  private InsightReadout() {
  }

  /**
   * @param ownerNames name of an online owner by id, or null
   * @param harmful    whether an effect type is harmful
   */
  public static InsightView read(LivingRef target, UUID viewerId, InsightTier tier, boolean stableHand,
                                 Function<UUID, String> ownerNames, Predicate<Id> harmful) {
    UUID owner = target.tameOwner();
    InsightBond bond = owner == null
        ? target.has(EntityTrait.TAMEABLE) ? InsightBond.WILD : InsightBond.NONE
        : owner.equals(viewerId) ? InsightBond.YOURS : InsightBond.OWNED;
    String ownerName = bond == InsightBond.OWNED ? nonNull(ownerNames.apply(owner)) : "";
    return new InsightView(target.entityId(), target.type().toString(), name(target), kind(target),
        finite(target.health()), finite(target.maxHealth()), finite(target.absorption()),
        attribute(target, Attributes.ARMOR, 0D), bond, ownerName, stableHand, stats(target, tier),
        tier.effects() ? effects(target, harmful) : List.of());
  }

  public static InsightMark mark(LivingRef target) {
    double max = target.maxHealth();
    double fraction = max > 0D && Double.isFinite(max) ? Math.max(0D, Math.min(1D, target.health() / max)) : 0D;
    return new InsightMark(target.entityId(), kind(target), fraction);
  }

  public static InsightKind kind(LivingRef target) {
    if (target instanceof PlayerRef) {
      return InsightKind.PLAYER;
    }
    return target.has(EntityTrait.ENEMY) ? InsightKind.HOSTILE : InsightKind.PASSIVE;
  }

  static List<InsightStat> stats(LivingRef target, InsightTier tier) {
    List<InsightStat> stats = new ArrayList<>(6);
    add(stats, target, InsightStatKind.ATTACK, Attributes.ATTACK_DAMAGE);
    if (tier.movement()) {
      add(stats, target, InsightStatKind.SPEED, Attributes.MOVEMENT_SPEED);
      add(stats, target, InsightStatKind.JUMP, Attributes.JUMP_STRENGTH);
    }
    if (tier.defenses()) {
      add(stats, target, InsightStatKind.TOUGHNESS, Attributes.ARMOR_TOUGHNESS);
      add(stats, target, InsightStatKind.KNOCKBACK_RESISTANCE, Attributes.KNOCKBACK_RESISTANCE);
      add(stats, target, InsightStatKind.FOLLOW_RANGE, Attributes.FOLLOW_RANGE);
    }
    return stats;
  }

  private static List<InsightEffect> effects(LivingRef target, Predicate<Id> harmful) {
    List<EffectInstance> active = new ArrayList<>(target.effects());
    active.removeIf((EffectInstance effect) -> !effect.icon());
    active.sort(Comparator.comparing(EffectInstance::type));
    List<InsightEffect> effects = new ArrayList<>(Math.min(active.size(), FrameLimits.MAX_INSIGHT_EFFECTS));
    for (EffectInstance effect : active) {
      if (effects.size() == FrameLimits.MAX_INSIGHT_EFFECTS) {
        break;
      }
      effects.add(new InsightEffect(effect.type().toString(), effect.amplifier(),
          effect.isInfinite() ? InsightEffect.INFINITE : Math.max(0, effect.durationTicks()), harmful.test(effect.type())));
    }
    return effects;
  }

  private static void add(List<InsightStat> stats, LivingRef target, InsightStatKind kind, Id attribute) {
    double value = attribute(target, attribute, Double.NaN);
    if (Double.isFinite(value)) {
      stats.add(new InsightStat(kind, value));
    }
  }

  private static double attribute(LivingRef target, Id attribute, double missing) {
    if (!target.hasAttribute(attribute)) {
      return missing;
    }
    double value = target.attributeValue(attribute);
    return Double.isFinite(value) ? value : missing;
  }

  private static String name(LivingRef target) {
    if (target instanceof PlayerRef player) {
      return player.name();
    }
    return nonNull(target.customName());
  }

  private static double finite(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  private static String nonNull(String value) {
    return value == null ? "" : value;
  }
}
