/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public class RiftEnderTaglock extends SimpleAdaptation<RiftEnderTaglock.Config> {
  private static final double PEARL_TELEPORT_DAMAGE = 5.0;
  private static final int MAX_PENDING_TELEPORTS = 2048;
  private static final int MAX_TARGET_SNAPSHOT_ATTEMPTS = 2;
  private static final long OPERATION_TIMEOUT_MILLIS = 60_000L;
  private static final long TELEPORT_COMPLETION_TIMEOUT_MILLIS = 300_000L;
  private static final long MIN_SUPPRESSION_MILLIS = 50L;
  private static final long MAX_SUPPRESSION_MILLIS = 2000L;
  static final String TARGET_KEY_NAME = "rift_taglock_target_uuid";
  /**
   * Taglocks ride on an ender pearl, so the throw cooldown lives in its own
   * group. A taglock on cooldown must never gray out or block plain pearls.
   */
  public static final NamespacedKey COOLDOWN_GROUP = ItemCooldowns.groupKey("item_rift_ender_taglock");
  private final ItemCooldowns throwCooldown = ItemCooldowns.forGroup(COOLDOWN_GROUP);
  private final NamespacedKey targetKey;
  private final Map<UUID, Long> suppressPearlTeleportUntil = playerState();
  private final TeleportAdmission pendingTeleports = new TeleportAdmission(MAX_PENDING_TELEPORTS);
  private volatile boolean acceptingTeleports = true;

  public RiftEnderTaglock() {
    super("rift-ender-taglock");
    registerConfiguration(Config.class);
    setIcon(Material.ENDER_PEARL);
    setInterval(1200);
    targetKey = new NamespacedKey(Adapt.instance, TARGET_KEY_NAME);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_taglock_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_taglock_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_taglock_100", "rift.ender-taglock.entities-tagged", 100, 400);
    registerMilestone("challenge_rift_taglock_500", "rift.ender-taglock.taglocked-teleports", 500, 1000);
  }

  @Override
  public void unregister() {
    acceptingTeleports = false;
    pendingTeleports.clear();
    suppressPearlTeleportUntil.clear();
    super.unregister();
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    suppressPearlTeleportUntil.entrySet().removeIf(i -> i.getValue() <= now);
    pendingTeleports.purgeExpired(now);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.ENDER_TAGLOCK_LORE1));
    if (level >= 2) {
      v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.ENDER_TAGLOCK_LORE2));
    }
    if (level >= 3) {
      v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.ENDER_TAGLOCK_LORE3));
    }
    statLore(v, C.YELLOW, "* ", Form.duration(getThrowCooldownTicks(level) * 50D, 1), 4);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) {
      return;
    }

    int level = getActiveDamageLevel(p, target);
    if (level <= 0 || !p.isSneaking()) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isItem(hand) || hand.getType() != Material.ENDER_PEARL || BoundEnderPearl.isBindableItem(hand)) {
      return;
    }

    if (!isTaggable(target, level)) {
      return;
    }

    if (!tagIntoPearl(p, hand, target)) {
      return;
    }

    e.setCancelled(true);
    fx(p, FxPriority.COMBAT).sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.4f);
    timeline(target)
        .duration(8)
        .priority(FxPriority.COMBAT)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.PORTAL, 1.2 - (1.1 * progress), 8, 1.0);
          if (tick == 7) {
            fx.particle(Particle.REVERSE_PORTAL, 6, 0, 1.0, 0, 0.2, 0.03);
          }
          if (tick % 3 == 0) {
            fx.sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, (float) (1.0 + (progress * 0.6)));
          }
        })
        .start();
    addStat(p, "rift.ender-taglock.entities-tagged", 1);
    xp(p, getConfig().xpOnTag);
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    EquipmentSlot slot = e.getHand();
    if (slot == null) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack hand = slot == EquipmentSlot.HAND
        ? p.getInventory().getItemInMainHand()
        : p.getInventory().getItemInOffHand();

    UUID target = getTaggedTarget(hand);
    if (target == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      e.setCancelled(true);
      return;
    }

    if (e.useItemInHand() == Event.Result.DENY) {
      return;
    }

    if (e.getClickedBlock() != null && e.useInteractedBlock() == Event.Result.DENY) {
      e.setCancelled(true);
      return;
    }

    e.setCancelled(true);
    upgradeLegacyTaglock(p, slot, hand);
    long throwCooldownMillis = getThrowCooldownTicks(level) * 50L;
    if (!throwCooldown.isReady(p, throwCooldownMillis)) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.15)
          .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.55f, 0.6f);
      Adapt.verbose("Ender Taglock throw declined for " + p.getUniqueId() + ": "
          + throwCooldown.remaining(p, throwCooldownMillis) + "ms cooldown left.");
      return;
    }

    if (!decrementTaggedPearl(p, slot, hand)) {
      return;
    }

    String targetId = target.toString();
    // The target key must land before the projectile spawns; ProjectileLaunchEvent fires during the
    // spawn and other pearl adaptations claim anything that still looks like a plain pearl there.
    p.launchProjectile(
        EnderPearl.class,
        null,
        (EnderPearl pearl) -> pearl.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, targetId)
    );
    throwCooldown.mark(p, throwCooldownMillis);
    Vector dir = p.getLocation().getDirection();
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .trail(Particle.REVERSE_PORTAL, dir.getX(), dir.getY(), dir.getZ(), 1.5, 8)
        .chord(Sound.ENTITY_ENDER_EYE_LAUNCH, 0.65f, 1.25f, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4f, 1.4f);
    xp(p, getConfig().xpOnThrow);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
      return;
    }

    UUID id = e.getPlayer().getUniqueId();
    Long armedUntil = suppressPearlTeleportUntil.remove(id);
    if (!shouldSuppressPearlTeleport(armedUntil, System.currentTimeMillis())) {
      return;
    }

    e.setCancelled(true);
    e.getPlayer().setFallDistance(0f);
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player thrower)) {
      return;
    }

    if (pearl.isDead() || !pearl.isValid()) {
      return;
    }

    String raw = pearl.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
    UUID targetId = parseTargetId(raw);
    if (targetId == null) {
      return;
    }

    if (vanillaPearlTeleportStillRuns(e.isCancelled(), e.getHitBlock() != null)) {
      suppressPearlTeleportUntil.put(
          thrower.getUniqueId(),
          System.currentTimeMillis() + getSuppressPearlTeleportWindowMillis()
      );
    }

    if (e.isCancelled()) {
      return;
    }

    Location destination = resolveDestination(e);
    J.runEntity(thrower, () -> prepareThrowerOwned(thrower, targetId, destination));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    suppressPearlTeleportUntil.remove(playerId);
    pendingTeleports.cancel(playerId);
  }

  /**
   * A taglocked pearl only keeps the vanilla thrower teleport once the hit resolves, so suppression is
   * armed there rather than at launch. Paper still runs the pearl's block impact when the hit event is
   * cancelled, so a cancelled block hit teleports the thrower and must stay suppressed anyway.
   */
  static boolean vanillaPearlTeleportStillRuns(boolean hitCancelled, boolean blockHit) {
    return !hitCancelled || blockHit;
  }

  static boolean shouldSuppressPearlTeleport(Long armedUntil, long now) {
    return armedUntil != null && armedUntil > now;
  }

  private void prepareThrowerOwned(Player thrower, UUID targetId, Location destination) {
    if (!acceptingTeleports) {
      return;
    }

    UUID throwerId = thrower.getUniqueId();
    int level = getActiveLevel(thrower);
    if (!thrower.isOnline() || level <= 0 || throwerId.equals(targetId)) {
      return;
    }

    Entity entity = Bukkit.getEntity(targetId);
    if (!(entity instanceof LivingEntity target)) {
      return;
    }

    long token = pendingTeleports.admit(throwerId, System.currentTimeMillis(), OPERATION_TIMEOUT_MILLIS);
    if (token < 0L) {
      return;
    }

    TaglockOperation operation = new TaglockOperation(
        thrower,
        throwerId,
        target,
        targetId,
        destination.clone(),
        level,
        getConfig().damageSender,
        token
    );
    if (!J.runEntity(target, () -> prepareTargetOwned(operation, 0))) {
      pendingTeleports.complete(throwerId, token);
    }
  }

  private void prepareTargetOwned(TaglockOperation operation, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    LivingEntity target = operation.target();
    if (!isUsableTargetOwned(operation)) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    TargetSnapshot snapshot = new TargetSnapshot(target.getLocation().clone(), target instanceof Player);
    if (!J.runEntity(operation.thrower(), () -> validatePolicyOwned(operation, snapshot, attempt))) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
    }
  }

  private void validatePolicyOwned(TaglockOperation operation, TargetSnapshot snapshot, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    Player thrower = operation.thrower();
    if (!thrower.isOnline() || getActiveLevel(thrower) <= 0) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    boolean allowed = snapshot.playerTarget()
        ? canPVP(thrower, snapshot.location().clone()) && canPVP(thrower, operation.destination().clone())
        : canPVE(thrower, snapshot.location().clone()) && canPVE(thrower, operation.destination().clone());
    if (!allowed) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    loadChunkAsync(operation.destination().clone(), ignored -> scheduleTargetApply(operation, snapshot, attempt));
  }

  private void scheduleTargetApply(TaglockOperation operation, TargetSnapshot snapshot, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    if (!J.runEntity(operation.target(), () -> applyTargetOwned(operation, snapshot, attempt))) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
    }
  }

  private void applyTargetOwned(TaglockOperation operation, TargetSnapshot snapshot, int attempt) {
    if (!isCurrent(operation)) {
      return;
    }

    if (!isUsableTargetOwned(operation)) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }

    LivingEntity target = operation.target();
    Location origin = target.getLocation().clone();
    if (!snapshot.sameBlock(origin)) {
      if (attempt + 1 < MAX_TARGET_SNAPSHOT_ATTEMPTS) {
        prepareTargetOwned(operation, attempt + 1);
      } else {
        pendingTeleports.complete(operation.throwerId(), operation.token());
      }
      return;
    }

    if (!pendingTeleports.markTeleporting(
        operation.throwerId(),
        operation.token(),
        System.currentTimeMillis(),
        TELEPORT_COMPLETION_TIMEOUT_MILLIS
    )) {
      return;
    }

    CompletableFuture<Boolean> completion;
    try {
      completion = PaperCompat.teleportAsync(target, operation.destination().clone());
    } catch (RuntimeException error) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      reportTeleportFailure(operation.targetId(), error);
      return;
    }

    if (completion == null) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      return;
    }
    completion.whenComplete((success, error) -> finishTeleport(operation, origin, success, error));
  }

  private void finishTeleport(TaglockOperation operation, Location origin, Boolean success, Throwable error) {
    if (error != null) {
      pendingTeleports.complete(operation.throwerId(), operation.token());
      reportTeleportFailure(operation.targetId(), error);
      return;
    }

    if (!Boolean.TRUE.equals(success)
        || !pendingTeleports.complete(operation.throwerId(), operation.token())) {
      return;
    }

    J.runEntity(operation.target(), () -> finishTargetOwned(operation, origin));
    J.runEntity(operation.thrower(), () -> rewardThrowerOwned(operation));
  }

  private void finishTargetOwned(TaglockOperation operation, Location origin) {
    LivingEntity target = operation.target();
    if (!operation.damageSender() && target.isValid() && !target.isDead()) {
      if (target instanceof Player player) {
        applyPlayerDamage(player, PEARL_TELEPORT_DAMAGE);
      } else {
        target.damage(PEARL_TELEPORT_DAMAGE);
      }
    }

    double shockRadius = getShockRadius(operation.level());
    fx(origin, FxPriority.COMBAT)
        .particle(Particle.REVERSE_PORTAL, 10, 0, 1.0, 0, 0.3, 0.05);
    timeline(operation.destination().clone())
        .duration(5)
        .priority(FxPriority.COMBAT)
        .cullRadius(28)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.3 + ((shockRadius - 0.3) * progress), 16, 0.1);
          if (tick == 0) {
            fx.chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.75f, 1.35f, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.9f);
          }
        })
        .start();
    if (target instanceof Player victim && victim.isOnline()) {
      fx(victim, FxPriority.COMBAT).sound(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.75f, 1.9f);
    }
  }

  private void rewardThrowerOwned(TaglockOperation operation) {
    Player thrower = operation.thrower();
    if (!thrower.isOnline()) {
      return;
    }

    if (operation.damageSender() && thrower.isValid() && !thrower.isDead()) {
      applyPlayerDamage(thrower, PEARL_TELEPORT_DAMAGE);
    }
    addStat(thrower, "rift.ender-taglock.taglocked-teleports", 1);
    xp(thrower, getConfig().xpOnTeleport);
  }

  private boolean isUsableTargetOwned(TaglockOperation operation) {
    LivingEntity target = operation.target();
    if (!target.isValid() || target.isDead() || !operation.targetId().equals(target.getUniqueId())) {
      return false;
    }
    if (isProtectedFriendly(null, target)) {
      return false;
    }
    return !(target instanceof Tameable tameable)
        || !tameable.isTamed()
        || !operation.throwerId().equals(PaperCompat.tamedOwnerId(tameable));
  }

  private boolean isCurrent(TaglockOperation operation) {
    return pendingTeleports.isCurrent(
        operation.throwerId(),
        operation.token(),
        System.currentTimeMillis()
    );
  }

  private double getShockRadius(int level) {
    return level >= 3 ? 2.0 : (level == 2 ? 1.6 : 1.4);
  }

  private void reportTeleportFailure(UUID targetId, Throwable error) {
    Adapt.warn("Failed to complete an Ender Taglock teleport for target " + targetId + ".");
    Adapt.error(error);
  }

  private Location resolveDestination(ProjectileHitEvent e) {
    Location base = e.getEntity().getLocation().clone();
    if (e.getHitBlock() != null && e.getHitBlockFace() != null) {
      Vector n = e.getHitBlockFace().getDirection().clone().normalize();
      base = e.getHitBlock().getLocation().add(0.5, 0.5, 0.5).add(n.multiply(0.6));
    }

    return base.add(0, 0.05, 0);
  }

  private boolean decrementTaggedPearl(Player p, EquipmentSlot slot, ItemStack stack) {
    if (!isItem(stack)) {
      return false;
    }

    return payItemCost(p, "consume", new ItemStack(stack.getType()), 1, () -> {
      if (stack.getAmount() <= 1) {
        if (slot == EquipmentSlot.HAND) {
          p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
          p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }

        return true;
      }

      stack.setAmount(stack.getAmount() - 1);
      if (slot == EquipmentSlot.HAND) {
        p.getInventory().setItemInMainHand(stack);
      } else {
        p.getInventory().setItemInOffHand(stack);
      }

      return true;
    });
  }

  private boolean tagIntoPearl(Player p, ItemStack hand, LivingEntity target) {
    ItemStack tagged = makeTaggedPearl(target);
    AtomicBoolean releasedMainHandSlot = new AtomicBoolean();

    if (!payItemCost(p, "bind", new ItemStack(hand.getType()), 1, () -> {
      if (hand.getAmount() <= 1) {
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        releasedMainHandSlot.set(true);
        return true;
      }

      hand.setAmount(hand.getAmount() - 1);
      p.getInventory().setItemInMainHand(hand);
      return true;
    })) {
      return false;
    }

    if (releasedMainHandSlot.get()) {
      p.getInventory().setItemInMainHand(tagged);
    } else {
      p.getInventory().addItem(tagged).values()
          .forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }
    return true;
  }

  private ItemStack makeTaggedPearl(LivingEntity target) {
    ItemStack item = new ItemStack(Material.ENDER_PEARL, 1);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
    ItemCooldowns.stampGroup(meta, COOLDOWN_GROUP);

    meta.setDisplayName(C.LIGHT_PURPLE + AdaptLanguage.text(RiftMessages.ENDER_TAGLOCK_ITEM_NAME));
    List<String> lore = new ArrayList<>();
    lore.add(C.GRAY + AdaptLanguage.text(
        RiftMessages.ENDER_TAGLOCK_TARGET,
        untrusted("target", C.WHITE + getTargetName(target))
    ));
    lore.add(C.DARK_GRAY + AdaptLanguage.text(RiftMessages.ENDER_TAGLOCK_THROW));
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }

  /**
   * Taglocks minted before the cooldown group existed still carry the plain
   * ender pearl group, so the sweep would land on vanilla pearls instead. Stamp
   * them the first time an owner actually uses one.
   */
  private void upgradeLegacyTaglock(Player p, EquipmentSlot slot, ItemStack hand) {
    if (!ItemCooldowns.stampGroup(hand, COOLDOWN_GROUP)) {
      return;
    }

    if (slot == EquipmentSlot.HAND) {
      p.getInventory().setItemInMainHand(hand);
      return;
    }

    p.getInventory().setItemInOffHand(hand);
  }

  private UUID getTaggedTarget(ItemStack item) {
    if (!isItem(item) || item.getType() != Material.ENDER_PEARL || item.getItemMeta() == null) {
      return null;
    }

    String raw = item.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
    return parseTargetId(raw);
  }

  private UUID parseTargetId(String raw) {
    if (raw == null || raw.length() != 36) {
      return null;
    }

    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String getTargetName(LivingEntity target) {
    if (target instanceof Player player) {
      return player.getName();
    }

    if (target.getCustomName() != null && !target.getCustomName().isEmpty()) {
      return target.getCustomName();
    }

    return Form.capitalizeWords(target.getType().name().toLowerCase().replaceAll("\\Q_\\E", " "));
  }

  private boolean isTaggable(LivingEntity target, int level) {
    if (target instanceof Player) {
      return level >= 3;
    }

    if (level >= 3) {
      return true;
    }

    if (level == 2) {
      return isLevel1Taggable(target) || target instanceof Villager || isLargeTarget(target);
    }

    return isLevel1Taggable(target);
  }

  private boolean isLevel1Taggable(LivingEntity target) {
    if (!(target instanceof Mob)) {
      return false;
    }

    if (target instanceof Villager) {
      return false;
    }

    if (isLargeTarget(target)) {
      return false;
    }

    return target instanceof Animals
        || target instanceof Monster
        || target instanceof WaterMob
        || target instanceof Ambient
        || target instanceof Slime;
  }

  private boolean isLargeTarget(LivingEntity target) {
    BoundingBox box = target.getBoundingBox();
    double width = Math.max(box.getWidthX(), box.getWidthZ());
    double height = box.getHeight();
    return width >= getConfig().largeWidthThreshold || height >= getConfig().largeHeightThreshold;
  }

  private int getThrowCooldownTicks(int level) {
    return Math.max(4, (int) Math.round(getConfig().throwCooldownTicksBase - (getLevelPercent(level) * getConfig().throwCooldownTicksFactor)));
  }

  private long getSuppressPearlTeleportWindowMillis() {
    return Math.max(MIN_SUPPRESSION_MILLIS, Math.min(MAX_SUPPRESSION_MILLIS, getConfig().suppressPearlTeleportWindowMillis));
  }

  @ConfigDescription("Tag entities into ender pearls and throw those pearls to reposition the tagged target.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Throw Cooldown Ticks Base for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwCooldownTicksBase = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Throw Cooldown Ticks Factor for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwCooldownTicksFactor = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How long the thrower's vanilla pearl teleport stays suppressed after a taglocked pearl lands.", impact = "Backstop only; the suppression is armed on impact and consumed by the teleport that follows it in the same tick, so this only needs to survive that hand-off. Clamped to 50-2000ms.")
    long suppressPearlTeleportWindowMillis = 250L;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Large Width Threshold for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double largeWidthThreshold = 1.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Large Height Threshold for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double largeHeightThreshold = 2.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP On Tag for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnTag = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP On Throw for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnThrow = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls who takes the ender pearl teleport damage when a taglocked pearl lands.", impact = "True damages the thrower like a normal pearl; false damages the taglocked target instead.")
    boolean damageSender = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP On Teleport for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnTeleport = 14;

    public Config() {
      baseCost = 7;
      costFactor = 0.95;
      maxLevel = 3;
      initialCost = 7;
    }
  }

  private record TaglockOperation(
      Player thrower,
      UUID throwerId,
      LivingEntity target,
      UUID targetId,
      Location destination,
      int level,
      boolean damageSender,
      long token
  ) {
  }

  private record TargetSnapshot(Location location, boolean playerTarget) {
    private boolean sameBlock(Location current) {
      return location.getWorld() == current.getWorld()
          && location.getBlockX() == current.getBlockX()
          && location.getBlockY() == current.getBlockY()
          && location.getBlockZ() == current.getBlockZ();
    }
  }

  static final class TeleportAdmission {
    private final int capacity;
    private final Map<UUID, PendingTeleport> pending = new HashMap<>();
    private long nextToken;

    TeleportAdmission(int capacity) {
      this.capacity = Math.max(1, capacity);
    }

    synchronized long admit(UUID ownerId, long now, long timeoutMillis) {
      PendingTeleport existing = pending.get(ownerId);
      if (existing != null && existing.expiresAt() > now) {
        return -1L;
      }
      if (existing != null) {
        pending.remove(ownerId);
      }
      if (pending.size() >= capacity) {
        purgeExpired(now);
        if (pending.size() >= capacity) {
          return -1L;
        }
      }

      long token = ++nextToken;
      pending.put(ownerId, new PendingTeleport(token, deadline(now, timeoutMillis), false));
      return token;
    }

    synchronized boolean isCurrent(UUID ownerId, long token, long now) {
      PendingTeleport operation = pending.get(ownerId);
      if (operation == null || operation.token() != token) {
        return false;
      }
      if (operation.expiresAt() <= now) {
        pending.remove(ownerId);
        return false;
      }
      return true;
    }

    synchronized boolean markTeleporting(UUID ownerId, long token, long now, long timeoutMillis) {
      if (!isCurrent(ownerId, token, now)) {
        return false;
      }

      PendingTeleport operation = pending.get(ownerId);
      if (operation.teleporting()) {
        return false;
      }
      pending.put(ownerId, new PendingTeleport(token, deadline(now, timeoutMillis), true));
      return true;
    }

    synchronized boolean complete(UUID ownerId, long token) {
      PendingTeleport operation = pending.get(ownerId);
      if (operation == null || operation.token() != token) {
        return false;
      }
      pending.remove(ownerId);
      return true;
    }

    synchronized void cancel(UUID ownerId) {
      PendingTeleport operation = pending.get(ownerId);
      if (operation != null && !operation.teleporting()) {
        pending.remove(ownerId);
      }
    }

    synchronized int purgeExpired(long now) {
      int before = pending.size();
      pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
      return before - pending.size();
    }

    synchronized int size() {
      return pending.size();
    }

    synchronized void clear() {
      pending.clear();
    }

    private static long deadline(long now, long timeoutMillis) {
      long timeout = Math.max(1L, timeoutMillis);
      if (now > Long.MAX_VALUE - timeout) {
        return Long.MAX_VALUE;
      }
      return now + timeout;
    }

    private record PendingTeleport(long token, long expiresAt, boolean teleporting) {
    }
  }
}
