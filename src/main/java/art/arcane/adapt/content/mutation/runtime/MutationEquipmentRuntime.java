package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.mutation.MutationClaim;
import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationEventClaims;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

final class MutationEquipmentRuntime {
  private final MutationRuntimeAccess access;
  private final MutationRuntimeStore store;
  private final MutationItemIdentity items;

  MutationEquipmentRuntime(MutationRuntimeAccess access, MutationRuntimeStore store, MutationItemIdentity items) {
    this.access = access;
    this.store = store;
    this.items = items;
  }

  void onCraft(CraftItemEvent event) {
    if (!access.enabled() || !(event.getWhoClicked() instanceof Player player) || event.isCancelled()) {
      return;
    }
    ItemStack result = event.getCurrentItem();
    if (items.isDurable(result)) {
      items.markCrafted(result, player.getUniqueId());
    }
  }

  void onPrepareAnvil(PrepareAnvilEvent event) {
    ItemStack source = event.getInventory().getFirstItem();
    ItemStack ingredient = event.getInventory().getSecondItem();
    ItemStack result = event.getResult();
    if (source == null || result == null) {
      return;
    }
    items.copyIdentity(source, result);
    if (event.getView().getPlayer() instanceof Player player
        && items.wasCraftedBy(source, player.getUniqueId())
        && isLegitimateRepair(source, ingredient, result)) {
      clearLegitimatelyRepairedState(source, result);
    }
    event.setResult(result);
  }

  void onPrepareGrindstone(PrepareGrindstoneEvent event) {
    ItemStack upper = event.getInventory().getUpperItem();
    ItemStack lower = event.getInventory().getLowerItem();
    ItemStack source = repairIdentitySource(upper, lower);
    ItemStack result = event.getResult();
    if (source == null || result == null) {
      return;
    }
    items.copyIdentity(source, result);
    ItemStack ingredient = source == upper ? lower : upper;
    if (event.getView().getPlayer() instanceof Player player
        && items.wasCraftedBy(source, player.getUniqueId())
        && isLegitimateRepair(source, ingredient, result)) {
      clearLegitimatelyRepairedState(source, result);
    }
    event.setResult(result);
  }

  void onPrepareSmithing(PrepareSmithingEvent event) {
    ItemStack source = event.getInventory().getInputEquipment();
    ItemStack result = event.getResult();
    Player player = event.getView().getPlayer() instanceof Player viewer ? viewer : null;
    if (source == null || result == null || !hasBoundUpgradeIdentity(player, source)) {
      return;
    }
    if (!items.copyIdentityForUpgrade(source, result)) {
      event.setResult(null);
      return;
    }
    event.setResult(result);
  }

  void onItemMend(PlayerItemMendEvent event) {
    ItemStack item = event.getItem();
    if (event.isCancelled()
        || event.getRepairAmount() <= 0
        || !items.isCracked(item)
        || !items.wasCraftedBy(item, event.getPlayer().getUniqueId())) {
      return;
    }
    items.clearCracked(item);
  }

  void validateTemperbound(Player player) {
    if (player == null || !access.expressed(player, MutationType.TEMPERBOUND)) {
      return;
    }
    PlayerMutationData durable = access.durable(player);
    if (durable == null || durable.getTemperboundBondId().isBlank()) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    if (access.perfect(player)) {
      synchronized (runtime) {
        runtime.temperboundRejectionUntil = 0L;
      }
      return;
    }
    if (hasCompleteAttunedSet(player, durable)) {
      return;
    }
    boolean newlyRejected = false;
    synchronized (runtime) {
      if (runtime.temperboundRejectionUntil <= System.currentTimeMillis()) {
        runtime.temperboundRejectionUntil = System.currentTimeMillis()
            + access.config().getTemperbound().getRejectionMillis();
        newlyRejected = true;
      }
    }
    if (newlyRejected) {
      access.tell(player, MutationType.TEMPERBOUND, Particle.WAX_OFF, 8);
    }
  }

