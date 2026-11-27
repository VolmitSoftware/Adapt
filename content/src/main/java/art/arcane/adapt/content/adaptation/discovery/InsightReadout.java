package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightEffect;
import art.arcane.adapt.api.insight.InsightKind;
import art.arcane.adapt.api.insight.InsightMark;
import art.arcane.adapt.api.insight.InsightStat;
import art.arcane.adapt.api.insight.InsightView;
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
  /** The tag of a creature Taming's Stable Hand biased. */
  public static final Id STABLE_HAND = Id.adapt("stable_hand");

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
    InsightView.Builder view = InsightView.builder(target.entityId(), target.type(), kind(target))
        .name(name(target))
        .health(finite(target.health()), finite(target.maxHealth()))
        .absorption(finite(target.absorption()))
        .armor(Math.max(0D, attribute(target, Attributes.ARMOR, 0D)))
        .bond(bond, ownerName)
        .stats(stats(target, tier));
    if (stableHand) {
      view.tag(STABLE_HAND);
    }
    if (tier.effects()) {
      view.effects(effects(target, harmful));
    }
    return view.build();
  }

  public static InsightMark mark(LivingRef target) {
    double max = target.maxHealth();
    double fraction = max > 0D && Double.isFinite(max) ? Math.max(0D, Math.min(1D, target.health() / max)) : 0D;
    return InsightMark.of(target.entityId(), kind(target), fraction);
  }

  public static InsightKind kind(LivingRef target) {
    if (target instanceof PlayerRef) {
      return InsightKind.PLAYER;
    }
    return target.has(EntityTrait.ENEMY) ? InsightKind.HOSTILE : InsightKind.PASSIVE;
  }

  static List<InsightStat> stats(LivingRef target, InsightTier tier) {
    List<InsightStat> stats = new ArrayList<>(6);
    add(stats, target, Attributes.ATTACK_DAMAGE);
    if (tier.movement()) {
      add(stats, target, Attributes.MOVEMENT_SPEED);
      add(stats, target, Attributes.JUMP_STRENGTH);
    }
    if (tier.defenses()) {
      add(stats, target, Attributes.ARMOR_TOUGHNESS);
      add(stats, target, Attributes.KNOCKBACK_RESISTANCE);
      add(stats, target, Attributes.FOLLOW_RANGE);
    }
    return stats;
  }

  private static List<InsightEffect> effects(LivingRef target, Predicate<Id> harmful) {
    List<EffectInstance> active = new ArrayList<>(target.effects());
    active.removeIf((EffectInstance effect) -> !effect.icon());
    active.sort(Comparator.comparing(EffectInstance::type));
    List<InsightEffect> effects = new ArrayList<>(Math.min(active.size(), InsightView.MAX_EFFECTS));
    for (EffectInstance effect : active) {
      if (effects.size() == InsightView.MAX_EFFECTS) {
        break;
      }
      int duration = effect.isInfinite() ? InsightEffect.INFINITE : Math.max(0, effect.durationTicks());
      effects.add(harmful.test(effect.type()) ? InsightEffect.harmful(effect.type(), effect.amplifier(), duration)
          : InsightEffect.beneficial(effect.type(), effect.amplifier(), duration));
    }
    return effects;
  }

  private static void add(List<InsightStat> stats, LivingRef target, Id attribute) {
    double value = attribute(target, attribute, Double.NaN);
    if (Double.isFinite(value)) {
      stats.add(InsightStat.of(attribute, value));
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
    return target instanceof PlayerRef player ? player.name() : "";
  }

  private static double finite(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  private static String nonNull(String value) {
    return value == null ? "" : value;
  }
}
