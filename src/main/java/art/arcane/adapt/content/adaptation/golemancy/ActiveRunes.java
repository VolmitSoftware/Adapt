package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneType;

import java.util.List;
import java.util.Map;

record ActiveRunes(Map<GolemRuneType, Integer> slotted, boolean tierUp, double tierThreeBoost) {
  static final ActiveRunes NONE = new ActiveRunes(Map.of(), false, 0D);

  static ActiveRunes slotted(Map<GolemRuneType, Integer> slotted) {
    return new ActiveRunes(slotted, false, 0D);
  }

  boolean isEmpty() {
    return slotted.isEmpty();
  }

  double value(List<Double> valuesByTier, GolemRuneType aspect) {
    GolemRuneEffects.Tier tier = effective(aspect);
    return tier.scale(RunicAugmentsRules.atTier(valuesByTier, tier.tier(), 0D));
  }

  int count(List<Integer> valuesByTier, GolemRuneType aspect) {
    GolemRuneEffects.Tier tier = effective(aspect);
    return tier.scale(RunicAugmentsRules.atTier(valuesByTier, tier.tier(), 0));
  }

  boolean flag(List<Boolean> valuesByTier, GolemRuneType aspect) {
    return RunicAugmentsRules.atTier(valuesByTier, slotted.getOrDefault(aspect, 0), false);
  }

  private GolemRuneEffects.Tier effective(GolemRuneType aspect) {
    return GolemRuneEffects.effectiveTier(slotted.getOrDefault(aspect, 0), tierUp, tierThreeBoost);
  }
}