  void onItemDamage(PlayerItemDamageEvent event, MutationEventClaims claims) {
    if (event.isCancelled() || event.getDamage() <= 0) {
      return;
    }
    Player player = event.getPlayer();
    validateTemperbound(player);
    for (MutationType type : access.ordered(player)) {
      if (claims.isClaimed(MutationClaim.ITEM_PRESERVATION)) {
        return;
      }
      switch (type) {
        case TEMPERBOUND -> preserveTemperbound(event, claims);
        case MASTERWORK_BOND -> preserveMasterwork(event, claims);
        case DEEPBLOOD -> preserveDeepblood(event, claims);
        default -> {
        }
      }
    }
  }

  boolean blocksHeldItem(Player player) {
    if (player == null) {
      return false;
    }
    return blocksItem(player.getInventory().getItemInMainHand());
  }

  boolean blocksItem(ItemStack item) {
    if (!access.enabled()) {
      return false;
    }
    return blocksEquipmentState(
        true,
        items.isBrokenMasterwork(item),
        items.isCrackedArmor(item)
    );
  }

  void onIncomingDamage(EntityDamageEvent event) {
    if (!access.enabled()
        || event == null
        || event.isCancelled()
        || event.getDamage() <= 0D
        || !(event.getEntity() instanceof Player player)
        || !event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
      return;
    }
    double crackedArmor = crackedAttribute(player, Attribute.ARMOR);
    double crackedToughness = crackedAttribute(player, Attribute.ARMOR_TOUGHNESS);
    if (crackedArmor <= 0D && crackedToughness <= 0D) {
      return;
    }
    AttributeInstance armorAttribute = player.getAttribute(Attribute.ARMOR);
    AttributeInstance toughnessAttribute = player.getAttribute(Attribute.ARMOR_TOUGHNESS);
    double armor = armorAttribute == null ? 0D : armorAttribute.getValue();
    double toughness = toughnessAttribute == null ? 0D : toughnessAttribute.getValue();
    double damage = event.getDamage();
    double target = damageAfterArmor(
        damage,
        Math.max(0D, armor - crackedArmor),
        Math.max(0D, toughness - crackedToughness)
    );
    event.setDamage(requiredBaseDamage(target, armor, toughness));
  }

  static boolean blocksEquipmentState(boolean enabled, boolean brokenMasterwork, boolean crackedArmor) {
    return enabled && (brokenMasterwork || crackedArmor);
  }

  boolean attuneCurrentArmor(Player player) {
    if (!canUseBookshelfControl(player, MutationType.TEMPERBOUND)) {
      return false;
    }
    PlayerMutationData durable = access.durable(player);
    if (durable == null || !durable.getTemperboundBondId().isBlank()) {
      return false;
    }
    List<ItemStack> armor = armor(player);
    if (armor.size() != 4
        || armor.stream().anyMatch(item -> !items.isDurable(item))
        || armor.stream().anyMatch(items::isCracked)
        || armor.stream().anyMatch(item -> !items.wasCraftedBy(item, player.getUniqueId()))) {
      return false;
    }
    String bondId = UUID.randomUUID().toString();
    ArrayList<String> pieceIds = new ArrayList<>(4);
    for (ItemStack piece : armor) {
      String pieceId = UUID.randomUUID().toString();
      while (pieceIds.contains(pieceId)) {
        pieceId = UUID.randomUUID().toString();
      }
      items.attuneArmorPiece(piece, bondId, pieceId);
      pieceIds.add(pieceId);
    }
    durable.setTemperboundBondId(bondId);
    durable.setTemperboundPieceIds(pieceIds);
    access.save(player);
    access.tell(player, MutationType.TEMPERBOUND, Particle.ENCHANT, 12);
    return true;
  }

  boolean dissolveTemperbound(Player player) {
    if (!canUseBookshelfControl(player, MutationType.TEMPERBOUND)) {
      return false;
    }
    PlayerMutationData durable = access.durable(player);
    if (durable == null || durable.getTemperboundBondId().isBlank()) {
      return false;
    }
    durable.setTemperboundBondId("");
    durable.setTemperboundPieceIds(new ArrayList<>());
    access.save(player);
    return true;
  }

