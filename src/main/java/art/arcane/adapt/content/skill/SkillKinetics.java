package art.arcane.adapt.content.skill;

import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillOwnerPulse;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.kinetics.KineticsBreachwright;
import art.arcane.adapt.content.adaptation.kinetics.KineticsChargeLance;
import art.arcane.adapt.content.adaptation.kinetics.KineticsDeadZone;
import art.arcane.adapt.content.adaptation.kinetics.KineticsHeavyFrame;
import art.arcane.adapt.content.adaptation.kinetics.KineticsImpalePin;
import art.arcane.adapt.content.adaptation.kinetics.KineticsLungeConductor;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMassShift;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMeteorCadence;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMoonJump;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMountedShock;
import art.arcane.adapt.content.adaptation.kinetics.KineticsPhalanxReach;
import art.arcane.adapt.content.adaptation.kinetics.KineticsQuakeGuard;
import art.arcane.adapt.content.adaptation.kinetics.KineticsReboundAnvil;
import art.arcane.adapt.content.adaptation.kinetics.KineticsRubberSoul;
import art.arcane.adapt.content.adaptation.kinetics.KineticsSoftCatch;
import art.arcane.adapt.content.adaptation.kinetics.KineticsSurfaceSkate;
import art.arcane.adapt.content.adaptation.kinetics.KineticsTerminalToggle;
import art.arcane.adapt.content.adaptation.kinetics.KineticsWindburst;
import art.arcane.adapt.content.skill.kinetics.KineticsAnvils;
import art.arcane.adapt.content.skill.kinetics.KineticsKnockback;
import art.arcane.adapt.content.skill.kinetics.KineticsLevitation;
import art.arcane.adapt.content.skill.kinetics.KineticsMotion;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SkillKinetics extends SimpleSkill<SkillKinetics.Config> {
  private static final long PENDING_CRUSH_SETTLE_MS = 3000L;
  private static final long SWEEP_INTERVAL_MS = 5000L;
  private static final int ANVIL_SHARE_CAP = 8;
  private static final int LEVITATION_TARGET_CAP = 8;

  private final SkillOwnerPulse.Registration ownerPulse;
  private final Cooldowns combatCooldowns = cooldowns();
  private final Cooldowns kbCooldowns = cooldowns();
  private final Cooldowns levitationCooldowns = cooldowns();
  private final Cooldowns anvilCooldowns = cooldowns();
  private final Cooldowns anvilShareCooldowns = cooldowns();
  private final Map<UUID, Long> lungeStamps = playerState();
  private final Map<UUID, KineticsMotion.MotionState> motionStates = playerState();
  private final Map<UUID, MotionRewardState> motionRewards = playerState();
  private final KineticsAnvils anvils = new KineticsAnvils();
  private final Set<UUID> rewardedLevitationClouds = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<UUID, PendingCrush> pendingCrushes = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> anvilLocationStamps = new ConcurrentHashMap<>();
  private final AtomicLong lastSweepAtMs = new AtomicLong();

  public SkillKinetics() {
    super("kinetics", SkillPresentation.of(SkillMessages.KINETICS_NAME, SkillMessages.KINETICS_ICON, SkillMessages.KINETICS_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.GOLD);
    setInterval(1000);
    setIcon(Material.MACE);
    registerAdaptation(new KineticsMoonJump());
    registerAdaptation(new KineticsRubberSoul());
    registerAdaptation(new KineticsSoftCatch());
    registerAdaptation(new KineticsSurfaceSkate());
    registerAdaptation(new KineticsTerminalToggle());
    registerAdaptation(new KineticsHeavyFrame());
    registerAdaptation(new KineticsMassShift());

    registerAdaptation(new KineticsMeteorCadence());
    registerAdaptation(new KineticsBreachwright());
    registerAdaptation(new KineticsWindburst());
    registerAdaptation(new KineticsQuakeGuard());
    registerAdaptation(new KineticsReboundAnvil());

    registerAdaptation(new KineticsPhalanxReach());
    registerAdaptation(new KineticsChargeLance());
    registerAdaptation(new KineticsImpalePin());
    registerAdaptation(new KineticsLungeConductor());
    registerAdaptation(new KineticsMountedShock());
    registerAdaptation(new KineticsDeadZone());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ANVIL)
        .key("challenge_kinetics_anvil_drop")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_kinetics_anvil_drop", "kinetics.anvil.deep-kills", 1, () -> getConfig().anvilDropReward);
    ownerPulse = SkillOwnerPulse.register(this, this::getInterval, this::pulseKinetics);
  }

  static boolean isSuccessfulSmash(Event.Result result, boolean originalResult) {
    return result == Event.Result.ALLOW || (result == Event.Result.DEFAULT && originalResult);
  }

  static boolean isSweetRangeJab(double distance, double sweetMin, double sweetMax) {
    if (!Double.isFinite(distance) || !Double.isFinite(sweetMin) || !Double.isFinite(sweetMax)) {
      return false;
    }
    return distance >= sweetMin && distance <= sweetMax;
  }

  static boolean isChargeHit(long nowMs, long lastLungeAtMs, long chargeWindowMs, double horizontalSpeed, double minSpeed) {
    boolean lungeActive = lastLungeAtMs > 0 && nowMs >= lastLungeAtMs && nowMs - lastLungeAtMs <= chargeWindowMs;
    boolean speedQualifies = Double.isFinite(horizontalSpeed) && Double.isFinite(minSpeed) && horizontalSpeed >= minSpeed;
    return lungeActive || speedQualifies;
  }

  static boolean motionRewardReady(long nowMs, long lastRewardAtMs, double distanceSquared,
      long cooldownMs, double minimumDistance) {
    if (nowMs < lastRewardAtMs || nowMs - lastRewardAtMs < Math.max(0L, cooldownMs)) {
      return false;
    }
    if (!Double.isFinite(distanceSquared) || !Double.isFinite(minimumDistance) || minimumDistance < 0D) {
      return false;
    }
    return distanceSquared >= minimumDistance * minimumDistance;
  }

  static boolean isLaunchSurface(Material material) {
    if (material == null) {
      return false;
    }
    return switch (material) {
      case SLIME_BLOCK, PISTON, STICKY_PISTON, PISTON_HEAD, MOVING_PISTON -> true;
      default -> false;
    };
  }

  static BlockFace pistonMovementDirection(BlockFace direction, boolean retracting) {
    return retracting ? direction.getOppositeFace() : direction;
  }

  static boolean isCorrelatedAnvilDeath(EntityDamageEvent.DamageCause cause, UUID actualFallingBlockId,
      UUID expectedFallingBlockId) {
    return cause == EntityDamageEvent.DamageCause.FALLING_BLOCK
        && expectedFallingBlockId != null
        && expectedFallingBlockId.equals(actualFallingBlockId);
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    if (!PaperCompat.hasClass("io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent")
        || !PaperCompat.hasClass("io.papermc.paper.event.entity.EntityKnockbackEvent")
        || !PaperCompat.hasClass("io.papermc.paper.event.entity.EntityLungeEvent")
        || !PaperCompat.hasClass("io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent")) {
      return List.of();
    }

    return List.of(new PaperCombatListener());
  }

  private final class PaperCombatListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityAttemptSmashAttackEvent e) {
      if (!isSuccessfulSmash(e.getResult(), e.getOriginalResult()) || !(e.getEntity() instanceof Player p)) {
        return;
      }
      LivingEntity target = e.getTarget();
      shouldReturnForPlayer(p, () -> {
        addStat(p, "kinetics.smash.hits", 1);
        payCombatXp(p, target.getLocation(), getConfig().smashHitXp, "kinetics:smash");
      });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityLungeEvent e) {
      if (e.getEntity() instanceof Player p) {
        lungeStamps.put(p.getUniqueId(), M.ms());
      }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityPushedByEntityAttackEvent e) {
      if (!(e.getPushedBy() instanceof Player p)) {
        return;
      }
      shouldReturnForPlayer(p, e, () -> {
        double magnitude = e.getKnockback().length();
        if (!KineticsKnockback.qualifies(magnitude, getConfig().kbMinMagnitude)
            || !kbCooldowns.isReady(p.getUniqueId(), getConfig().kbCooldownMs)) {
          return;
        }
        kbCooldowns.mark(p.getUniqueId());
        boolean selfCaused = e.getPushedBy() == e.getEntity();
        double amount = KineticsKnockback.applySelfFactor(
            KineticsKnockback.dealtXp(getConfig().kbDealtBaseXp, magnitude, getConfig().kbXpCap),
            selfCaused,
            getConfig().selfKnockbackFactor);
        if (amount > 0) {
          xp(p, e.getEntity().getLocation(), amount, "kinetics:knockback-dealt");
        }
      });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityKnockbackEvent e) {
      if (!(e.getEntity() instanceof Player p)) {
        return;
      }
      shouldReturnForPlayer(p, e, () -> {
        double magnitude = e.getKnockback().length();
        if (!KineticsKnockback.qualifies(magnitude, getConfig().kbMinMagnitude)
            || !kbCooldowns.isReady(p.getUniqueId(), getConfig().kbCooldownMs)) {
          return;
        }
        kbCooldowns.mark(p.getUniqueId());
        double amount = KineticsKnockback.takenXp(getConfig().kbTakenBaseXp, magnitude, getConfig().kbXpCap);
        if (amount > 0) {
          xp(p, p.getLocation(), amount, "kinetics:knockback-taken");
        }
      });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getCause() == EntityDamageEvent.DamageCause.FALLING_BLOCK && e.getDamager() instanceof FallingBlock fallingBlock) {
      handleAnvilCrush(e, fallingBlock);
      return;
    }
    if (!(e.getDamager() instanceof Player p) || !checkValidEntity(e.getEntity().getType())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> handleMeleeHit(p, e));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  @ReceiveCancelledEvents
  public void on(EntityDamageEvent e) {
    if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      double fallDistance = p.getFallDistance();
      Block feet = p.getLocation().getBlock();
      Material feetMaterial = feet.getType();
      Material below = feet.getRelative(BlockFace.DOWN).getType();
      boolean mitigated = e.isCancelled() || e.getFinalDamage() < Math.max(0D, fallDistance - 3.0D);
      if (!mitigated && !KineticsMotion.isSoftLanding(feetMaterial) && !KineticsMotion.isSoftLanding(below)) {
        return;
      }
      double amount = KineticsMotion.breakFallXp(fallDistance, getConfig().breakFallXpPerBlock, getConfig().breakFallCap);
      if (amount > 0) {
        xp(p, p.getLocation(), amount, "kinetics:break-fall");
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Location from = e.getFrom();
    Location to = e.getTo();
    if (to == null || from.getWorld() != to.getWorld()
        || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
      return;
    }
    Player p = e.getPlayer();
    shouldReturnForPlayer(p, e, () -> handleMotion(p, from, to));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityPotionEffectEvent e) {
    if (!(PaperCompat.livingEntity(e) instanceof Player p)
        || !PotionEffectType.LEVITATION.equals(e.getModifiedType())
        || (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED)) {
      return;
    }
    PotionEffect newEffect = e.getNewEffect();
    if (newEffect == null) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!levitationCooldowns.isReady(p.getUniqueId(), getConfig().levitationCooldownMs)) {
        return;
      }
      levitationCooldowns.mark(p.getUniqueId());
      double amount = KineticsLevitation.receiveXp(
          getConfig().levitationReceiveXp,
          newEffect.getAmplifier(),
          normalizeDuration(newEffect.getDuration()),
          getConfig().levitationXpCap);
      if (amount > 0) {
        xp(p, p.getLocation(), amount, "kinetics:levitation-received");
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PotionSplashEvent e) {
    if (!(e.getPotion().getShooter() instanceof Player p)) {
      return;
    }
    PotionEffect levitation = findLevitation(e.getPotion().getEffects());
    if (levitation == null) {
      return;
    }
    int amplifier = levitation.getAmplifier();
    int duration = normalizeDuration(levitation.getDuration());
    int affectedCount = Math.min(LEVITATION_TARGET_CAP, e.getAffectedEntities().size());
    Location applicationLocation = e.getPotion().getLocation().clone();
    shouldReturnForPlayer(p, () -> payLevitationApplied(
        p, amplifier, duration, affectedCount, applicationLocation, null));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(AreaEffectCloudApplyEvent e) {
    AreaEffectCloud cloud = e.getEntity();
    if (!(cloud.getSource() instanceof Player p)) {
      return;
    }
    PotionEffect levitation = findLevitation(cloud.getCustomEffects());
    if (levitation == null) {
      return;
    }
    int amplifier = levitation.getAmplifier();
    int duration = normalizeDuration(levitation.getDuration());
    int affectedCount = Math.min(LEVITATION_TARGET_CAP, e.getAffectedEntities().size());
    Location applicationLocation = cloud.getLocation().clone();
    UUID cloudId = cloud.getUniqueId();
    shouldReturnForPlayer(p, () -> payLevitationApplied(
        p, amplifier, duration, affectedCount, applicationLocation, cloudId));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    Location placed = e.getBlockPlaced().getLocation();
    if (KineticsAnvils.isAnvil(e.getBlockPlaced().getType())) {
      shouldReturnForPlayer(p, e, () -> anvils.recordPlacement(placed, p.getUniqueId(), M.ms()));
      return;
    }
    anvils.clearPlacement(placed);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityChangeBlockEvent e) {
    if (!(e.getEntity() instanceof FallingBlock fallingBlock)) {
      return;
    }
    long now = M.ms();
    if (e.getTo() == Material.AIR && KineticsAnvils.isAnvil(e.getBlock().getType())) {
      anvils.beginFall(fallingBlock.getUniqueId(), e.getBlock().getLocation(), now);
      return;
    }
    if (KineticsAnvils.isAnvil(e.getTo())) {
      anvils.land(fallingBlock.getUniqueId(), e.getBlock().getLocation());
      return;
    }
    anvils.clearPlacement(e.getBlock().getLocation());
    anvils.clearFall(fallingBlock.getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent e) {
    transferPistonAnvils(e.getBlocks(), pistonMovementDirection(e.getDirection(), false));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent e) {
    transferPistonAnvils(e.getBlocks(), pistonMovementDirection(e.getDirection(), true));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    anvils.clearPlacement(e.getBlock().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockExplodeEvent e) {
    clearAnvilPlacements(e.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityExplodeEvent e) {
    clearAnvilPlacements(e.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    UUID entityId = e.getEntity().getUniqueId();
    if (e.getEntity() instanceof AreaEffectCloud) {
      rewardedLevitationClouds.remove(entityId);
    }
    if (e.getEntity() instanceof FallingBlock) {
      anvils.clearFall(entityId);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    PendingCrush pending = pendingCrushes.remove(e.getEntity().getUniqueId());
    if (pending == null) {
      return;
    }
    EntityDamageEvent lastDamage = e.getEntity().getLastDamageCause();
    UUID fallingBlockId = fallingBlockId(lastDamage);
    boolean kill = isCorrelatedAnvilDeath(
        lastDamage == null ? null : lastDamage.getCause(),
        fallingBlockId,
        pending.fallingBlockId());
    settleCrush(pending, kill, kill ? e.getEntity().getLocation() : pending.location());
  }

  @Override
  public void unregister() {
    ownerPulse.unregister();
    super.unregister();
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  private void handleMeleeHit(Player p, EntityDamageByEntityEvent e) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    Location at = e.getEntity().getLocation();
    if (isMace(hand)) {
      payCombatXp(p, at, getConfig().plainMaceHitXp, "kinetics:mace-hit");
      return;
    }
    if (!isSpear(hand)) {
      return;
    }
    if (p.isInsideVehicle()) {
      payCombatXp(p, at, getConfig().mountedChargeXp, "kinetics:mounted-charge");
      return;
    }
    Long lungeAt = lungeStamps.get(p.getUniqueId());
    Vector velocity = p.getVelocity();
    double horizontalSpeed = p.isSprinting() ? Math.hypot(velocity.getX(), velocity.getZ()) : 0D;
    if (isChargeHit(M.ms(), lungeAt == null ? 0L : lungeAt, getConfig().lungeChargeWindowMs, horizontalSpeed, getConfig().chargeMinSpeed)) {
      payCombatXp(p, at, getConfig().spearChargeXp, "kinetics:spear-charge");
      return;
    }
    double distance = p.getLocation().distance(at);
    if (isSweetRangeJab(distance, getConfig().sweetRangeMin, getConfig().sweetRangeMax)) {
      payCombatXp(p, at, getConfig().spearJabXp, "kinetics:spear-jab");
    }
  }

  private void payCombatXp(Player p, Location at, double amount, String rewardKey) {
    if (amount <= 0 || !combatCooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
      return;
    }
    combatCooldowns.mark(p.getUniqueId());
    xp(p, at, amount, rewardKey);
  }

  private void handleMotion(Player p, Location from, Location to) {
    long now = M.ms();
    double deltaY = to.getY() - from.getY();
    KineticsMotion.MotionState state = motionStates.getOrDefault(
        p.getUniqueId(), new KineticsMotion.MotionState(0L, 0, 0D, 0L));
    Material below = from.getBlock().getRelative(BlockFace.DOWN).getType();
    long lastBounceAtMs = state.lastBounceAtMs();
    int bounceChain = state.bounceChain();
    long lastLaunchAtMs = state.lastLaunchAtMs();
    if (state.lastDeltaY() < 0 && deltaY >= 0 && KineticsMotion.isBouncySurface(below)) {
      bounceChain = KineticsMotion.nextBounceChain(bounceChain, now, lastBounceAtMs, getConfig().bounceChainWindowMs);
      lastBounceAtMs = now;
      double amount = KineticsMotion.bounceXp(getConfig().bounceXp, bounceChain, getConfig().bounceChainBonus, getConfig().bounceCap);
      rewardMotion(p, to, now, amount, "kinetics:bounce");
    } else if (KineticsMotion.isLaunch(deltaY, state.lastDeltaY(), getConfig().launchMinDeltaY)
        && isLaunchSurface(below)
        && now >= lastLaunchAtMs
        && now - lastLaunchAtMs >= Math.max(0L, getConfig().motionRewardCooldownMs)) {
      lastLaunchAtMs = now;
      rewardMotion(p, to, now, getConfig().launchXp, "kinetics:launch");
    }
    motionStates.put(p.getUniqueId(), new KineticsMotion.MotionState(lastBounceAtMs, bounceChain, deltaY, lastLaunchAtMs));
  }

  private void rewardMotion(Player p, Location location, long now, double amount, String rewardKey) {
    if (!Double.isFinite(amount) || amount <= 0D) {
      return;
    }
    UUID playerId = p.getUniqueId();
    MotionRewardState previous = motionRewards.get(playerId);
    if (previous != null) {
      double distanceSquared = previous.horizontalDistanceSquared(location);
      if (!motionRewardReady(now, previous.atMs(), distanceSquared,
          getConfig().motionRewardCooldownMs, getConfig().motionRewardMinDistance)) {
        return;
      }
    }
    motionRewards.put(playerId, MotionRewardState.at(location, now));
    xp(p, location, amount, rewardKey);
  }

  private void payLevitationApplied(Player p, int amplifier, int duration, int affectedCount,
      Location applicationLocation, UUID cloudId) {
    if (affectedCount <= 0 || !levitationCooldowns.isReady(p.getUniqueId(), getConfig().levitationCooldownMs)) {
      return;
    }
    if (cloudId != null && !rewardedLevitationClouds.add(cloudId)) {
      return;
    }
    double amount = KineticsLevitation.applyXpForTargets(
        getConfig().levitationApplyXp,
        amplifier,
        duration,
        affectedCount,
        LEVITATION_TARGET_CAP,
        getConfig().levitationXpCap);
    if (amount <= 0) {
      if (cloudId != null) {
        rewardedLevitationClouds.remove(cloudId);
      }
      return;
    }
    levitationCooldowns.mark(p.getUniqueId());
    xp(p, applicationLocation, amount, "kinetics:levitation-applied");
  }

  private void handleAnvilCrush(EntityDamageByEntityEvent e, FallingBlock fallingBlock) {
    if (!(e.getEntity() instanceof LivingEntity victim)
        || !KineticsAnvils.isAnvil(fallingBlock.getBlockData().getMaterial())) {
      return;
    }
    long now = M.ms();
    UUID ownerId = anvils.ownerOf(fallingBlock.getUniqueId(), now, getConfig().anvilLedgerTtlMs);
    if (ownerId == null || victim.getUniqueId().equals(ownerId)) {
      return;
    }
    if (Bukkit.getPlayer(ownerId) == null) {
      return;
    }
    if (!anvilCooldowns.isReady(ownerId, getConfig().anvilCooldownMs)) {
      return;
    }
    String locationKey = locationKey(victim.getLocation());
    Long locationStamp = anvilLocationStamps.get(locationKey);
    if (locationStamp != null && now - locationStamp < getConfig().anvilLocationCooldownMs) {
      return;
    }
    anvilCooldowns.mark(ownerId);
    anvilLocationStamps.put(locationKey, now);
    pendingCrushes.put(victim.getUniqueId(), new PendingCrush(
        ownerId,
        fallingBlock.getUniqueId(),
        fallingBlock.getFallDistance(),
        e.getFinalDamage(),
        maxHealthOf(victim),
        victim.getLocation().clone(),
        now,
        captureShareRecipients(victim, ownerId)));
  }

  private List<UUID> captureShareRecipients(LivingEntity victim, UUID ownerId) {
    List<UUID> recipients = new ArrayList<>(ANVIL_SHARE_CAP);
    for (Player nearby : PaperCompat.nearbyPlayers(victim.getLocation(), getConfig().anvilShareRadius)) {
      UUID id = nearby.getUniqueId();
      if (id.equals(ownerId) || id.equals(victim.getUniqueId())) {
        continue;
      }
      recipients.add(id);
      if (recipients.size() >= ANVIL_SHARE_CAP) {
        break;
      }
    }
    return recipients;
  }

  private void settleCrush(PendingCrush pending, boolean kill, Location where) {
    double crushXp = KineticsAnvils.crushXp(
        getConfig().anvilBaseXp,
        pending.fallDistance(),
        getConfig().anvilFallFactor,
        pending.targetMaxHealth(),
        getConfig().anvilHealthFactor,
        pending.damageDealt(),
        kill,
        getConfig().anvilKillBonusMultiplier,
        getConfig().anvilPerEventCap);
    KineticsAnvils.CrushResult result = new KineticsAnvils.CrushResult(
        crushXp,
        kill && pending.fallDistance() >= getConfig().anvilAdvancementMinFall);
    if (result.xp() <= 0D) {
      return;
    }
    Location settlementLocation = where.clone();
    Player owner = Bukkit.getPlayer(pending.owner());
    if (owner != null) {
      shouldReturnForPlayer(owner, () -> payAnvilOwner(owner, settlementLocation, result));
    }

    double share = KineticsAnvils.shareXp(result.xp(), getConfig().anvilShareFactor);
    if (share <= 0D) {
      return;
    }
    for (UUID id : pending.shareRecipients()) {
      Player sharer = Bukkit.getPlayer(id);
      if (sharer != null) {
        shouldReturnForPlayer(sharer, () -> payAnvilShare(sharer, settlementLocation, share));
      }
    }
  }

  private void payAnvilOwner(Player owner, Location where, KineticsAnvils.CrushResult result) {
    xp(owner, where, result.xp(), "kinetics:anvil-crush");
    if (result.advancementQualifies()) {
      addStat(owner, "kinetics.anvil.deep-kills", 1);
    }
  }

  private void payAnvilShare(Player sharer, Location where, double share) {
    UUID id = sharer.getUniqueId();
    if (!anvilShareCooldowns.isReady(id, getConfig().anvilCooldownMs)) {
      return;
    }
    anvilShareCooldowns.mark(id);
    xp(sharer, where, share, "kinetics:anvil-share");
  }

  private void transferPistonAnvils(List<Block> blocks, BlockFace direction) {
    List<KineticsAnvils.PistonTransfer> transfers = new ArrayList<>(blocks.size());
    for (Block block : blocks) {
      if (KineticsAnvils.isAnvil(block.getType())) {
        transfers.add(new KineticsAnvils.PistonTransfer(
            block.getLocation(), block.getRelative(direction).getLocation()));
      }
    }
    anvils.transferPistons(transfers);
  }

  private void clearAnvilPlacements(List<Block> blocks) {
    for (Block block : blocks) {
      anvils.clearPlacement(block.getLocation());
    }
  }

  private void pulseKinetics(AdaptPlayer adaptPlayer, Player player, long elapsedMillis, long cadenceMillis) {
    sweep(M.ms());
    shouldReturnForPlayer(player, () -> {
      if (!player.hasPotionEffect(PotionEffectType.LEVITATION)) {
        return;
      }
      double amount = KineticsLevitation.pulseXp(getConfig().levitationPulseXp, elapsedMillis, cadenceMillis);
      if (amount > 0) {
        xpS(player, player.getLocation(), amount, "kinetics:levitation-airtime");
      }
    });
  }

  private void sweep(long now) {
    long last = lastSweepAtMs.get();
    if (now - last < SWEEP_INTERVAL_MS || !lastSweepAtMs.compareAndSet(last, now)) {
      return;
    }
    anvils.expire(now, getConfig().anvilLedgerTtlMs);
    anvilLocationStamps.values().removeIf(at -> now - at > getConfig().anvilLocationCooldownMs);
    List<PendingCrush> expired = new ArrayList<>(0);
    pendingCrushes.entrySet().removeIf(entry -> {
      if (now - entry.getValue().atMs() > PENDING_CRUSH_SETTLE_MS) {
        expired.add(entry.getValue());
        return true;
      }
      return false;
    });
    for (PendingCrush pending : expired) {
      settleCrush(pending, false, pending.location());
    }
  }

  private double maxHealthOf(LivingEntity entity) {
    if (Attributes.MAX_HEALTH == null) {
      return 20D;
    }
    IAttribute attribute = Version.get().getAttribute(entity, Attributes.MAX_HEALTH);
    return attribute == null ? 20D : attribute.getValue();
  }

  private UUID fallingBlockId(EntityDamageEvent damage) {
    if (damage instanceof EntityDamageByEntityEvent byEntity
        && byEntity.getDamager() instanceof FallingBlock fallingBlock) {
      return fallingBlock.getUniqueId();
    }
    return null;
  }

  private int normalizeDuration(int durationTicks) {
    return durationTicks < 0 ? 200 : durationTicks;
  }

  private PotionEffect findLevitation(Collection<PotionEffect> effects) {
    for (PotionEffect effect : effects) {
      if (PotionEffectType.LEVITATION.equals(effect.getType())) {
        return effect;
      }
    }
    return null;
  }

  private String locationKey(Location location) {
    String world = location.getWorld() == null ? "" : location.getWorld().getName();
    return world + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
  }

  private record PendingCrush(UUID owner, UUID fallingBlockId, double fallDistance, double damageDealt, double targetMaxHealth,
                              Location location, long atMs, List<UUID> shareRecipients) {
  }

  private record MotionRewardState(String world, double x, double z, long atMs) {
    private static MotionRewardState at(Location location, long atMs) {
      String world = location.getWorld() == null ? "" : location.getWorld().getName();
      return new MotionRewardState(world, location.getX(), location.getZ(), atMs);
    }

    private double horizontalDistanceSquared(Location location) {
      String currentWorld = location.getWorld() == null ? "" : location.getWorld().getName();
      if (!world.equals(currentWorld)) {
        return Double.MAX_VALUE;
      }
      double deltaX = location.getX() - x;
      double deltaZ = location.getZ() - z;
      return deltaX * deltaX + deltaZ * deltaZ;
    }
  }

  @NoArgsConstructor
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&6";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Smash Hit Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double smashHitXp = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Plain Mace Hit Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double plainMaceHitXp = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spear Jab Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spearJabXp = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spear Charge Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spearChargeXp = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Charge Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedChargeXp = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sweet Range Min for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sweetRangeMin = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sweet Range Max for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sweetRangeMax = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Min Speed for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargeMinSpeed = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lunge Charge Window Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long lungeChargeWindowMs = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Break Fall Xp Per Block for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breakFallXpPerBlock = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Break Fall Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breakFallCap = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bounceXp = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Chain Bonus for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bounceChainBonus = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Chain Window Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long bounceChainWindowMs = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bounceCap = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Launch Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double launchXp = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Launch Min Delta Y for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double launchMinDeltaY = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum delay in milliseconds between bounce or launch rewards.", impact = "Higher values reduce repeated motion rewards from rapid mechanisms.")
    long motionRewardCooldownMs = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal travel in blocks between bounce or launch rewards.", impact = "Higher values require players to traverse farther before motion rewards rearm.")
    double motionRewardMinDistance = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kb Dealt Base Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbDealtBaseXp = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kb Taken Base Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbTakenBaseXp = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kb Min Magnitude for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbMinMagnitude = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kb Xp Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbXpCap = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kb Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long kbCooldownMs = 750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Self Knockback Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double selfKnockbackFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Levitation Receive Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationReceiveXp = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Levitation Apply Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationApplyXp = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Levitation Pulse Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationPulseXp = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Levitation Xp Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationXpCap = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Levitation Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long levitationCooldownMs = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Base Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilBaseXp = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Fall Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilFallFactor = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Health Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilHealthFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Kill Bonus Multiplier for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilKillBonusMultiplier = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Per Event Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilPerEventCap = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long anvilCooldownMs = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Location Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long anvilLocationCooldownMs = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Share Radius for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilShareRadius = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Share Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilShareFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Ledger Ttl Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long anvilLedgerTtlMs = 120000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Advancement Min Fall for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilAdvancementMinFall = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anvil Drop Reward for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilDropReward = 500;
  }
}
