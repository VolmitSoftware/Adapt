package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.internal.AbilityApiPolicy;
import art.arcane.adapt.api.ability.internal.AbilityApiRuntime;
import art.arcane.adapt.api.ability.internal.AbilityCostGateway;
import art.arcane.adapt.api.ability.internal.AbilityDenial;
import art.arcane.adapt.api.adaptation.AbilityApiPort;
import art.arcane.adapt.api.adaptation.AbilityCostRequest;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.event.AdaptAdaptationUseEvent;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class BukkitAbilityApiPort implements AbilityApiPort {
  private static final Map<UUID, AbilityDenial> DENIALS = PlayerStateRegistry.newPlayerMap();

  static void start(Plugin plugin) {
    AbilityApiRuntime.install(plugin, BukkitAbilityApiPort::policy, DENIALS);
  }

  static void stop() {
    AbilityApiRuntime.uninstall();
    DENIALS.clear();
  }

  @Override
  public boolean useEventAllows(AdaptPlayer player, Adaptation<?> adaptation) {
    AdaptAdaptationUseEvent event = new AdaptAdaptationUseEvent(!Bukkit.isPrimaryThread(), player, adaptation);
    Bukkit.getServer().getPluginManager().callEvent(event);
    return !event.isCancelled();
  }

  @Override
  public boolean hasUsePolicies(String abilityId, String skillId) {
    AbilityApiRuntime runtime = AbilityApiRuntime.instance();
    return runtime != null && runtime.usePolicy().hasPoliciesFor(abilityId, skillId);
  }

  @Override
  public boolean allowedByUsePolicy(String abilityId, String skillId, int level, PlayerRef player) {
    AbilityApiRuntime runtime = AbilityApiRuntime.instance();
    Player bukkitPlayer = BukkitRefs.player(player);
    if (runtime == null || bukkitPlayer == null) {
      return true;
    }
    return runtime.usePolicy().evaluate(AbilityContext.check(abilityId, skillId, level, bukkitPlayer)).allowed();
  }

  @Override
  public boolean costsActive() {
    AbilityCostGateway gateway = gateway();
    return gateway != null && !gateway.idle();
  }

  @Override
  public AbilityCharge charge(AbilityCostRequest request, AbilityDefaultCost defaultCost) {
    return requireGateway().charge(costContext(request), defaultCost);
  }

  @Override
  public AbilityCharge chargeDeferred(AbilityCostRequest request, AbilityDefaultCost defaultCost) {
    return requireGateway().chargeDeferred(costContext(request), defaultCost);
  }

  @Override
  public boolean settle(UUID activationId) {
    AbilityCostGateway gateway = gateway();
    return gateway != null && gateway.settle(activationId);
  }

  @Override
  public boolean refund(UUID activationId, AbilityRefundReason reason) {
    AbilityCostGateway gateway = gateway();
    return gateway != null && gateway.refund(activationId, reason);
  }

  @Override
  public AbilityDenial lastDenial(UUID playerId) {
    AbilityApiRuntime runtime = AbilityApiRuntime.instance();
    return runtime == null ? null : runtime.usePolicy().lastDenial(playerId);
  }

  private static AbilityCostGateway gateway() {
    AbilityApiRuntime runtime = AbilityApiRuntime.instance();
    return runtime == null ? null : runtime.cost();
  }

  private static AbilityCostGateway requireGateway() {
    AbilityCostGateway gateway = gateway();
    if (gateway == null) {
      throw new IllegalStateException("No ability cost gateway is installed");
    }
    return gateway;
  }

  private static AbilityCostContext costContext(AbilityCostRequest request) {
    Player player = BukkitRefs.player(request.player());
    Location origin = BukkitRefs.location(request.origin());
    if (origin == null && player != null) {
      origin = player.getLocation();
    }
    AbilityContext ability = AbilityContext.activate(request.abilityId(), request.skillId(), request.level(), player,
        origin);
    Optional<ItemStack> unit = request.unit() == null ? Optional.empty() : Optional.of(BukkitRefs.item(request.unit()));
    return new AbilityCostContext(ability, request.costKey(), request.kind(), unit, Math.max(0, request.amount()));
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
