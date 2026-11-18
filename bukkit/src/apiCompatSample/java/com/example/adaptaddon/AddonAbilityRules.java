package com.example.adaptaddon;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityQuote;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.bukkit.inventory.ItemStack;

public final class AddonAbilityRules {
  private AddonAbilityRules() {
  }

  public static final class UsePolicy implements AbilityUsePolicy {
    @Override
    public AbilityUseDecision evaluate(AbilityContext context) {
      if (context.player() == null || context.abilityId().startsWith("addon-blocked")) {
        return AbilityUseDecision.deny("blocked by addon");
      }
      return AbilityUseDecision.allow();
    }

    @Override
    public String providerId() {
      return "adapt-addon";
    }
  }

  public static final class CostProvider implements AbilityCostProvider {
    @Override
    public AbilityQuote quote(AbilityCostContext context) {
      ItemStack item = context.defaultItem().orElse(null);
      return item == null ? AbilityQuote.pass() : AbilityQuote.waived("addon pays " + context.defaultAmount());
    }
  }
}