  boolean bindMasterwork(Player player) {
    if (!canUseBookshelfControl(player, MutationType.MASTERWORK_BOND)) {
      return false;
    }
    PlayerMutationData durable = access.durable(player);
    ItemStack held = player.getInventory().getItemInMainHand();
    if (durable == null
        || !durable.getMasterworkItemId().isBlank()
        || durable.getMasterworkAbandonReadyAt() > System.currentTimeMillis()
        || !items.isTool(held)
        || !items.wasCraftedBy(held, player.getUniqueId())) {
      return false;
    }
    String itemId = items.ensureItemId(held);
    if (!hasUniqueItemIdentity(player, held, itemId)) {
      return false;
    }
    items.markMasterwork(held, itemId);
    durable.setMasterworkItemId(itemId);
    access.save(player);
    access.tell(player, MutationType.MASTERWORK_BOND, Particle.ENCHANT, 12);
    return true;
  }

  boolean abandonMasterwork(Player player) {
    if (!canUseBookshelfControl(player, MutationType.MASTERWORK_BOND)) {
      return false;
    }
    PlayerMutationData durable = access.durable(player);
    long now = System.currentTimeMillis();
    if (durable == null || durable.getMasterworkItemId().isBlank() || durable.getMasterworkAbandonReadyAt() > now) {
      return false;
    }
    durable.setMasterworkItemId("");
    durable.setMasterworkAbandonReadyAt(now + access.config().getMasterworkBond().getAbandonCooldownMillis());
    access.save(player);
    return true;
  }

  boolean bindDeepbloodTool(Player player) {
    if (!canUseBookshelfControl(player, MutationType.DEEPBLOOD)) {
      return false;
    }
    ItemStack held = player.getInventory().getItemInMainHand();
    PlayerMutationData durable = access.durable(player);
    if (durable == null || !items.isTool(held)) {
      return false;
    }
    String itemId = items.ensureItemId(held);
    if (!hasUniqueItemIdentity(player, held, itemId)) {
      return false;
    }
    durable.setDeepbloodToolId(itemId);
    access.save(player);
    access.tell(player, MutationType.DEEPBLOOD, Particle.CRIMSON_SPORE, 10);
    return true;
  }

  double currentIchor(Player player) {
    PlayerMutationData durable = access.durable(player);
    if (durable == null) {
      return 0D;
    }
    normalizeIchor(player, durable, System.currentTimeMillis());
    return durable.getDeepbloodIchor();
  }

  void addIchor(Player player, double amount) {
    PlayerMutationData durable = access.durable(player);
    if (durable == null || amount <= 0D) {
      return;
    }
    long now = System.currentTimeMillis();
    normalizeIchor(player, durable, now);
    MutationConfig.Deepblood config = access.config().getDeepblood();
    durable.setDeepbloodIchor(MutationRuntimePolicy.clamp(
        durable.getDeepbloodIchor() + amount,
        0D,
        config.getMaximumIchor()
    ));
    durable.setDeepbloodUpdatedAt(now);
    access.tell(player, MutationType.DEEPBLOOD, Particle.CRIMSON_SPORE, 3);
  }

  boolean consumeIchor(Player player, double amount) {
    PlayerMutationData durable = access.durable(player);
    if (durable == null || amount < 0D) {
      return false;
    }
    long now = System.currentTimeMillis();
    normalizeIchor(player, durable, now);
    if (durable.getDeepbloodIchor() < amount) {
      return false;
    }
    durable.setDeepbloodIchor(durable.getDeepbloodIchor() - amount);
    durable.setDeepbloodUpdatedAt(now);
    return true;
  }

  void onDepthTransition(Player player, int previousBlockY, int currentBlockY) {
    if (player == null || !access.expressed(player, MutationType.DEEPBLOOD)) {
      return;
    }
    MutationConfig.Deepblood config = access.config().getDeepblood();
    boolean wasAbove = previousBlockY > config.getMaximumDepthY();
    boolean isAbove = currentBlockY > config.getMaximumDepthY();
    if (wasAbove == isAbove) {
      return;
    }
    PlayerMutationData durable = access.durable(player);
    if (durable == null) {
      return;
    }
    normalizeIchor(durable, System.currentTimeMillis(), wasAbove, config);
    access.save(player);
  }

