package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.mutation.MutationClaim;
import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationEventClaims;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.api.potion.AdaptBrewCompleteEvent;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MutationFormulaRuntime {
  private static final String CRAFTING = "crafting";
  private static final String BREWING = "brewing";
  private static final String ENCHANTING = "enchanting";
  private static final long PROTECTION_AUTHORIZATION_MILLIS = 1_000L;

  private final MutationRuntimeAccess access;
  private final MutationRuntimeStore store;
  private final MutationCombatRuntime combat;

  MutationFormulaRuntime(MutationRuntimeAccess access, MutationRuntimeStore store, MutationCombatRuntime combat) {
    this.access = access;
    this.store = store;
    this.combat = combat;
  }

  void onCraft(CraftItemEvent event) {
    if (event.isCancelled() || !(event.getWhoClicked() instanceof Player player) || !isNontrivialCraft(event)) {
      return;
    }
    signal(player, CRAFTING);
  }

  void onBrew(AdaptBrewCompleteEvent event) {
    Player player = Bukkit.getPlayer(event.getBrewerId());
    if (player != null && event.getBrewedPotions() > 0) {
      J.runEntity(player, () -> signal(player, BREWING));
    }
  }

  void onEnchant(EnchantItemEvent event) {
    if (!event.isCancelled() && event.getExpLevelCost() > 0 && !event.getEnchantsToAdd().isEmpty()) {
      signal(event.getEnchanter(), ENCHANTING);
    }
  }

  boolean echoUtility(
      Player actor,
      LivingEntity target,
      MutationUtilityTag tag,
      double strength,
      MutationEventClaims claims
  ) {
    if (actor == null || target == null || tag == null || tag == MutationUtilityTag.NONE
        || (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(actor) || !J.isOwnedByCurrentRegion(target)))
        || !access.expressed(actor, MutationType.RESONANT_FORMULA)
        || (target instanceof Player && !access.pvpEnabled(MutationType.RESONANT_FORMULA))
        || !access.protection().canAffect(actor, target)
        || claims == null) {
      return false;
    }
    PlayerMutationData durable = access.durable(actor);
    if (durable == null) {
      return false;
    }
    MutationConfig.ResonantFormula config = access.config().getResonantFormula();
    if (pruneSigils(durable, System.currentTimeMillis(), config.getSigilLifetimeMillis())) {
      access.save(actor);
    }
    if (!isPrimed(durable)) {
      return false;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(actor.getUniqueId());
    long generation;
    long loadoutGeneration;
    synchronized (runtime) {
      if (runtime.formula.echoing) {
        return false;
      }
    }
    if (!claims.tryClaim(MutationClaim.UTILITY_ECHO)
        || !runtime.tryClaimUtilityEcho(System.currentTimeMillis())) {
      return false;
    }
    synchronized (runtime) {
      if (runtime.formula.echoing) {
        return false;
      }
      runtime.formula.echoing = true;
      runtime.formula.generation++;
      generation = runtime.formula.generation;
      loadoutGeneration = runtime.loadoutGeneration;
    }
    LinkedHashMap<String, Long> spentSigils = new LinkedHashMap<>(durable.getFormulaSigils());
    durable.setFormulaSigils(new LinkedHashMap<>());
    access.save(actor);
    Location actorOrigin = actor.getLocation().clone();
    UUID actorId = actor.getUniqueId();
    FormulaEchoRequest request = new FormulaEchoRequest(
        actorId,
        target.getUniqueId(),
        target.getWorld().getUID(),
        target instanceof Player,
        actorOrigin,
        tag,
        strength * config.getEchoFactor(),
        generation,
        loadoutGeneration
    );
    boolean scheduled = J.runEntity(target, () -> recheckFormulaTarget(target, request), config.getEchoDelayTicks());
    if (!scheduled) {
      synchronized (runtime) {
        if (runtime.formula.generation == generation) {
          runtime.formula.echoing = false;
          runtime.formula.generation++;
        }
      }
      durable.setFormulaSigils(spentSigils);
      access.save(actor);
      return false;
    }
    return true;
  }

  private void recheckFormulaTarget(LivingEntity target, FormulaEchoRequest request) {
    if (!validFormulaTarget(target, request)
        || (request.playerTarget() && !access.pvpEnabled(MutationType.RESONANT_FORMULA))) {
      abortFormulaEcho(request);
      return;
    }
    Location targetLocation = target.getLocation().clone();
    Player actor = access.onlinePlayer(request.actorId());
    if (actor == null || !J.runEntity(actor, () -> authorizeFormulaEcho(actor, target, targetLocation, request))) {
      abortFormulaEcho(request);
    }
  }

  private void authorizeFormulaEcho(
      Player actor,
      LivingEntity target,
      Location targetLocation,
      FormulaEchoRequest request
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.actorId());
    if (runtime == null
        || !actor.isOnline()
        || !access.expressed(actor, MutationType.RESONANT_FORMULA)
        || (request.playerTarget() && !access.pvpEnabled(MutationType.RESONANT_FORMULA))
        || !access.protection().canAffectAt(actor, targetLocation, request.playerTarget())) {
      abortFormulaEcho(request);
      return;
    }
    synchronized (runtime) {
      if (runtime.formula.generation != request.generation()
          || runtime.loadoutGeneration != request.loadoutGeneration()
          || !runtime.formula.echoing) {
        return;
      }
    }
    FormulaEchoAuthorization authorization = new FormulaEchoAuthorization(
        request,
        targetLocation.clone(),
        System.currentTimeMillis()
    );
    if (!J.runEntity(target, () -> applyFormulaEcho(actor, target, authorization))) {
      abortFormulaEcho(request);
    }
  }

  private void applyFormulaEcho(Player actor, LivingEntity target, FormulaEchoAuthorization authorization) {
    FormulaEchoRequest request = authorization.request();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.actorId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.formula.generation != request.generation()
          || runtime.loadoutGeneration != request.loadoutGeneration()
          || !runtime.formula.echoing) {
        return;
      }
    }
    if (!validFormulaTarget(target, request)
        || !sameBlock(target.getLocation(), authorization.targetLocation())
        || System.currentTimeMillis() - authorization.authorizedAt() > PROTECTION_AUTHORIZATION_MILLIS
        || (request.playerTarget() && !access.pvpEnabled(MutationType.RESONANT_FORMULA))) {
      abortFormulaEcho(request);
      return;
    }
    synchronized (runtime) {
      if (runtime.formula.generation != request.generation()
          || runtime.loadoutGeneration != request.loadoutGeneration()
          || !runtime.formula.echoing) {
        return;
      }
      runtime.formula.echoing = false;
    }
    combat.applyUtilityOwned(request.actorOrigin(), target, request.tag(), request.factor());
    J.runEntity(actor, () -> access.tell(actor, MutationType.RESONANT_FORMULA, Particle.ENCHANT, 10));
  }

  private void abortFormulaEcho(FormulaEchoRequest request) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.actorId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.formula.generation == request.generation()
          && runtime.loadoutGeneration == request.loadoutGeneration()) {
        runtime.formula.echoing = false;
      }
    }
  }

  private boolean validFormulaTarget(LivingEntity target, FormulaEchoRequest request) {
    return target != null
        && target.isValid()
        && !target.isDead()
        && target.getUniqueId().equals(request.targetId())
        && target.getWorld().getUID().equals(request.targetWorldId());
  }

  void onDeath(Player player) {
    PlayerMutationData durable = access.durable(player);
    if (durable != null && !durable.getFormulaSigils().isEmpty()) {
      durable.setFormulaSigils(new LinkedHashMap<>());
      access.save(player);
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      runtime.formula.clearTransient();
    }
  }

  void cleanup(Player player) {
    if (player == null) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      runtime.formula.clearTransient();
    }
  }

  private void signal(Player player, String discipline) {
    if (!access.expressed(player, MutationType.RESONANT_FORMULA)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long now = System.currentTimeMillis();
    synchronized (runtime) {
      if (runtime.formula.collapseLockUntil > now) {
        return;
      }
    }
    PlayerMutationData durable = access.durable(player);
    if (durable == null) {
      return;
    }
    MutationConfig.ResonantFormula config = access.config().getResonantFormula();
    pruneSigils(durable, now, config.getSigilLifetimeMillis());
    Map<String, Long> sigils = new LinkedHashMap<>(durable.getFormulaSigils());
    if (sigils.containsKey(discipline) && !access.perfect(player)) {
      sigils.clear();
      durable.setFormulaSigils(sigils);
      synchronized (runtime) {
        runtime.formula.collapseLockUntil = now + config.getCollapseLockMillis();
        runtime.formula.generation++;
      }
      removeOldestBeneficial(player);
      access.save(player);
      access.tell(player, MutationType.RESONANT_FORMULA, Particle.SMOKE, 8);
      return;
    }
    sigils.put(discipline, now);
    durable.setFormulaSigils(sigils);
    access.save(player);
    access.tell(player, MutationType.RESONANT_FORMULA, Particle.ENCHANT, isPrimed(durable) ? 12 : 4);
  }

  private boolean pruneSigils(PlayerMutationData durable, long now, long lifetime) {
    LinkedHashMap<String, Long> active = new LinkedHashMap<>();
    for (Map.Entry<String, Long> entry : durable.getFormulaSigils().entrySet()) {
      if (entry.getValue() != null && now - entry.getValue() <= lifetime) {
        active.put(entry.getKey(), entry.getValue());
      }
    }
    boolean changed = active.size() != durable.getFormulaSigils().size();
    durable.setFormulaSigils(active);
    return changed;
  }

  private boolean isPrimed(PlayerMutationData durable) {
    Map<String, Long> sigils = durable.getFormulaSigils();
    return sigils.containsKey(CRAFTING) && sigils.containsKey(BREWING) && sigils.containsKey(ENCHANTING);
  }

  private void removeOldestBeneficial(Player player) {
    PotionEffect candidate = player.getActivePotionEffects().stream()
        .filter(effect -> effect.getType().getCategory() == PotionEffectTypeCategory.BENEFICIAL)
        .min(Comparator.comparingInt(PotionEffect::getDuration))
        .orElse(null);
    if (candidate != null) {
      player.removePotionEffect(candidate.getType());
    }
  }

  private boolean isNontrivialCraft(CraftItemEvent event) {
    ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
    if (result != null && result.getType().getMaxDurability() > 0) {
      return true;
    }
    int ingredients = 0;
    for (ItemStack item : event.getInventory().getMatrix()) {
      if (item != null && !item.getType().isAir()) {
        ingredients++;
      }
    }
    return ingredients >= 3;
  }

  static boolean sameBlock(Location first, Location second) {
    return first != null
        && second != null
        && first.getWorld() != null
        && first.getWorld() == second.getWorld()
        && first.getBlockX() == second.getBlockX()
        && first.getBlockY() == second.getBlockY()
        && first.getBlockZ() == second.getBlockZ();
  }

  private record FormulaEchoRequest(
      UUID actorId,
      UUID targetId,
      UUID targetWorldId,
      boolean playerTarget,
      Location actorOrigin,
      MutationUtilityTag tag,
      double factor,
      long generation,
      long loadoutGeneration
  ) {
  }

  private record FormulaEchoAuthorization(
      FormulaEchoRequest request,
      Location targetLocation,
      long authorizedAt
  ) {
  }
}
