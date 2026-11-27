package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
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
import art.arcane.adapt.content.adaptation.kinetics.blocks.KineticsBoon;
import art.arcane.adapt.content.adaptation.kinetics.blocks.KineticsFightStrike;
import art.arcane.adapt.content.adaptation.kinetics.blocks.KineticsFuel;
import art.arcane.adapt.content.adaptation.kinetics.blocks.KineticsState;
import art.arcane.adapt.content.adaptation.kinetics.blocks.KineticsTrigger;
import art.arcane.adapt.content.adaptation.kinetics.blocks.KineticsWorkStrike;
import art.arcane.adapt.content.adaptation.kinetics.upgraded.Comet;
import art.arcane.adapt.content.adaptation.kinetics.upgraded.Featherweight;
import art.arcane.adapt.content.adaptation.kinetics.upgraded.Lancer;
import art.arcane.adapt.content.skill.kinetics.KineticsAnvils;
import art.arcane.adapt.content.skill.kinetics.KineticsKnockback;
import art.arcane.adapt.content.skill.kinetics.KineticsLevitation;
import art.arcane.adapt.content.skill.kinetics.KineticsMotion;
import art.arcane.adapt.content.skill.kinetics.KineticsWorkstations;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.AreaEffectCloudApplyEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SkillKinetics extends SimpleSkill<SkillKinetics.Config> {
  public static final Id ID = Id.adapt("kinetics");
  private final XpSource smash = xpSource("smash", Text.key("adapt.xp_guide.kinetics.mace"));
  private final XpSource maceHit = xpSource("mace-hit", Text.key("adapt.xp_guide.kinetics.mace"));
  private final XpSource mountedCharge = xpSource("mounted-charge", Text.key("adapt.xp_guide.kinetics.spear"));
  private final XpSource spearCharge = xpSource("spear-charge", Text.key("adapt.xp_guide.kinetics.spear"));
  private final XpSource spearJab = xpSource("spear-jab", Text.key("adapt.xp_guide.kinetics.spear"));
  private final XpSource knockbackDealt = xpSource("knockback-dealt", Text.key("adapt.xp_guide.kinetics.knockback"));
  private final XpSource knockbackTaken = xpSource("knockback-taken", Text.key("adapt.xp_guide.kinetics.knockback"));
  private final XpSource breakFall = xpSource("break-fall", Text.key("adapt.xp_guide.kinetics.fall"));
  private final XpSource fall = xpSource("fall", Text.key("adapt.xp_guide.kinetics.fall"));
  private final XpSource bounce = xpSource("bounce", Text.key("adapt.xp_guide.kinetics.fall"));
  private final XpSource launch = xpSource("launch", Text.key("adapt.xp_guide.kinetics.fall"));
  private final XpSource levitationReceived = xpSource("levitation-received", Text.key("adapt.xp_guide.kinetics.levitation"));
  private final XpSource levitationApplied = xpSource("levitation-applied", Text.key("adapt.xp_guide.kinetics.levitation"));
  private final XpSource levitationAirtime = xpSource("levitation-airtime", Text.key("adapt.xp_guide.kinetics.levitation"));
  private final XpSource anvilCrush = xpSource("anvil-crush", Text.key("adapt.xp_guide.kinetics.anvil"));
  private final XpSource anvilShare = xpSource("anvil-share", Text.key("adapt.xp_guide.kinetics.anvil"));
  private final XpSource anvilUse = xpSource("anvil-use", Text.key("adapt.xp_guide.kinetics.anvil"));
  private final XpSource grindstoneUse = xpSource("grindstone-use", Text.key("adapt.xp_guide.kinetics.anvil"));
  private static final long PENDING_CRUSH_SETTLE_MS = 3000L;
  private static final long SWEEP_INTERVAL_MS = 5000L;
  private static final int ANVIL_SHARE_CAP = 8;
  private static final int LEVITATION_TARGET_CAP = 8;
  private static final int RECENT_LANDINGS = 3;
  private static final Id LEVITATION = Id.minecraft("levitation");
  private static final Id AIR = Id.minecraft("air");
  private static final Id FALLING_BLOCK = Id.minecraft("falling_block");
  private static final Id AREA_EFFECT_CLOUD = Id.minecraft("area_effect_cloud");
  private static final Set<Id> LAUNCH_SURFACES = Set.of(Id.minecraft("slime_block"), Id.minecraft("piston"),
      Id.minecraft("sticky_piston"), Id.minecraft("piston_head"), Id.minecraft("moving_piston"));

  private final Cooldowns combatCooldowns = cooldowns();
  private final Cooldowns kbCooldowns = cooldowns();
  private final Cooldowns levitationCooldowns = cooldowns();
  private final Cooldowns anvilCooldowns = cooldowns();
  private final Cooldowns anvilShareCooldowns = cooldowns();
  private final Cooldowns fallCooldowns = cooldowns();
  private final Map<UUID, List<Loc>> recentLandings = playerState();
  private final Map<UUID, Long> lungeStamps = playerState();
  private final Map<UUID, KineticsMotion.MotionState> motionStates = playerState();
  private final Map<UUID, MotionRewardState> motionRewards = playerState();
  private final KineticsAnvils anvils = new KineticsAnvils();
  private final Set<UUID> rewardedLevitationClouds = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<UUID, PendingCrush> pendingCrushes = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> anvilLocationStamps = new ConcurrentHashMap<>();
  private final AtomicLong lastSweepAtMs = new AtomicLong();


  public SkillKinetics() {
    super(ID);
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFAA00));
    setInterval(1000);
    setIcon(ContentIds.itemOr(Id.minecraft("mace"), Id.minecraft("anvil")));
    registerAdaptation(KineticsMoonJump.ID, KineticsMoonJump::new);
    registerAdaptation(KineticsRubberSoul.ID, KineticsRubberSoul::new);
    registerAdaptation(KineticsSoftCatch.ID, KineticsSoftCatch::new);
    registerAdaptation(KineticsSurfaceSkate.ID, KineticsSurfaceSkate::new);
    registerAdaptation(KineticsTerminalToggle.ID, KineticsTerminalToggle::new);
    registerAdaptation(KineticsHeavyFrame.ID, KineticsHeavyFrame::new);
    registerAdaptation(KineticsMassShift.ID, KineticsMassShift::new);

    registerAdaptation(KineticsMeteorCadence.ID, KineticsMeteorCadence::new);
    registerAdaptation(KineticsBreachwright.ID, KineticsBreachwright::new);
    registerAdaptation(KineticsWindburst.ID, KineticsWindburst::new);
    registerAdaptation(KineticsQuakeGuard.ID, KineticsQuakeGuard::new);
    registerAdaptation(KineticsReboundAnvil.ID, KineticsReboundAnvil::new);

    registerAdaptation(KineticsPhalanxReach.ID, KineticsPhalanxReach::new);
    registerAdaptation(KineticsChargeLance.ID, KineticsChargeLance::new);
    registerAdaptation(KineticsImpalePin.ID, KineticsImpalePin::new);
    registerAdaptation(KineticsLungeConductor.ID, KineticsLungeConductor::new);
    registerAdaptation(KineticsMountedShock.ID, KineticsMountedShock::new);
    registerAdaptation(KineticsDeadZone.ID, KineticsDeadZone::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.kinetics"), Text.key("adapt.mutated.adjective.kinetics"))
        .trigger(TriggerKind.EITHER, KineticsTrigger::new)
        .strikes(KineticsFightStrike::new, KineticsWorkStrike::new)
        .boon(KineticsBoon::new)
        .fuel(KineticsFuel::new)
        .state(KineticsState::new)
        .upgrade(Comet.ID, Comet::new,
            List.of(KineticsMeteorCadence.ID, KineticsWindburst.ID, KineticsBreachwright.ID), Set.of())
        .upgrade(Lancer.ID, Lancer::new,
            List.of(KineticsChargeLance.ID, KineticsPhalanxReach.ID, KineticsImpalePin.ID), Set.of())
        .upgrade(Featherweight.ID, Featherweight::new,
            List.of(KineticsMoonJump.ID, KineticsRubberSoul.ID, KineticsSoftCatch.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("block.piston.extend"), Id.minecraft("entity.slime.jump_small"),
            Id.minecraft("block.slime_block.fall"), Id.minecraft("entity.slime.squish_small")))
        .tint(Rgb.of(0xffe68a))
        // Mass Shift, and Terminal Toggle's double-tap preference.
        .reserve(BarterGesture.SNEAK_SWAP, BarterGesture.DOUBLE_SNEAK)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("anvil"))
        .key("challenge_kinetics_anvil_drop")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_kinetics_anvil_drop", "kinetics.anvil.deep-kills", 1, () -> getConfig().anvilDropReward);
    pulseLearners(this::getInterval, this::pulseKinetics);
    registerGroup(Text.key("adapt.skill.kinetics.group.spear"), List.of(Id.parseAdapt("adapt:kinetics/phalanx_reach"), Id.parseAdapt("adapt:kinetics/charge_lance"), Id.parseAdapt("adapt:kinetics/lunge_conductor"), Id.parseAdapt("adapt:kinetics/impale_pin"), Id.parseAdapt("adapt:kinetics/mounted_shock"), Id.parseAdapt("adapt:kinetics/dead_zone")));
    registerGroup(Text.key("adapt.skill.kinetics.group.mace"), List.of(Id.parseAdapt("adapt:kinetics/meteor_cadence"), Id.parseAdapt("adapt:kinetics/breachwright"), Id.parseAdapt("adapt:kinetics/windburst"), Id.parseAdapt("adapt:kinetics/quake_guard"), Id.parseAdapt("adapt:kinetics/rebound_anvil"), Id.parseAdapt("adapt:kinetics/heavy_frame")));
    registerGroup(Text.key("adapt.skill.kinetics.group.movement"), List.of(Id.parseAdapt("adapt:kinetics/moon_jump"), Id.parseAdapt("adapt:kinetics/rubber_soul"), Id.parseAdapt("adapt:kinetics/soft_catch"), Id.parseAdapt("adapt:kinetics/surface_skate"), Id.parseAdapt("adapt:kinetics/terminal_toggle"), Id.parseAdapt("adapt:kinetics/mass_shift")));
  }


  static boolean isSuccessfulSmash(UseResult result, boolean originalResult) {
    return result == UseResult.ALLOW || (result == UseResult.DEFAULT && originalResult);
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

  static boolean isLaunchSurface(Id material) {
    return material != null && LAUNCH_SURFACES.contains(material);
  }

  static BlockFace pistonMovementDirection(BlockFace direction, boolean retracting) {
    return retracting ? direction.opposite() : direction;
  }

  static boolean isCorrelatedAnvilDeath(DamageCause cause, UUID actualFallingBlockId,
      UUID expectedFallingBlockId) {
    return cause == DamageCause.FALLING_BLOCK
        && expectedFallingBlockId != null
        && expectedFallingBlockId.equals(actualFallingBlockId);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!isSuccessfulSmash(e.result(), e.originalResult()) || !(e.entity() instanceof PlayerRef p)) {
      return;
    }
    LivingRef target = e.target();
    shouldReturnForPlayer(p, () -> {
      addStat(p, "kinetics.smash.hits", 1);
      payCombatXp(p, target.location(), getConfig().smashHitXp, smash);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityLungeEvent e) {
    if (e.entity() instanceof PlayerRef p) {
      lungeStamps.put(p.uuid(), System.currentTimeMillis());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPushedByEntityAttackEvent e) {
    if (!(e.pushedBy() instanceof PlayerRef p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      double magnitude = e.knockback().length();
      if (!KineticsKnockback.qualifies(magnitude, getConfig().kbMinMagnitude)
          || !kbCooldowns.isReady(p.uuid(), getConfig().kbCooldownMs)) {
        return;
      }
      kbCooldowns.mark(p.uuid());
      boolean selfCaused = p.uuid().equals(e.entity().uuid());
      double amount = KineticsKnockback.applySelfFactor(
          KineticsKnockback.dealtXp(getConfig().kbDealtBaseXp, magnitude, getConfig().kbXpCap),
          selfCaused,
          getConfig().selfKnockbackFactor);
      if (amount > 0) {
        knockbackDealt.award(p, e.entity().location(), amount);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityKnockbackEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      double magnitude = e.knockback().length();
      if (!KineticsKnockback.qualifies(magnitude, getConfig().kbMinMagnitude)
          || !kbCooldowns.isReady(p.uuid(), getConfig().kbCooldownMs)) {
        return;
      }
      kbCooldowns.mark(p.uuid());
      double amount = KineticsKnockback.takenXp(getConfig().kbTakenBaseXp, magnitude, getConfig().kbXpCap);
      if (amount > 0) {
        knockbackTaken.award(p, p.location(), amount);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.cause() == DamageCause.FALLING_BLOCK && isFallingBlock(e.damager())) {
      handleAnvilCrush(e, e.damager());
      return;
    }
    if (!(e.damager() instanceof PlayerRef p) || !checkValidEntity(e.entity())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> handleMeleeHit(p, e));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  @ReceiveCancelledEvents
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.FALL || !(e.entity() instanceof PlayerRef p)) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      double fallDistance = p.fallDistance();
      BlockRef feet = p.world().blockAt(p.location());
      Id feetMaterial = feet.type();
      Id below = feet.relative(BlockFace.DOWN).type();
      boolean mitigated = e.isCancelled() || e.finalDamage() < Math.max(0D, fallDistance - 3.0D);
      if (!mitigated && !KineticsMotion.isSoftLanding(feetMaterial) && !KineticsMotion.isSoftLanding(below)) {
        rewardHardLanding(p, fallDistance, e.finalDamage());
        return;
      }
      double amount = KineticsMotion.breakFallXp(fallDistance, getConfig().breakFallXpPerBlock, getConfig().breakFallCap);
      if (amount > 0) {
        breakFall.award(p, p.location(), amount);
      }
    });
  }

  private void rewardHardLanding(PlayerRef p, double fallDistance, double damage) {
    if (damage <= 0D || !fallCooldowns.isReady(p.uuid(), getConfig().fallXpCooldownMs)) {
      return;
    }
    Loc landing = p.location();
    List<Loc> landings = recentLandings.computeIfAbsent(p.uuid(), ignored -> new ArrayList<>());
    if (KineticsMotion.nearAnyLanding(landings, landing, getConfig().fallXpRepeatRadius)) {
      return;
    }
    double amount = KineticsMotion.fallXp(fallDistance, getConfig().fallXpFreeBlocks, getConfig().fallXpPerBlock,
        getConfig().fallXpCap);
    if (amount <= 0D) {
      return;
    }
    fallCooldowns.mark(p.uuid());
    landings.add(landing);
    if (landings.size() > RECENT_LANDINGS) {
      landings.removeFirst();
    }
    fall.award(p, landing, amount);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Loc from = e.from();
    Loc to = e.to();
    if (to == null || !from.sameWorld(to)
        || (from.x() == to.x() && from.y() == to.y() && from.z() == to.z())) {
      return;
    }
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> handleMotion(p, from, to));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPotionEffectEvent e) {
    if (!(e.entity() instanceof PlayerRef p)
        || !LEVITATION.equals(e.modifiedType())
        || (e.action() != EntityPotionEffectEvent.Action.ADDED && e.action() != EntityPotionEffectEvent.Action.CHANGED)) {
      return;
    }
    EffectInstance newEffect = e.newEffect();
    if (newEffect == null) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!levitationCooldowns.isReady(p.uuid(), getConfig().levitationCooldownMs)) {
        return;
      }
      levitationCooldowns.mark(p.uuid());
      double amount = KineticsLevitation.receiveXp(
          getConfig().levitationReceiveXp,
          newEffect.amplifier(),
          normalizeDuration(newEffect.durationTicks()),
          getConfig().levitationXpCap);
      if (amount > 0) {
        levitationReceived.award(p, p.location(), amount);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PotionSplashEvent e) {
    if (!(e.potion().shooter() instanceof PlayerRef p)) {
      return;
    }
    EffectInstance levitation = findLevitation(e.effects());
    if (levitation == null) {
      return;
    }
    int amplifier = levitation.amplifier();
    int duration = normalizeDuration(levitation.durationTicks());
    int affectedCount = Math.min(LEVITATION_TARGET_CAP, e.affected().size());
    Loc applicationLocation = e.potion().location();
    shouldReturnForPlayer(p, () -> payLevitationApplied(
        p, amplifier, duration, affectedCount, applicationLocation, null));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AreaEffectCloudApplyEvent e) {
    EntityRef cloud = e.cloud();
    if (!(e.source() instanceof PlayerRef p)) {
      return;
    }
    EffectInstance levitation = findLevitation(e.customEffects());
    if (levitation == null) {
      return;
    }
    int amplifier = levitation.amplifier();
    int duration = normalizeDuration(levitation.durationTicks());
    int affectedCount = Math.min(LEVITATION_TARGET_CAP, e.affected().size());
    Loc applicationLocation = cloud.location();
    UUID cloudId = cloud.uuid();
    shouldReturnForPlayer(p, () -> payLevitationApplied(
        p, amplifier, duration, affectedCount, applicationLocation, cloudId));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    Loc placed = e.block().location();
    if (KineticsAnvils.isAnvil(e.block().type())) {
      shouldReturnForPlayer(p, e, () -> anvils.recordPlacement(placed, p.uuid(), System.currentTimeMillis()));
      return;
    }
    anvils.clearPlacement(placed);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityChangeBlockEvent e) {
    if (!isFallingBlock(e.entity())) {
      return;
    }
    EntityRef fallingBlock = e.entity();
    long now = System.currentTimeMillis();
    Id to = e.to() == null ? null : e.to().type();
    if (AIR.equals(to) && KineticsAnvils.isAnvil(e.block().type())) {
      anvils.beginFall(fallingBlock.uuid(), e.block().location(), now);
      return;
    }
    if (KineticsAnvils.isAnvil(to)) {
      anvils.land(fallingBlock.uuid(), e.block().location());
      return;
    }
    anvils.clearPlacement(e.block().location());
    anvils.clearFall(fallingBlock.uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPistonEvent e) {
    transferPistonAnvils(e.blocks(), pistonMovementDirection(e.direction(), !e.extending()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    anvils.clearPlacement(e.block().location());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryClickEvent e) {
    InventoryKind kind = e.viewKind();
    if (e.rawSlot() != 2 || (kind != InventoryKind.ANVIL && kind != InventoryKind.GRINDSTONE)
        || !ItemKinds.isItem(e.currentItem()) || e.action() == InventoryAction.NOTHING || e.action() == InventoryAction.CLONE_STACK) {
      return;
    }
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      InventoryRef menu = p.openMenuInventory();
      if (menu == null) {
        return;
      }
      if (kind == InventoryKind.ANVIL) {
        int levelsBefore = p.level();
        AdaptApi.scheduler().runEntity(p, () -> shouldReturnForPlayer(p, () -> anvilUse.award(p,
            KineticsWorkstations.anvilXp(levelsBefore, p.level(), getConfig().anvilUseXpPerLevel))), 1);
        return;
      }
      int removed = KineticsWorkstations.removedEnchantLevels(menu.item(0), menu.item(1), e.currentItem());
      if (removed <= 0) {
        return;
      }
      AdaptApi.scheduler().runEntity(p, () -> shouldReturnForPlayer(p, () -> {
        if (ItemKinds.isItem(menu.item(0)) || ItemKinds.isItem(menu.item(1))) {
          return;
        }
        grindstoneUse.award(p, KineticsWorkstations.grindstoneXp(removed, getConfig().grindstoneXpPerEnchantLevel));
      }), 1);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockExplodeEvent e) {
    clearAnvilPlacements(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityExplodeEvent e) {
    clearAnvilPlacements(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    EntityRef entity = e.entity();
    UUID entityId = entity.uuid();
    if (AREA_EFFECT_CLOUD.equals(entity.type())) {
      rewardedLevitationClouds.remove(entityId);
    }
    if (FALLING_BLOCK.equals(entity.type())) {
      anvils.clearFall(entityId);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    PendingCrush pending = pendingCrushes.remove(e.entity().uuid());
    if (pending == null) {
      return;
    }
    UUID fallingBlockId = isFallingBlock(e.directDamager()) ? e.directDamager().uuid() : null;
    boolean kill = isCorrelatedAnvilDeath(e.cause(), fallingBlockId, pending.fallingBlockId());
    settleCrush(pending, kill, kill ? e.entity().location() : pending.location());
  }

  @Override
  public void unregister() {
    super.unregister();
  }


  private void handleMeleeHit(PlayerRef p, EntityDamageByEntityEvent e) {
    ItemRef hand = p.inventory().mainHand();
    Loc at = e.entity().location();
    if (ItemKinds.isMace(hand)) {
      payCombatXp(p, at, getConfig().plainMaceHitXp, maceHit);
      return;
    }
    if (!ItemKinds.isSpear(hand)) {
      return;
    }
    if (p.vehicle() != null) {
      payCombatXp(p, at, getConfig().mountedChargeXp, mountedCharge);
      return;
    }
    Long lungeAt = lungeStamps.get(p.uuid());
    Vec3 velocity = p.velocity();
    double horizontalSpeed = p.isSprinting() ? Math.hypot(velocity.x(), velocity.z()) : 0D;
    if (isChargeHit(System.currentTimeMillis(), lungeAt == null ? 0L : lungeAt, getConfig().lungeChargeWindowMs, horizontalSpeed, getConfig().chargeMinSpeed)) {
      payCombatXp(p, at, getConfig().spearChargeXp, spearCharge);
      return;
    }
    double distance = p.location().distance(at);
    if (isSweetRangeJab(distance, getConfig().sweetRangeMin, getConfig().sweetRangeMax)) {
      payCombatXp(p, at, getConfig().spearJabXp, spearJab);
    }
  }

  private void payCombatXp(PlayerRef p, Loc at, double amount, XpSource source) {
    if (amount <= 0 || !combatCooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    combatCooldowns.mark(p.uuid());
    source.award(p, at, amount);
  }

  private void handleMotion(PlayerRef p, Loc from, Loc to) {
    long now = System.currentTimeMillis();
    double deltaY = to.y() - from.y();
    KineticsMotion.MotionState state = motionStates.getOrDefault(
        p.uuid(), new KineticsMotion.MotionState(0L, 0, 0D, 0L));
    Id below = from.world().blockAt(from).relative(BlockFace.DOWN).type();
    long lastBounceAtMs = state.lastBounceAtMs();
    int bounceChain = state.bounceChain();
    long lastLaunchAtMs = state.lastLaunchAtMs();
    if (state.lastDeltaY() < 0 && deltaY >= 0 && KineticsMotion.isBouncySurface(below)) {
      bounceChain = KineticsMotion.nextBounceChain(bounceChain, now, lastBounceAtMs, getConfig().bounceChainWindowMs);
      lastBounceAtMs = now;
      double amount = KineticsMotion.bounceXp(getConfig().bounceXp, bounceChain, getConfig().bounceChainBonus, getConfig().bounceCap);
      rewardMotion(p, to, now, amount, bounce);
    } else if (KineticsMotion.isLaunch(deltaY, state.lastDeltaY(), getConfig().launchMinDeltaY)
        && isLaunchSurface(below)
        && now >= lastLaunchAtMs
        && now - lastLaunchAtMs >= Math.max(0L, getConfig().motionRewardCooldownMs)) {
      lastLaunchAtMs = now;
      rewardMotion(p, to, now, getConfig().launchXp, launch);
    }
    motionStates.put(p.uuid(), new KineticsMotion.MotionState(lastBounceAtMs, bounceChain, deltaY, lastLaunchAtMs));
  }

  private void rewardMotion(PlayerRef p, Loc location, long now, double amount, XpSource source) {
    if (!Double.isFinite(amount) || amount <= 0D) {
      return;
    }
    UUID playerId = p.uuid();
    MotionRewardState previous = motionRewards.get(playerId);
    if (previous != null) {
      double distanceSquared = previous.horizontalDistanceSquared(location);
      if (!motionRewardReady(now, previous.atMs(), distanceSquared,
          getConfig().motionRewardCooldownMs, getConfig().motionRewardMinDistance)) {
        return;
      }
    }
    motionRewards.put(playerId, MotionRewardState.at(location, now));
    source.award(p, location, amount);
  }

  private void payLevitationApplied(PlayerRef p, int amplifier, int duration, int affectedCount,
      Loc applicationLocation, UUID cloudId) {
    if (affectedCount <= 0 || !levitationCooldowns.isReady(p.uuid(), getConfig().levitationCooldownMs)) {
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
    levitationCooldowns.mark(p.uuid());
    levitationApplied.award(p, applicationLocation, amount);
  }

  private void handleAnvilCrush(EntityDamageByEntityEvent e, EntityRef fallingBlock) {
    if (!(e.entity() instanceof LivingRef victim)) {
      return;
    }
    long now = System.currentTimeMillis();
    UUID ownerId = anvils.ownerOf(fallingBlock.uuid(), now, getConfig().anvilLedgerTtlMs);
    if (ownerId == null || victim.uuid().equals(ownerId)) {
      return;
    }
    if (AdaptApi.platform().host().player(ownerId) == null) {
      return;
    }
    if (!anvilCooldowns.isReady(ownerId, getConfig().anvilCooldownMs)) {
      return;
    }
    String locationKey = locationKey(victim.location());
    Long locationStamp = anvilLocationStamps.get(locationKey);
    if (locationStamp != null && now - locationStamp < getConfig().anvilLocationCooldownMs) {
      return;
    }
    anvilCooldowns.mark(ownerId);
    anvilLocationStamps.put(locationKey, now);
    pendingCrushes.put(victim.uuid(), new PendingCrush(
        ownerId,
        fallingBlock.uuid(),
        fallingBlock.fallDistance(),
        e.finalDamage(),
        maxHealthOf(victim),
        victim.location(),
        now,
        captureShareRecipients(victim, ownerId)));
  }

  private List<UUID> captureShareRecipients(LivingRef victim, UUID ownerId) {
    List<UUID> recipients = new ArrayList<>(ANVIL_SHARE_CAP);
    for (PlayerRef nearby : nearbyPlayers(victim.location(), getConfig().anvilShareRadius)) {
      UUID id = nearby.uuid();
      if (id.equals(ownerId) || id.equals(victim.uuid())) {
        continue;
      }
      recipients.add(id);
      if (recipients.size() >= ANVIL_SHARE_CAP) {
        break;
      }
    }
    return recipients;
  }

  private static List<PlayerRef> nearbyPlayers(Loc center, double radius) {
    WorldRef world = center.world();
    List<PlayerRef> found = new ArrayList<>();
    if (world == null || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(center, radius, radius))) {
      return found;
    }
    for (EntityRef entity : world.nearbyEntities(center.position(), radius, radius, radius)) {
      if (entity instanceof PlayerRef player) {
        found.add(player);
      }
    }
    return found;
  }

  private void settleCrush(PendingCrush pending, boolean kill, Loc where) {
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
    PlayerRef owner = AdaptApi.platform().host().player(pending.owner());
    if (owner != null) {
      shouldReturnForPlayer(owner, () -> payAnvilOwner(owner, where, result));
    }

    double share = KineticsAnvils.shareXp(result.xp(), getConfig().anvilShareFactor);
    if (share <= 0D) {
      return;
    }
    for (UUID id : pending.shareRecipients()) {
      PlayerRef sharer = AdaptApi.platform().host().player(id);
      if (sharer != null) {
        shouldReturnForPlayer(sharer, () -> payAnvilShare(sharer, where, share));
      }
    }
  }

  private void payAnvilOwner(PlayerRef owner, Loc where, KineticsAnvils.CrushResult result) {
    anvilCrush.award(owner, where, result.xp());
    if (result.advancementQualifies()) {
      addStat(owner, "kinetics.anvil.deep-kills", 1);
    }
  }

  private void payAnvilShare(PlayerRef sharer, Loc where, double share) {
    UUID id = sharer.uuid();
    if (!anvilShareCooldowns.isReady(id, getConfig().anvilCooldownMs)) {
      return;
    }
    anvilShareCooldowns.mark(id);
    anvilShare.award(sharer, where, share);
  }

  private void transferPistonAnvils(List<BlockRef> blocks, BlockFace direction) {
    List<KineticsAnvils.PistonTransfer> transfers = new ArrayList<>(blocks.size());
    for (BlockRef block : blocks) {
      if (KineticsAnvils.isAnvil(block.type())) {
        transfers.add(new KineticsAnvils.PistonTransfer(
            block.location(), block.relative(direction).location()));
      }
    }
    anvils.transferPistons(transfers);
  }

  private void clearAnvilPlacements(List<BlockRef> blocks) {
    for (BlockRef block : blocks) {
      anvils.clearPlacement(block.location());
    }
  }

  private void pulseKinetics(PlayerRef player, long elapsedMillis) {
    long cadenceMillis = Math.max(50L, getInterval());
    sweep(System.currentTimeMillis());
    shouldReturnForPlayer(player, () -> {
      if (!player.hasEffect(LEVITATION)) {
        return;
      }
      double amount = KineticsLevitation.pulseXp(getConfig().levitationPulseXp, elapsedMillis, cadenceMillis);
      if (amount > 0) {
        levitationAirtime.awardSilent(player, player.location(), amount);
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

  private static boolean isFallingBlock(EntityRef entity) {
    return entity != null && FALLING_BLOCK.equals(entity.type());
  }

  private double maxHealthOf(LivingRef entity) {
    return entity.hasAttribute(Attributes.MAX_HEALTH) ? entity.attributeValue(Attributes.MAX_HEALTH) : 20D;
  }

  private int normalizeDuration(int durationTicks) {
    return durationTicks < 0 ? 200 : durationTicks;
  }

  private EffectInstance findLevitation(Collection<EffectInstance> effects) {
    for (EffectInstance effect : effects) {
      if (LEVITATION.equals(effect.type())) {
        return effect;
      }
    }
    return null;
  }

  private String locationKey(Loc location) {
    String world = location.world() == null ? "" : location.world().name();
    return world + ":" + location.blockX() + ":" + location.blockY() + ":" + location.blockZ();
  }

  private record PendingCrush(UUID owner, UUID fallingBlockId, double fallDistance, double damageDealt, double targetMaxHealth,
                              Loc location, long atMs, List<UUID> shareRecipients) {
  }

  private record MotionRewardState(String world, double x, double z, long atMs) {
    private static MotionRewardState at(Loc location, long atMs) {
      String world = location.world() == null ? "" : location.world().name();
      return new MotionRewardState(world, location.x(), location.z(), atMs);
    }

    private double horizontalDistanceSquared(Loc location) {
      String currentWorld = location.world() == null ? "" : location.world().name();
      if (!world.equals(currentWorld)) {
        return Double.MAX_VALUE;
      }
      double deltaX = location.x() - x;
      double deltaZ = location.z() - z;
      return deltaX * deltaX + deltaZ * deltaZ;
    }
  }

  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Cooldown Delay for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1000;
    @ConfigDoc(value = "Controls Smash Hit Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double smashHitXp = 24;
    @ConfigDoc(value = "Controls Plain Mace Hit Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double plainMaceHitXp = 6;
    @ConfigDoc(value = "Controls Spear Jab Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spearJabXp = 12;
    @ConfigDoc(value = "Controls Spear Charge Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spearChargeXp = 24;
    @ConfigDoc(value = "Controls Mounted Charge Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedChargeXp = 28;
    @ConfigDoc(value = "Controls Sweet Range Min for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sweetRangeMin = 3.0;
    @ConfigDoc(value = "Controls Sweet Range Max for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sweetRangeMax = 6.0;
    @ConfigDoc(value = "Controls Charge Min Speed for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargeMinSpeed = 0.18;
    @ConfigDoc(value = "Controls Lunge Charge Window Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long lungeChargeWindowMs = 1200;
    @ConfigDoc(value = "Controls Break Fall Xp Per Block for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breakFallXpPerBlock = 1.2;
    @ConfigDoc(value = "Controls Break Fall Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breakFallCap = 25;
    @ConfigDoc(value = "XP per block fallen past fallXpFreeBlocks when the fall hurts you.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fallXpPerBlock = 1.5;
    @ConfigDoc(value = "Blocks of a damaging fall that pay nothing.", impact = "Higher values require longer falls before they pay.")
    double fallXpFreeBlocks = 3;
    @ConfigDoc(value = "Most XP one damaging fall can pay.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fallXpCap = 40;
    @ConfigDoc(value = "Milliseconds between paid damaging falls.", impact = "Higher values reduce how often falls pay.")
    long fallXpCooldownMs = 30000;
    @ConfigDoc(value = "A damaging fall that lands within this many blocks of one of your last three paid landings pays nothing.", impact = "Higher values stop repeated drops from the same spot over a wider area.")
    double fallXpRepeatRadius = 8;
    @ConfigDoc(value = "Controls Bounce Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bounceXp = 8;
    @ConfigDoc(value = "Controls Bounce Chain Bonus for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bounceChainBonus = 4;
    @ConfigDoc(value = "Controls Bounce Chain Window Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long bounceChainWindowMs = 4000;
    @ConfigDoc(value = "Controls Bounce Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bounceCap = 40;
    @ConfigDoc(value = "Controls Launch Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double launchXp = 8;
    @ConfigDoc(value = "Controls Launch Min Delta Y for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double launchMinDeltaY = 0.6;
    @ConfigDoc(value = "Minimum delay in milliseconds between bounce or launch rewards.", impact = "Higher values reduce repeated motion rewards from rapid mechanisms.")
    long motionRewardCooldownMs = 1000;
    @ConfigDoc(value = "Minimum horizontal travel in blocks between bounce or launch rewards.", impact = "Higher values require players to traverse farther before motion rewards rearm.")
    double motionRewardMinDistance = 1.5;
    @ConfigDoc(value = "Controls Kb Dealt Base Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbDealtBaseXp = 6;
    @ConfigDoc(value = "Controls Kb Taken Base Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbTakenBaseXp = 3;
    @ConfigDoc(value = "Controls Kb Min Magnitude for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbMinMagnitude = 0.25;
    @ConfigDoc(value = "Controls Kb Xp Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double kbXpCap = 24;
    @ConfigDoc(value = "Controls Kb Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long kbCooldownMs = 750;
    @ConfigDoc(value = "Controls Self Knockback Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double selfKnockbackFactor = 0.35;
    @ConfigDoc(value = "Controls Levitation Receive Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationReceiveXp = 10;
    @ConfigDoc(value = "Controls Levitation Apply Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationApplyXp = 10;
    @ConfigDoc(value = "Controls Levitation Pulse Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationPulseXp = 1.6;
    @ConfigDoc(value = "Controls Levitation Xp Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double levitationXpCap = 30;
    @ConfigDoc(value = "Controls Levitation Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long levitationCooldownMs = 1500;
    @ConfigDoc(value = "Controls Anvil Base Xp for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilBaseXp = 20;
    @ConfigDoc(value = "Controls Anvil Fall Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilFallFactor = 6;
    @ConfigDoc(value = "Controls Anvil Health Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilHealthFactor = 0.6;
    @ConfigDoc(value = "Controls Anvil Kill Bonus Multiplier for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilKillBonusMultiplier = 1.5;
    @ConfigDoc(value = "Controls Anvil Per Event Cap for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilPerEventCap = 250;
    @ConfigDoc(value = "Controls Anvil Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long anvilCooldownMs = 4000;
    @ConfigDoc(value = "Controls Anvil Location Cooldown Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long anvilLocationCooldownMs = 8000;
    @ConfigDoc(value = "Controls Anvil Share Radius for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilShareRadius = 8;
    @ConfigDoc(value = "Controls Anvil Share Factor for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilShareFactor = 0.35;
    @ConfigDoc(value = "Controls Anvil Ledger Ttl Ms for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long anvilLedgerTtlMs = 120000;
    @ConfigDoc(value = "Controls Anvil Advancement Min Fall for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilAdvancementMinFall = 8;
    @ConfigDoc(value = "Controls Anvil Drop Reward for the Kinetics skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilDropReward = 500;
    @ConfigDoc(value = "XP per experience level spent taking an anvil result.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double anvilUseXpPerLevel = 15;
    @ConfigDoc(value = "XP per enchantment level a grindstone removes, curses excluded.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double grindstoneXpPerEnchantLevel = 10;

    public Config() {
    }
  }
}