  private void preserveTemperbound(PlayerItemDamageEvent event, MutationEventClaims claims) {
    Player player = event.getPlayer();
    PlayerMutationData durable = access.durable(player);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long rejectionUntil;
    synchronized (runtime) {
      rejectionUntil = runtime.temperboundRejectionUntil;
    }
    if (durable == null
        || durable.getTemperboundBondId().isBlank()
        || (rejectionUntil > System.currentTimeMillis() && !access.perfect(player))
        || !hasCompleteAttunedSet(player, durable)
        || !isCanonicalTemperboundItem(player, event.getItem(), durable)
        || !claims.tryClaim(MutationClaim.ITEM_PRESERVATION)) {
      return;
    }
    event.setCancelled(true);
    distributeDamage(armor(player), event.getDamage());
    access.tell(player, MutationType.TEMPERBOUND, Particle.WAX_ON, 5);
  }

  private void preserveMasterwork(PlayerItemDamageEvent event, MutationEventClaims claims) {
    PlayerMutationData durable = access.durable(event.getPlayer());
    ItemStack item = event.getItem();
    if (durable == null
        || !items.isMasterwork(item, durable.getMasterworkItemId())
        || !hasUniqueItemIdentity(event.getPlayer(), item, durable.getMasterworkItemId())
        || !wouldBreak(item, event.getDamage())
        || !claims.tryClaim(MutationClaim.ITEM_PRESERVATION)) {
      return;
    }
    event.setDamage(Math.max(0, maximumDamage(item) - damage(item) - 1));
    items.setBroken(item, true);
    access.tell(event.getPlayer(), MutationType.MASTERWORK_BOND, Particle.CRIT, 8);
  }

  private void preserveDeepblood(PlayerItemDamageEvent event, MutationEventClaims claims) {
    Player player = event.getPlayer();
    PlayerMutationData durable = access.durable(player);
    ItemStack item = event.getItem();
    MutationConfig.Deepblood config = access.config().getDeepblood();
    if (durable == null
        || !durable.getDeepbloodToolId().equals(items.itemId(item))
        || !hasUniqueItemIdentity(player, item, durable.getDeepbloodToolId())
        || !items.isTool(item)
        || !wouldBreak(item, event.getDamage())
        || currentIchor(player) < config.getToolPreservationCost()
        || !claims.tryClaim(MutationClaim.ITEM_PRESERVATION)
        || !consumeIchor(player, config.getToolPreservationCost())) {
      return;
    }
    event.setDamage(Math.max(0, maximumDamage(item) - damage(item) - 1));
    access.tell(player, MutationType.DEEPBLOOD, Particle.CRIMSON_SPORE, 10);
  }

  private void distributeDamage(List<ItemStack> armor, int totalDamage) {
    ArrayList<ItemStack> candidates = new ArrayList<>(armor);
    candidates.removeIf(item -> items.isCracked(item) || maximumDamage(item) - damage(item) <= 1);
    int[] currentDamage = new int[candidates.size()];
    int[] maximumDamage = new int[candidates.size()];
    for (int index = 0; index < candidates.size(); index++) {
      currentDamage[index] = damage(candidates.get(index));
      maximumDamage[index] = maximumDamage(candidates.get(index));
    }
    int[] targets = balancedDamageTargets(currentDamage, maximumDamage, Math.max(0L, (long) totalDamage));
    for (int index = 0; index < candidates.size(); index++) {
      ItemStack candidate = candidates.get(index);
      setDamage(candidate, targets[index]);
      if (maximumDamage(candidate) - damage(candidate) <= 1) {
        items.setCracked(candidate, true);
      }
    }
  }

