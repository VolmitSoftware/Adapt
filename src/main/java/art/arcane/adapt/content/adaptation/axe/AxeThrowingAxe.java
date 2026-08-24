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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AxeMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AxeThrowingAxe extends SimpleAdaptation<AxeThrowingAxe.Config> {
  private static final int MAX_IN_FLIGHT = 512;
  private static final String RECOVERY_KEY_PREFIX = "throwing_axe_recovery_";
  private static final long TICK_NANOS = 50_000_000L;
  private static final long BLOCK_BREAK_SWING_GUARD_TICKS = 1L;
  private static final long SHUTDOWN_RECOVERY_MILLIS = 2_000L;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, ThrownAxe> inFlight = new ConcurrentHashMap<>();
  private final Set<AxeReplacementTicket> pendingReplacements = ConcurrentHashMap.newKeySet();
  private final Map<NamespacedKey, PendingAxeDrop> pendingDrops = new ConcurrentHashMap<>();
  private final AxeRecoveryJournal recoveryJournal = AxeRecoveryJournal.createDefault();
  private final ThreadLocal<Deque<ThrowDamageAttempt>> damageAttempts = new ThreadLocal<>();
  private final AtomicBoolean closing = new AtomicBoolean();
  private final Object lifecycleLock = new Object();
  private final AxeBlockBreakSwingGuard blockBreakSwingGuard =
      new AxeBlockBreakSwingGuard(BLOCK_BREAK_SWING_GUARD_TICKS);
  private final String recoveryNamespace;

  public AxeThrowingAxe() {
    super("axe-throwing-axe");
    registerConfiguration(Config.class);
    recoveryNamespace = new NamespacedKey(Adapt.instance, "throwing_axe_recovery").getNamespace();
    setIcon(Material.IRON_AXE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_AXE)
        .key("challenge_axe_throw_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_AXE)
            .key("challenge_axe_throw_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_axe_throw_500", "axe.throwing-axe.hits", 500, 500);
    registerMilestone("challenge_axe_throw_5k", "axe.throwing-axe.hits", 5000, 1800);
  }

  @Override
  protected void onRuntimeActivated() {
    closing.set(false);
    for (Player player : Bukkit.getOnlinePlayers()) {
      J.runEntity(player, () -> recoverStampedAxes(player));
    }
  }

  @Override
  public void unregister() {
    List<AxeReplacementTicket> replacements;
    List<PendingAxeDrop> drops;
    List<Map.Entry<UUID, ThrownAxe>> activeThrows;
    synchronized (lifecycleLock) {
      closing.set(true);
      blockBreakSwingGuard.clear();
      replacements = new ArrayList<>(pendingReplacements);
      pendingReplacements.clear();
      drops = new ArrayList<>(pendingDrops.values());
      activeThrows = new ArrayList<>(inFlight.entrySet());
      inFlight.clear();
    }
    for (AxeReplacementTicket replacement : replacements) {
      replacement.cancel();
    }
    for (PendingAxeDrop drop : drops) {
      drop.cancel(null);
    }
    long recoveryDeadline = System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_RECOVERY_MILLIS);
    for (PendingAxeDrop drop : drops) {
      long remainingNanos = recoveryDeadline - System.nanoTime();
      if (remainingNanos <= 0L || !drop.awaitResolution(remainingNanos)) {
        if (!drop.persistFallback()) {
          Adapt.error("Failed to journal a reserved thrown axe during shutdown.");
        }
      }
    }
    for (Map.Entry<UUID, ThrownAxe> entry : activeThrows) {
      UUID projectileId = entry.getKey();
      ThrownAxe thrown = entry.getValue();
      ProjectileReplacementRegistry.unregister(projectileId);
      removeProjectile(projectileId);
      recoverRetiredAxe(thrown);
    }
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getDamageMultiplier(f), 2), 1);
    statLore(v, C.GREEN, "+ ", Form.f(getThrowSpeed(f), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMs(f), 1), 3);
    if (returnsAtLevel(level)) {
      v.addLore(C.AQUA + "" + AdaptLanguage.text(AxeMessages.THROWING_AXE_LORE4));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (!isThrowInteraction(e.getAction(), e.getHand())) {
      if (e.getAction() == Action.LEFT_CLICK_BLOCK
          && (e.getHand() == null || e.getHand() == EquipmentSlot.HAND)) {
        blockBreakSwingGuard.clear(e.getPlayer().getUniqueId());
      }
      return;
    }

    Player p = e.getPlayer();
    if (blockBreakSwingGuard.consume(p.getUniqueId(), p.getTicksLived())) {
      return;
    }

    withAdaptedPlayer(p, () -> throwAxe(p));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!isAxe(p.getInventory().getItemInMainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    blockBreakSwingGuard.mark(p.getUniqueId(), p.getTicksLived());
  }

  private void throwAxe(Player p) {
    if (closing.get()) {
      return;
    }
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isAxe(hand)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    long cooldown = (long) getCooldownMs(getLevelPercent(level));
    if (!cooldowns.isReady(id, cooldown)) {
      return;
    }

    if (inFlight.size() >= MAX_IN_FLIGHT) {
      return;
    }

    ItemStack consumedAxe = hand.clone();
    consumedAxe.setAmount(1);
    ItemStack thrown = consumedAxe.clone();
    boolean broken = applyThrowDurability(thrown, getConfig().durabilityCost);
    double damage = getThrowDamage(hand.getType(), getLevelPercent(level));
    boolean returns = returnsAtLevel(level);

    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "throw", new ItemStack(hand.getType()), 1, () -> {
      if (hand.getAmount() > 1) {
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand);
      } else {
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      }

      defaultConsumed.set(true);
      return true;
    })) {
      return;
    }

    boolean recoverable = isRecoverableThrow(defaultConsumed.get(), broken);
    byte[] recoveryData = null;
    if (recoverable) {
      try {
        recoveryData = thrown.serializeAsBytes();
      } catch (Throwable error) {
        deliverAxe(p, consumedAxe);
        playReturnFx(p);
        Adapt.warn("Failed to prepare durable recovery for a thrown axe owned by " + p.getName() + ".");
        Adapt.error(error);
        return;
      }
    }

    Location eye = p.getEyeLocation();
    Vector dir = eye.getDirection().clone().normalize();
    double speed = getThrowSpeed(getLevelPercent(level));
    Location spawn = eye.clone().add(dir.clone().multiply(0.6D));
    Snowball ball;
    try {
      ball = p.getWorld().spawn(spawn, Snowball.class, snowball -> {
        snowball.setItem(thrown.clone());
        snowball.setShooter(p);
        snowball.setVelocity(dir.clone().multiply(speed));
      });
    } catch (Throwable error) {
      if (defaultConsumed.get()) {
        deliverAxe(p, consumedAxe);
        playReturnFx(p);
      }
      Adapt.error("Failed to spawn a thrown axe for " + p.getUniqueId() + ".");
      Adapt.error(error);
      return;
    }

    UUID ballId = ball.getUniqueId();
    NamespacedKey recoveryKey = recoveryKey(ballId);
    if (recoveryData != null) {
      try {
        p.getPersistentDataContainer().set(recoveryKey, PersistentDataType.BYTE_ARRAY, recoveryData);
      } catch (Throwable error) {
        ball.remove();
        deliverAxe(p, consumedAxe);
        playReturnFx(p);
        Adapt.warn("Failed to persist durable recovery for a thrown axe owned by " + p.getName() + ".");
        Adapt.error(error);
        return;
      }
    }
    int flightTicks = Math.max(1, getConfig().maxFlightTicks);
    ThrownAxe state = new ThrownAxe(
        id,
        thrown,
        damage,
        returns,
        defaultConsumed.get() && broken,
        recoverable,
        recoveryKey,
        flightDeadline(System.nanoTime(), flightTicks)
    );
    if (!registerThrow(ball, state)) {
      ball.remove();
      recoverRetiredAxe(state);
      return;
    }
    cooldowns.mark(id);
    p.setCooldown(thrown.getType(), (int) Math.max(1L, cooldown / 50L));
    addStat(p, "axe.throwing-axe.thrown", 1);

    fx(spawn, FxPriority.GAMEPLAY)
        .trail(Particles.CRIT_MAGIC, dir.getX(), dir.getY(), dir.getZ(), 1.3D, 6)
        .chord(Sound.ITEM_TRIDENT_THROW, 0.8F, 1.2F, Sound.ITEM_AXE_STRIP, 0.4F, 0.7F);
    timeline(ball)
        .duration(flightTicks)
        .priority(FxPriority.TRAIL)
        .cullRadius(40)
        .frame((f, tick, progress) -> {
          f.particle(Particles.CRIT_MAGIC, 2, 0D, 0D, 0D, 0.05D, 0.0D);
          if ((tick & 1) == 0) {
            f.particle(Particles.SMOKE, 1, 0D, 0D, 0D, 0.02D, 0.0D);
          }
        })
        .start();

    if (!scheduleThrowExpiry(ballId, ball, state)) {
      resolveThrow(ballId, ball, null);
    }
  }

  private boolean registerThrow(Snowball ball, ThrownAxe thrown) {
    synchronized (lifecycleLock) {
      if (closing.get()) {
        return false;
      }
      UUID projectileId = ball.getUniqueId();
      inFlight.put(projectileId, thrown);
      ProjectileReplacementRegistry.register(
          ball,
          source -> beginRicochetReplacement(source, thrown)
      );
      return true;
    }
  }

  private ProjectileReplacementRegistry.Ticket beginRicochetReplacement(
      Projectile source,
      ThrownAxe thrown
  ) {
    synchronized (lifecycleLock) {
      UUID projectileId = source.getUniqueId();
      if (closing.get() || !(source instanceof Snowball)
          || !inFlight.remove(projectileId, thrown)) {
        return null;
      }

      Location impact = safeLocation(source);
      AxeReplacementTicket replacement = new AxeReplacementTicket(thrown, impact);
      pendingReplacements.add(replacement);
      return replacement;
    }
  }

  private boolean scheduleThrowExpiry(UUID projectileId, Snowball ball, ThrownAxe thrown) {
    long remainingTicks = remainingFlightTicks(thrown.expiresAtNanos(), System.nanoTime());
    if (remainingTicks <= 0L) {
      return false;
    }
    try {
      return FoliaScheduler.runEntity(
          Adapt.instance,
          ball,
          () -> resolveThrow(projectileId, ball, null),
          remainingTicks
      );
    } catch (Throwable error) {
      Adapt.error("Failed to schedule thrown axe expiry for " + projectileId + ".");
      Adapt.error(error);
      return false;
    }
  }

  private void retireThrow(UUID ballId, Location impact) {
    ProjectileReplacementRegistry.unregister(ballId);
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null) {
      return;
    }

    finishThrow(impact, thrown);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof Snowball ball) || !inFlight.containsKey(ball.getUniqueId())) {
      return;
    }
    resolveThrow(ball.getUniqueId(), ball, e.getHitEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (e.getEntity() instanceof Snowball ball) {
      retireThrow(ball.getUniqueId(), safeLocation(ball));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      return;
    }
    ThrowDamageAttempt attempt = active.peekLast();
    if (attempt == null || e.getDamager() != attempt.owner() || e.getEntity() != attempt.target()) {
      return;
    }
    attempt.resolve(isSuccessfulDamageEvent(true, e.isCancelled(), e.getFinalDamage()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      return;
    }
    ThrowDamageAttempt attempt = active.peekLast();
    if (attempt != null && e.getEntity() == attempt.target()) {
      attempt.markKilled();
    }
  }

  private void resolveThrow(UUID ballId, Snowball ball, Entity hit) {
    ProjectileReplacementRegistry.unregister(ballId);
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null) {
      return;
    }

    Location impact = safeLocation(ball);
    Player owner = Bukkit.getPlayer(thrown.ownerId());
    RangedRicochetBolt.RicochetImpact ricochet = RangedRicochetBolt.impactOf(ball);

    if (hit instanceof LivingEntity target && owner != null) {
      J.runEntity(target, () -> prepareThrowHit(target, owner, thrown, ricochet));
    }

    if (ball.isValid()) {
      ball.remove();
    }

    finishThrow(impact, thrown);
  }

  private void finishThrow(Location impact, ThrownAxe thrown) {
    if (thrown.broken()) {
      if (impact != null) {
        fx(impact, FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 6, 0.25D)
            .sound(Sound.ENTITY_ITEM_BREAK, 0.8F, 0.9F);
      }
      return;
    }

    if (!thrown.recoverable()) {
      return;
    }

    Player owner = Bukkit.getPlayer(thrown.ownerId());
    if (owner != null) {
      J.runEntity(owner, () -> completeThrowRecovery(owner, impact, thrown));
    }
  }

  private void prepareThrowHit(
      LivingEntity target,
      Player owner,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!isEligibleThrowTarget(target, thrown.ownerId())) {
      return;
    }

    Location targetLocation = target.getLocation().clone();
    boolean playerTarget = target instanceof Player;
    J.runEntity(
        owner,
        () -> authorizeThrowHit(owner, target, targetLocation, playerTarget, thrown, ricochet)
    );
  }

  private void authorizeThrowHit(
      Player owner,
      LivingEntity target,
      Location targetLocation,
      boolean playerTarget,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!owner.isOnline() || !hasActiveAdaptation(owner)) {
      return;
    }
    boolean allowed = playerTarget ? canPVP(owner, targetLocation) : canPVE(owner, targetLocation);
    if (allowed) {
      J.runEntity(target, () -> damageThrowTarget(target, owner, thrown, ricochet));
    }
  }

  private void damageThrowTarget(
      LivingEntity target,
      Player owner,
      ThrownAxe thrown,
      RangedRicochetBolt.RicochetImpact ricochet
  ) {
    if (!isEligibleThrowTarget(target, thrown.ownerId())) {
      return;
    }
    ThrowDamageAttempt attempt = new ThrowDamageAttempt(owner, target);
    Deque<ThrowDamageAttempt> active = damageAttempts.get();
    if (active == null) {
      active = new ArrayDeque<>();
      damageAttempts.set(active);
    }
    active.addLast(attempt);
    try {
      target.damage(thrown.damage() + ricochet.bonusDamage(), owner);
    } catch (Throwable error) {
      Adapt.error("Thrown axe damage failed for target " + target.getUniqueId() + ".");
      Adapt.error(error);
      return;
    } finally {
      active.removeLastOccurrence(attempt);
      if (active.isEmpty()) {
        damageAttempts.remove();
      }
    }
    if (!attempt.successful()) {
      return;
    }
    fx(target.getLocation().add(0D, target.getHeight() * 0.6D, 0D), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 8, 0.35D)
        .chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.0F, Sound.ITEM_AXE_STRIP, 0.4F, 0.8F);
    J.runEntity(owner, () -> rewardHit(owner));
    if (attempt.killed() && ricochet.count() > 0) {
      Location deathLocation = target.getLocation().clone();
      J.runEntity(
          owner,
          () -> rewardRicochetKill(owner, deathLocation, ricochet.count())
      );
    }
  }

  private void rewardRicochetKill(Player owner, Location deathLocation, int ricochetCount) {
    if (!owner.isOnline() || Adapt.instance.getAdaptServer() == null
        || Adapt.instance.getAdaptServer().getSkillRegistry() == null) {
      return;
    }
    Skill<?> ranged = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill("ranged");
    if (ranged == null) {
      return;
    }
    for (Adaptation<?> adaptation : ranged.getAdaptations()) {
      if (adaptation instanceof RangedRicochetBolt ricochet) {
        ricochet.rewardManualRicochetKill(owner, deathLocation, ricochetCount);
        return;
      }
    }
  }

  private boolean isEligibleThrowTarget(LivingEntity target, UUID ownerId) {
    if (!target.isValid() || target.isDead() || ownerId.equals(target.getUniqueId())
        || isProtectedFriendly(null, target)) {
      return false;
    }
    if (!(target instanceof Tameable tameable) || !tameable.isTamed()) {
      return true;
    }
    AnimalTamer tamer = tameable.getOwner();
    return tamer == null || !ownerId.equals(tamer.getUniqueId());
  }

  private void completeThrowRecovery(Player owner, Location impact, ThrownAxe thrown) {
    if (!owner.isOnline()) {
      return;
    }
    if (thrown.returns()) {
      recoverStampedAxe(owner, thrown.recoveryKey());
    } else {
      dropAxe(impact, thrown, owner);
    }
  }

  private void rewardHit(Player owner) {
    if (!owner.isOnline() || !hasActiveAdaptation(owner)) {
      return;
    }
    addStat(owner, "axe.throwing-axe.hits", 1);
    xp(owner, getConfig().xpPerHit);
  }

  private void deliverAxe(Player owner, ItemStack axe) {
    ItemStack main = owner.getInventory().getItemInMainHand();
    if (main == null || main.getType() == Material.AIR) {
      owner.getInventory().setItemInMainHand(axe);
    } else {
      Map<Integer, ItemStack> overflow = owner.getInventory().addItem(axe);
      for (ItemStack item : overflow.values()) {
        owner.getWorld().dropItem(owner.getLocation(), item);
      }
    }
  }

  private void playReturnFx(Player owner) {
    fx(owner.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.ENCHANTMENT_TABLE, 8, 0.3D)
        .chord(Sound.ITEM_TRIDENT_RETURN, 0.7F, 1.1F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
  }

  private void dropAxe(Location impact, ThrownAxe thrown, Player owner) {
    if (impact == null || impact.getWorld() == null) {
      return;
    }
    PersistentDataContainer data = owner.getPersistentDataContainer();
    byte[] encoded = data.get(thrown.recoveryKey(), PersistentDataType.BYTE_ARRAY);
    if (encoded == null) {
      return;
    }

    PendingAxeDrop pending = new PendingAxeDrop(thrown, impact, encoded);
    synchronized (lifecycleLock) {
      if (closing.get() || pendingDrops.putIfAbsent(thrown.recoveryKey(), pending) != null) {
        return;
      }
      try {
        data.remove(thrown.recoveryKey());
      } catch (Throwable error) {
        pendingDrops.remove(thrown.recoveryKey(), pending);
        Adapt.error("Failed to reserve a thrown axe impact drop.");
        Adapt.error(error);
        return;
      }
    }

    if (!J.runAt(impact, pending::deliver)) {
      pending.cancel(owner);
    }
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    recoverStampedAxes(event.getPlayer());
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    blockBreakSwingGuard.clear(playerId);
    for (AxeReplacementTicket replacement : new ArrayList<>(pendingReplacements)) {
      if (replacement.ownerId().equals(playerId)) {
        replacement.cancel();
      }
    }
    for (PendingAxeDrop drop : new ArrayList<>(pendingDrops.values())) {
      if (drop.ownerId().equals(playerId)) {
        drop.cancel(player);
      }
    }
    for (Map.Entry<UUID, ThrownAxe> entry : new ArrayList<>(inFlight.entrySet())) {
      ThrownAxe thrown = entry.getValue();
      if (!thrown.ownerId().equals(playerId) || !inFlight.remove(entry.getKey(), thrown)) {
        continue;
      }
      ProjectileReplacementRegistry.unregister(entry.getKey());
      removeProjectile(entry.getKey());
      if (thrown.recoverable()) {
        recoverStampedAxe(player, thrown.recoveryKey());
      }
    }
  }

  private void recoverStampedAxes(Player player) {
    if (!player.isOnline()) {
      return;
    }
    for (PendingAxeDrop drop : new ArrayList<>(pendingDrops.values())) {
      if (drop.ownerId().equals(player.getUniqueId())) {
        drop.cancel(player);
      }
    }
    JournalRecoveryBatch journalRecovery = recoverJournalAxes(player);
    if (!journalRecovery.available()) {
      return;
    }
    PersistentDataContainer data = player.getPersistentDataContainer();
    for (NamespacedKey key : new ArrayList<>(data.getKeys())) {
      if (key.getNamespace().equals(recoveryNamespace)
          && key.getKey().startsWith(RECOVERY_KEY_PREFIX)
          && !journalRecovery.keys().contains(key)) {
        recoverStampedAxe(player, key);
      }
    }
  }

  private JournalRecoveryBatch recoverJournalAxes(Player player) {
    UUID ownerId = player.getUniqueId();
    List<String> recoveryKeys;
    try {
      recoveryKeys = recoveryJournal.keys(ownerId);
    } catch (Throwable error) {
      Adapt.error("Failed to list journaled thrown axes for " + ownerId + ".");
      Adapt.error(error);
      return new JournalRecoveryBatch(false, Set.of());
    }

    Set<NamespacedKey> journalKeys = new HashSet<>(recoveryKeys.size());
    for (String recoveryKeyName : recoveryKeys) {
      NamespacedKey journalKey = new NamespacedKey(Adapt.instance, recoveryKeyName);
      journalKeys.add(journalKey);
      NamespacedKey recoveryKey = stageJournaledAxe(player, recoveryKeyName);
      if (recoveryKey != null) {
        recoverStampedAxe(player, recoveryKey);
      }
    }
    return new JournalRecoveryBatch(true, journalKeys);
  }

  private NamespacedKey stageJournaledAxe(Player player, String recoveryKeyName) {
    UUID ownerId = player.getUniqueId();
    NamespacedKey recoveryKey = new NamespacedKey(Adapt.instance, recoveryKeyName);
    try {
      byte[] journaled = recoveryJournal.read(ownerId, recoveryKeyName);
      PersistentDataContainer data = player.getPersistentDataContainer();
      byte[] stamped = data.get(recoveryKey, PersistentDataType.BYTE_ARRAY);
      if (stamped != null && !Arrays.equals(stamped, journaled)) {
        Adapt.error("Conflicting journaled thrown axe for " + ownerId
            + " at " + recoveryKeyName + ".");
        return null;
      }
      if (stamped == null) {
        data.set(recoveryKey, PersistentDataType.BYTE_ARRAY, journaled);
      }
      recoveryJournal.delete(ownerId, recoveryKeyName);
      return recoveryKey;
    } catch (Throwable error) {
      Adapt.error("Failed to import a journaled thrown axe for " + ownerId
          + " at " + recoveryKeyName + ".");
      Adapt.error(error);
      return null;
    }
  }

  private void recoverStampedAxe(Player player, NamespacedKey key) {
    PersistentDataContainer data = player.getPersistentDataContainer();
    byte[] encoded = data.get(key, PersistentDataType.BYTE_ARRAY);
    if (encoded == null) {
      return;
    }

    ItemStack axe;
    try {
      axe = ItemStack.deserializeBytes(encoded);
    } catch (Throwable error) {
      Adapt.warn("Failed to decode durable thrown-axe recovery for " + player.getName() + ".");
      Adapt.error(error);
      return;
    }

    try {
      data.remove(key);
      deliverAxe(player, axe);
    } catch (Throwable error) {
      try {
        data.set(key, PersistentDataType.BYTE_ARRAY, encoded);
      } catch (Throwable restoreError) {
        error.addSuppressed(restoreError);
      }
      Adapt.error("Failed to deliver durable thrown-axe recovery to " + player.getName() + ".");
      Adapt.error(error);
      return;
    }
    playReturnFx(player);
  }

  private NamespacedKey recoveryKey(UUID projectileId) {
    return new NamespacedKey(
        Adapt.instance,
        RECOVERY_KEY_PREFIX + projectileId.toString().replace("-", "")
    );
  }

  private void removeProjectile(UUID projectileId) {
    Entity projectile = Bukkit.getEntity(projectileId);
    if (projectile != null) {
      J.runEntity(projectile, () -> {
        if (projectile.isValid()) {
          projectile.remove();
        }
      });
    }
  }

  private Location safeLocation(Entity entity) {
    try {
      return entity.getLocation().clone();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private boolean applyThrowDurability(ItemStack stack, int amount) {
    ItemMeta meta = stack.getItemMeta();
    if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
      return false;
    }

    int next = damageable.getDamage() + amount;
    if (reachesBreakThreshold(
        damageable.getDamage(),
        amount,
        stack.getType().getMaxDurability()
    )) {
      return true;
    }
    damageable.setDamage(next);
    stack.setItemMeta(meta);
    return false;
  }

  private boolean returnsAtLevel(int level) {
    return getLevelPercent(level) >= getConfig().returnUnlockLevelPercent;
  }

  private double getThrowSpeed(double factor) {
    return getConfig().throwSpeedBase + (factor * getConfig().throwSpeedFactor);
  }

  private double getDamageMultiplier(double factor) {
    return getConfig().damageMultiplierBase + (factor * getConfig().damageMultiplierFactor);
  }

  private double getThrowDamage(Material axe, double factor) {
    return meleeBaseDamage(axe) * getDamageMultiplier(factor);
  }

  private double getCooldownMs(double factor) {
    return Math.max(250D, getConfig().cooldownMsBase - (factor * getConfig().cooldownMsLevelReduction));
  }

  static double meleeBaseDamage(Material axe) {
    return switch (axe) {
      case NETHERITE_AXE -> 10D;
      case DIAMOND_AXE, IRON_AXE, STONE_AXE -> 9D;
      case WOODEN_AXE, GOLDEN_AXE -> 7D;
      default -> 7D;
    };
  }

  static boolean isThrowInteraction(Action action, EquipmentSlot hand) {
    return action == Action.LEFT_CLICK_AIR
        && (hand == null || hand == EquipmentSlot.HAND);
  }

  static boolean reachesBreakThreshold(int currentDamage, int addedDamage, int maxDurability) {
    return (long) currentDamage + addedDamage >= maxDurability;
  }

  static long flightDeadline(long nowNanos, long flightTicks) {
    long safeTicks = Math.max(1L, flightTicks);
    long maximumTicks = Long.MAX_VALUE / TICK_NANOS;
    long duration = Math.min(safeTicks, maximumTicks) * TICK_NANOS;
    if (nowNanos > Long.MAX_VALUE - duration) {
      return Long.MAX_VALUE;
    }
    return nowNanos + duration;
  }

  static long remainingFlightTicks(long deadlineNanos, long nowNanos) {
    long remainingNanos = deadlineNanos - nowNanos;
    if (remainingNanos <= 0L) {
      return 0L;
    }
    long completeTicks = remainingNanos / TICK_NANOS;
    return remainingNanos % TICK_NANOS == 0L ? completeTicks : completeTicks + 1L;
  }

  static boolean isSuccessfulDamageEvent(boolean observed, boolean cancelled, double finalDamage) {
    return observed && !cancelled && finalDamage > 0D;
  }

  private void recoverRetiredAxe(ThrownAxe thrown) {
    Player owner = Bukkit.getPlayer(thrown.ownerId());
    RecoveryDisposition disposition = recoveryDisposition(thrown.recoverable(), owner != null && owner.isOnline());
    if (disposition == RecoveryDisposition.DESTROYED) {
      return;
    }
    if (disposition == RecoveryDisposition.OWNER) {
      J.runEntity(owner, () -> recoverStampedAxe(owner, thrown.recoveryKey()));
    }
  }

  static boolean isRecoverableThrow(boolean defaultConsumed, boolean broken) {
    return defaultConsumed && !broken;
  }

  static RecoveryDisposition recoveryDisposition(boolean recoverable, boolean ownerOnline) {
    if (!recoverable) {
      return RecoveryDisposition.DESTROYED;
    }
    return ownerOnline ? RecoveryDisposition.OWNER : RecoveryDisposition.PENDING;
  }

  enum RecoveryDisposition {
    DESTROYED,
    OWNER,
    PENDING
  }

  record ThrownAxe(
      UUID ownerId,
      ItemStack axe,
      double damage,
      boolean returns,
      boolean broken,
      boolean recoverable,
      NamespacedKey recoveryKey,
      long expiresAtNanos
  ) {
    ThrownAxe {
      axe = axe.clone();
    }
  }

  private record JournalRecoveryBatch(boolean available, Set<NamespacedKey> keys) {
    private JournalRecoveryBatch {
      keys = Set.copyOf(keys);
    }
  }

  private final class PendingAxeDrop {
    private final ThrownAxe thrown;
    private final Location impact;
    private final byte[] encoded;
    private final CountDownLatch resolution = new CountDownLatch(1);
    private boolean deliveryAllowed = true;
    private boolean resolved;
    private boolean restorationScheduled;

    private PendingAxeDrop(ThrownAxe thrown, Location impact, byte[] encoded) {
      this.thrown = thrown;
      this.impact = impact.clone();
      this.encoded = encoded.clone();
    }

    private void deliver() {
      Throwable failure = null;
      synchronized (this) {
        if (resolved || !deliveryAllowed) {
          return;
        }
        try {
          impact.getWorld().dropItem(impact, thrown.axe());
          resolved = true;
          pendingDrops.remove(thrown.recoveryKey(), this);
          resolution.countDown();
        } catch (Throwable error) {
          deliveryAllowed = false;
          failure = error;
        }
      }
      if (failure != null) {
        Adapt.error("Failed to materialize a thrown axe impact drop.");
        Adapt.error(failure);
        cancel(null);
      }
    }

    private void cancel(Player knownOwner) {
      Player owner = knownOwner == null ? Bukkit.getPlayer(thrown.ownerId()) : knownOwner;
      synchronized (this) {
        deliveryAllowed = false;
        if (resolved || restorationScheduled) {
          return;
        }
        if (owner == null) {
          persistFallback();
          return;
        }
        restorationScheduled = true;
      }

      if (J.isOwnedByCurrentRegion(owner)) {
        restoreOwned(owner);
        return;
      }
      try {
        boolean scheduled = FoliaScheduler.runEntity(
            Adapt.instance,
            owner,
            () -> restoreOwned(owner),
            0L,
            this::restoreRetired
        );
        if (!scheduled) {
          restoreRetired();
        }
      } catch (Throwable error) {
        restoreRetired();
        Adapt.error("Failed to schedule restoration of a reserved thrown axe drop.");
        Adapt.error(error);
      }
    }

    private void restoreOwned(Player owner) {
      try {
        synchronized (this) {
          if (resolved) {
            return;
          }
          PersistentDataContainer data = owner.getPersistentDataContainer();
          byte[] stamped = data.get(thrown.recoveryKey(), PersistentDataType.BYTE_ARRAY);
          if (stamped != null && !Arrays.equals(stamped, encoded)) {
            throw new IllegalStateException(
                "Conflicting durable thrown-axe stamp for " + thrown.ownerId()
            );
          }
          if (stamped == null) {
            data.set(thrown.recoveryKey(), PersistentDataType.BYTE_ARRAY, encoded.clone());
          }
          resolved = true;
          restorationScheduled = false;
          pendingDrops.remove(thrown.recoveryKey(), this);
          resolution.countDown();
        }
      } catch (Throwable error) {
        persistFallback();
        Adapt.error("Failed to restore a reserved thrown axe drop.");
        Adapt.error(error);
      }
    }

    private void restoreRetired() {
      synchronized (this) {
        restorationScheduled = false;
      }
      if (!persistFallback()) {
        Adapt.error("Failed to journal a retired thrown axe recovery.");
      }
    }

    private synchronized boolean persistFallback() {
      deliveryAllowed = false;
      if (resolved) {
        return true;
      }
      try {
        recoveryJournal.persist(thrown.ownerId(), thrown.recoveryKey(), encoded);
        resolved = true;
        restorationScheduled = false;
        pendingDrops.remove(thrown.recoveryKey(), this);
        resolution.countDown();
        return true;
      } catch (Throwable error) {
        restorationScheduled = false;
        Adapt.error("Failed to persist durable thrown-axe recovery for "
            + thrown.ownerId() + ".");
        Adapt.error(error);
        return false;
      }
    }

    private boolean awaitResolution(long timeoutNanos) {
      try {
        return resolution.await(Math.max(0L, timeoutNanos), TimeUnit.NANOSECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    private UUID ownerId() {
      return thrown.ownerId();
    }
  }

  private final class AxeReplacementTicket implements ProjectileReplacementRegistry.Ticket {
    private final ThrownAxe thrown;
    private final Location impact;
    private boolean finished;

    private AxeReplacementTicket(ThrownAxe thrown, Location impact) {
      this.thrown = thrown;
      this.impact = impact == null ? null : impact.clone();
    }

    @Override
    public synchronized boolean complete(Projectile replacement) {
      if (finished || !(replacement instanceof Snowball ball)) {
        return false;
      }

      synchronized (lifecycleLock) {
        UUID projectileId = ball.getUniqueId();
        if (closing.get()
            || remainingFlightTicks(thrown.expiresAtNanos(), System.nanoTime()) <= 0L
            || inFlight.putIfAbsent(projectileId, thrown) != null) {
          return false;
        }
        ProjectileReplacementRegistry.register(
            ball,
            source -> beginRicochetReplacement(source, thrown)
        );
        if (!scheduleThrowExpiry(projectileId, ball, thrown)) {
          ProjectileReplacementRegistry.unregister(projectileId);
          inFlight.remove(projectileId, thrown);
          return false;
        }

        finished = true;
        pendingReplacements.remove(this);
        return true;
      }
    }

    @Override
    public synchronized void cancel() {
      if (finished) {
        return;
      }
      finished = true;
      pendingReplacements.remove(this);
      finishThrow(impact, thrown);
    }

    private UUID ownerId() {
      return thrown.ownerId();
    }
  }

  private static final class ThrowDamageAttempt {
    private final Player owner;
    private final LivingEntity target;
    private boolean successful;
    private boolean killed;

    private ThrowDamageAttempt(Player owner, LivingEntity target) {
      this.owner = owner;
      this.target = target;
    }

    private Player owner() {
      return owner;
    }

    private LivingEntity target() {
      return target;
    }

    private void resolve(boolean accepted) {
      successful = accepted;
    }

    private boolean successful() {
      return successful;
    }

    private void markKilled() {
      killed = true;
    }

    private boolean killed() {
      return killed;
    }
  }

  @ConfigDescription("Left-click air with an axe to hurl it as a spinning projectile that deals its melee damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of the axe's melee damage dealt on a throw hit.", impact = "Higher values make thrown axes hit harder at every level.")
    double damageMultiplierBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra melee-damage fraction added by leveling this adaptation.", impact = "Higher values reward leveling with stronger throws.")
    double damageMultiplierFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base flight velocity of a thrown axe in blocks per tick.", impact = "Higher values make thrown axes travel faster and flatter.")
    double throwSpeedBase = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra flight velocity added by leveling this adaptation.", impact = "Higher values increase throw speed scaling with level.")
    double throwSpeedFactor = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base throw cooldown in milliseconds before level scaling.", impact = "Higher values slow how often axes can be thrown.")
    double cooldownMsBase = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds of cooldown removed at maximum level.", impact = "Higher values let higher levels throw more frequently.")
    double cooldownMsLevelReduction = 600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Durability spent from the axe on each throw.", impact = "Higher values wear thrown axes out faster.")
    int durabilityCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a thrown axe stays airborne before it is recovered automatically.", impact = "Higher values let throws travel farther before auto-recovery.")
    int maxFlightTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level progress required before thrown axes return to your hand.", impact = "Lower values unlock the auto-return at an earlier level.")
    double returnUnlockLevelPercent = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per thrown-axe hit on an entity.", impact = "Higher values accelerate skill progression from throwing.")
    double xpPerHit = 6;

    public Config() {
      baseCost = 5;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 5;
    }
  }
}
