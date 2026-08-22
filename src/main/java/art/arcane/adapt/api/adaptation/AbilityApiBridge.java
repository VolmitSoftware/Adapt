package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.internal.AbilityApiPolicy;
import art.arcane.adapt.api.ability.internal.AbilityApiRuntime;
import art.arcane.adapt.api.ability.internal.AbilityCostGateway;
import art.arcane.adapt.api.ability.internal.AbilityDenial;
import art.arcane.adapt.api.ability.internal.AbilityUsePolicyGateway;
import art.arcane.adapt.api.world.AdaptPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AbilityApiBridge {
  private static final Map<UUID, AbilityDenial> DENIALS = PlayerStateRegistry.newPlayerMap();

  private AbilityApiBridge() {
  }

  public static void install(Plugin plugin) {
    AbilityApiRuntime.install(plugin, AbilityApiBridge::policy, DENIALS);
  }

  public static void uninstall() {
    AbilityApiRuntime.uninstall();
    DENIALS.clear();
  }

  public static AbilityDenial lastDenial(UUID playerId) {
    AbilityApiRuntime runtime = AbilityApiRuntime.instance();
    return runtime == null ? null : runtime.usePolicy().lastDenial(playerId);
  }

  static boolean allowedByUsePolicy(Adaptation<?> adaptation, AdaptPlayer player) {
    AbilityApiRuntime runtime = AbilityApiRuntime.instance();

    if (runtime == null || adaptation == null || player == null) {
      return true;
    }

    Player bukkitPlayer = player.getPlayer();

    if (bukkitPlayer == null) {
      return true;
    }

    String abilityId = adaptation.getName();
    String skillId = adaptation.getSkill().getName();
    AbilityUsePolicyGateway gateway = runtime.usePolicy();

    if (!gateway.hasPoliciesFor(abilityId, skillId)) {
      return true;
    }

    return gateway.evaluate(AbilityContext.check(abilityId, skillId,
        AdaptationRuntimeGuards.getLevel(adaptation, player), bukkitPlayer)).allowed();
  }

  static boolean charge(Adaptation<?> adaptation, Player player, String costKey, AbilityCostKind kind, ItemStack unit,
                        int amount, AbilityDefaultCost defaultCost) {
    AbilityCostGateway gateway = gateway();

    if (gateway == null || gateway.idle()) {
      return defaultCost == null || defaultCost.take();
    }

    return gateway.charge(costContext(adaptation, player, costKey, kind, unit, amount), defaultCost).allowed();
  }

  static AbilityCharge chargeDeferred(Adaptation<?> adaptation, Player player, String costKey, AbilityCostKind kind,
                                      ItemStack unit, int amount, AbilityDefaultCost defaultCost) {
    AbilityCostGateway gateway = gateway();

    if (gateway == null || gateway.idle()) {
      boolean taken = defaultCost == null || defaultCost.take();
      return new AbilityCharge(UUID.randomUUID(),
          taken ? AbilityOutcome.ALLOWED_DEFAULT : AbilityOutcome.DENIED_INSUFFICIENT, false, "", "",
          List.of());
    }

    return gateway.chargeDeferred(costContext(adaptation, player, costKey, kind, unit, amount), defaultCost);
  }

  static boolean settle(UUID activationId) {
    AbilityCostGateway gateway = gateway();
    return gateway != null && gateway.settle(activationId);
  }

  static boolean refund(UUID activationId, AbilityRefundReason reason) {
    AbilityCostGateway gateway = gateway();
    return gateway != null && gateway.refund(activationId, reason);
  }

  private static AbilityCostGateway gateway() {
    AbilityApiRuntime runtime = AbilityApiRuntime.instance();
    return runtime == null ? null : runtime.cost();
  }

  private static AbilityCostContext costContext(Adaptation<?> adaptation, Player player, String costKey,
                                                AbilityCostKind kind, ItemStack unit, int amount) {
    Location origin = player.getLocation();
    AbilityContext ability = AbilityContext.activate(adaptation.getName(), adaptation.getSkill().getName(),
        AdaptationRuntimeGuards.getLevel(adaptation, player), player, origin);
    return new AbilityCostContext(ability, AdaptationRuntimeGuards.adaptationRewardKey(adaptation, costKey), kind,
        Optional.ofNullable(unit), Math.max(0, amount));
  }

  private static AbilityApiPolicy policy() {
    AdaptConfig config = AdaptConfig.get();

    if (config == null) {
      return AbilityApiPolicy.defaults();
    }

    AdaptConfig.AbilityApi api = config.getAbilityApi();

    if (api == null) {
      return AbilityApiPolicy.defaults();
    }

    return AbilityApiPolicy.of(api.isEnabled(), api.getUsePolicyFailureMode(), api.getCostProviderFailureMode(),
        api.getProviderFaultLimit(), api.getSlowProviderMillis(), api.getDenyMessageThrottleMillis());
  }
}