  static int[] balancedDamageTargets(int[] currentDamage, int[] maximumDamage, long totalDamage) {
    if (currentDamage == null || maximumDamage == null || currentDamage.length != maximumDamage.length) {
      return new int[0];
    }
    int[] targets = currentDamage.clone();
    boolean[] active = new boolean[targets.length];
    for (int index = 0; index < targets.length; index++) {
      int cap = Math.max(0, maximumDamage[index] - 1);
      targets[index] = Math.max(0, Math.min(targets[index], cap));
      active[index] = targets[index] < cap;
    }
    long remaining = Math.max(0L, totalDamage);
    while (remaining > 0L) {
      int minimumDamage = Integer.MAX_VALUE;
      int activeCount = 0;
      for (int index = 0; index < targets.length; index++) {
        if (active[index]) {
          minimumDamage = Math.min(minimumDamage, targets[index]);
          activeCount++;
        }
      }
      if (activeCount == 0) {
        break;
      }

      int leastCount = 0;
      int targetDamage = Integer.MAX_VALUE;
      for (int index = 0; index < targets.length; index++) {
        if (!active[index]) {
          continue;
        }
        if (targets[index] == minimumDamage) {
          leastCount++;
          targetDamage = Math.min(targetDamage, maximumDamage[index] - 1);
        } else {
          targetDamage = Math.min(targetDamage, targets[index]);
        }
      }

      long required = Math.max(0L, (long) targetDamage - minimumDamage) * leastCount;
      if (required > remaining) {
        long shared = remaining / leastCount;
        int extra = (int) (remaining % leastCount);
        for (int index = 0; index < targets.length; index++) {
          if (active[index] && targets[index] == minimumDamage) {
            long applied = shared;
            if (extra > 0) {
              applied++;
              extra--;
            }
            targets[index] = safeDamageAdd(targets[index], applied);
          }
        }
        remaining = 0L;
      } else if (required > 0L) {
        for (int index = 0; index < targets.length; index++) {
          if (active[index] && targets[index] == minimumDamage) {
            targets[index] = targetDamage;
          }
        }
        remaining -= required;
      }
      for (int index = 0; index < targets.length; index++) {
        if (active[index] && targets[index] >= maximumDamage[index] - 1) {
          active[index] = false;
        }
      }
    }
    return targets;
  }

  private boolean hasCompleteAttunedSet(Player player, PlayerMutationData durable) {
    List<ItemStack> armor = armor(player);
    if (armor.size() != 4 || durable.getTemperboundPieceIds().size() != 4) {
      return false;
    }
    ArrayList<String> actualPieceIds = new ArrayList<>(4);
    for (ItemStack piece : armor) {
      actualPieceIds.add(items.attunedPieceId(piece, durable.getTemperboundBondId()));
    }
    return matchesTemperboundSlots(actualPieceIds, durable.getTemperboundPieceIds());
  }

  static boolean matchesTemperboundSlots(List<String> actualPieceIds, List<String> expectedPieceIds) {
    return actualPieceIds != null
        && expectedPieceIds != null
        && actualPieceIds.size() == 4
        && expectedPieceIds.size() == 4
        && new HashSet<>(expectedPieceIds).size() == 4
        && actualPieceIds.equals(expectedPieceIds);
  }

  private List<ItemStack> armor(Player player) {
    EntityEquipment equipment = player.getEquipment();
    if (equipment == null) {
      return List.of();
    }
    ArrayList<ItemStack> armor = new ArrayList<>(4);
    addArmorPiece(armor, equipment.getBoots());
    addArmorPiece(armor, equipment.getLeggings());
    addArmorPiece(armor, equipment.getChestplate());
    addArmorPiece(armor, equipment.getHelmet());
    return armor;
  }

  private void addArmorPiece(List<ItemStack> armor, ItemStack item) {
    if (item != null && !item.getType().isAir()) {
      armor.add(item);
    }
  }

  private boolean canUseBookshelfControl(Player player, MutationType type) {
    MutationManager manager = access.manager();
    return player != null
        && manager != null
        && manager.hasValidBookshelfAccess(player)
        && access.expressed(player, type);
  }

  private void normalizeIchor(Player player, PlayerMutationData durable, long now) {
    MutationConfig.Deepblood config = access.config().getDeepblood();
    normalizeIchor(
        durable,
        now,
        player.getLocation().getBlockY() > config.getMaximumDepthY(),
        config
    );
  }

  private void normalizeIchor(
      PlayerMutationData durable,
      long now,
      boolean elapsedAboveGround,
      MutationConfig.Deepblood config
  ) {
    long updatedAt = durable.getDeepbloodUpdatedAt();
    if (updatedAt > 0L) {
      durable.setDeepbloodIchor(ichorAfterDepthInterval(
          durable.getDeepbloodIchor(),
          Math.max(0L, now - updatedAt),
          config.getAboveGroundHalfLifeMillis(),
          elapsedAboveGround
      ));
    }
    durable.setDeepbloodUpdatedAt(now);
  }

  private ItemStack repairIdentitySource(ItemStack upper, ItemStack lower) {
    boolean upperIdentity = items.hasEquipmentIdentity(upper);
    boolean lowerIdentity = items.hasEquipmentIdentity(lower);
    if (upperIdentity == lowerIdentity) {
      return null;
    }
    return upperIdentity ? upper : lower;
  }

  private boolean isLegitimateRepair(ItemStack source, ItemStack ingredient, ItemStack result) {
    return source != null
        && ingredient != null
        && !ingredient.getType().isAir()
        && result != null
        && source.getType() == result.getType()
        && items.isDurable(source)
        && items.isDurable(result)
        && damage(result) < damage(source);
  }

  private void clearLegitimatelyRepairedState(ItemStack source, ItemStack result) {
    if (items.isCracked(source)) {
      items.clearCracked(result);
    }
    if (items.isBrokenMasterwork(source)) {
      items.clearBroken(result);
    }
  }

  private double crackedAttribute(Player player, Attribute attribute) {
    EntityEquipment equipment = player.getEquipment();
    if (equipment == null) {
      return 0D;
    }
    return crackedAttribute(equipment.getBoots(), EquipmentSlot.FEET, attribute)
        + crackedAttribute(equipment.getLeggings(), EquipmentSlot.LEGS, attribute)
        + crackedAttribute(equipment.getChestplate(), EquipmentSlot.CHEST, attribute)
        + crackedAttribute(equipment.getHelmet(), EquipmentSlot.HEAD, attribute);
  }

  private double crackedAttribute(ItemStack item, EquipmentSlot slot, Attribute attribute) {
    if (!items.isCrackedArmor(item) || items.hasSuppressedCrackedAttributes(item)) {
      return 0D;
    }
    ItemMeta meta = item.getItemMeta();
    Collection<AttributeModifier> modifiers;
    if (meta.hasAttributeModifiers() && meta.getAttributeModifiers(slot) != null) {
      modifiers = meta.getAttributeModifiers(slot).get(attribute);
    } else {
      modifiers = item.getType().getDefaultAttributeModifiers(slot).get(attribute);
    }
    double add = 0D;
    double scalar = 0D;
    double multiply = 1D;
    for (AttributeModifier modifier : modifiers) {
      switch (modifier.getOperation()) {
        case ADD_NUMBER -> add += modifier.getAmount();
        case ADD_SCALAR -> scalar += modifier.getAmount();
        case MULTIPLY_SCALAR_1 -> multiply *= 1D + modifier.getAmount();
      }
    }
    return Math.max(0D, (add + (add * scalar)) * multiply);
  }

  static double damageAfterArmor(double damage, double armor, double toughness) {
    if (damage <= 0D) {
      return 0D;
    }
    double boundedArmor = Math.max(0D, armor);
    double boundedToughness = Math.max(0D, toughness);
    double effectiveArmor = Math.min(
        20D,
        Math.max(boundedArmor / 5D, boundedArmor - (damage / (2D + (boundedToughness / 4D))))
    );
    return damage * (1D - (effectiveArmor / 25D));
  }

  static double ichorAfterDepthInterval(
      double ichor,
      long elapsedMillis,
      long halfLifeMillis,
      boolean elapsedAboveGround
  ) {
    return elapsedAboveGround
        ? MutationRuntimePolicy.decayHalfLife(ichor, elapsedMillis, halfLifeMillis)
        : ichor;
  }

  static double requiredBaseDamage(double targetAfterArmor, double armor, double toughness) {
    if (targetAfterArmor <= 0D) {
      return 0D;
    }
    double low = targetAfterArmor;
    double high = (targetAfterArmor * 5D) + 1D;
    for (int i = 0; i < 24; i++) {
      double midpoint = (low + high) * 0.5D;
      if (damageAfterArmor(midpoint, armor, toughness) < targetAfterArmor) {
        low = midpoint;
      } else {
        high = midpoint;
      }
    }
    return high;
  }

  private boolean wouldBreak(ItemStack item, int incomingDamage) {
    return items.isDurable(item)
        && (long) damage(item) + Math.max(0L, (long) incomingDamage) >= maximumDamage(item);
  }

  private boolean hasBoundUpgradeIdentity(Player player, ItemStack source) {
    if (items.hasEquipmentIdentity(source)) {
      return true;
    }
    PlayerMutationData durable = player == null ? null : access.durable(player);
    return durable != null
        && !durable.getDeepbloodToolId().isBlank()
        && durable.getDeepbloodToolId().equals(items.itemId(source));
  }

  private boolean isCanonicalTemperboundItem(Player player, ItemStack eventItem, PlayerMutationData durable) {
    if (player == null || eventItem == null || durable == null || durable.getTemperboundPieceIds().size() != 4) {
      return false;
    }
    EquipmentSlot slot = eventItem.getType().getEquipmentSlot();
    int index = switch (slot) {
      case FEET -> 0;
      case LEGS -> 1;
      case CHEST -> 2;
      case HEAD -> 3;
      default -> -1;
    };
    if (index < 0) {
      return false;
    }
    String expectedId = durable.getTemperboundPieceIds().get(index);
    EntityEquipment equipment = player.getEquipment();
    ItemStack equipped = equipmentItem(equipment, slot);
    return equipped != null
        && !expectedId.isBlank()
        && expectedId.equals(items.attunedPieceId(eventItem, durable.getTemperboundBondId()))
        && expectedId.equals(items.attunedPieceId(equipped, durable.getTemperboundBondId()))
        && eventItem.getType() == equipped.getType()
        && hasUniqueItemIdentity(player, eventItem, expectedId);
  }

  private ItemStack equipmentItem(EntityEquipment equipment, EquipmentSlot slot) {
    if (equipment == null) {
      return null;
    }
    return switch (slot) {
      case FEET -> equipment.getBoots();
      case LEGS -> equipment.getLeggings();
      case CHEST -> equipment.getChestplate();
      case HEAD -> equipment.getHelmet();
      default -> null;
    };
  }

  private boolean hasUniqueItemIdentity(Player player, ItemStack item, String expectedId) {
    if (player == null
        || item == null
        || expectedId == null
        || expectedId.isBlank()
        || !expectedId.equals(items.itemId(item))) {
      return false;
    }
    int matches = 0;
    for (ItemStack inventoryItem : player.getInventory().getContents()) {
      if (inventoryItem == null || !expectedId.equals(items.itemId(inventoryItem))) {
        continue;
      }
      matches += Math.max(1, inventoryItem.getAmount());
      if (matches > 1) {
        return false;
      }
    }
    return matches == 1;
  }

  private static int safeDamageAdd(int damage, long addition) {
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) damage + addition));
  }

  private int damage(ItemStack item) {
    if (item == null || !(item.getItemMeta() instanceof Damageable damageable)) {
      return 0;
    }
    return damageable.getDamage();
  }

  private int maximumDamage(ItemStack item) {
    if (item == null) {
      return 0;
    }
    if (item.getItemMeta() instanceof Damageable damageable && damageable.hasMaxDamage()) {
      return damageable.getMaxDamage();
    }
    return item.getType().getMaxDurability();
  }

  private void setDamage(ItemStack item, int value) {
    if (item == null || !(item.getItemMeta() instanceof Damageable damageable)) {
      return;
    }
    damageable.setDamage(Math.max(0, Math.min(maximumDamage(item) - 1, value)));
    item.setItemMeta(damageable);
  }
}
