package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillKineticsTest {
  @Test
  void smashSuccessUsesTheEffectivePaperResult() {
    assertThat(SkillKinetics.isSuccessfulSmash(UseResult.ALLOW, false)).isTrue();
    assertThat(SkillKinetics.isSuccessfulSmash(UseResult.ALLOW, true)).isTrue();
    assertThat(SkillKinetics.isSuccessfulSmash(UseResult.DEFAULT, true)).isTrue();
    assertThat(SkillKinetics.isSuccessfulSmash(UseResult.DEFAULT, false)).isFalse();
    assertThat(SkillKinetics.isSuccessfulSmash(UseResult.DENY, true)).isFalse();
    assertThat(SkillKinetics.isSuccessfulSmash(null, true)).isFalse();
  }

  @Test
  void launchSurfaceIncludesSlimeAndEveryPistonState() {
    assertThat(SkillKinetics.isLaunchSurface(Id.minecraft("slime_block"))).isTrue();
    assertThat(SkillKinetics.isLaunchSurface(Id.minecraft("piston"))).isTrue();
    assertThat(SkillKinetics.isLaunchSurface(Id.minecraft("sticky_piston"))).isTrue();
    assertThat(SkillKinetics.isLaunchSurface(Id.minecraft("piston_head"))).isTrue();
    assertThat(SkillKinetics.isLaunchSurface(Id.minecraft("moving_piston"))).isTrue();
    assertThat(SkillKinetics.isLaunchSurface(Id.minecraft("honey_block"))).isFalse();
    assertThat(SkillKinetics.isLaunchSurface(Id.minecraft("stone"))).isFalse();
    assertThat(SkillKinetics.isLaunchSurface(null)).isFalse();
  }

  @Test
  void pistonMovementReversesOnlyForRetraction() {
    assertThat(SkillKinetics.pistonMovementDirection(BlockFace.EAST, false)).isEqualTo(BlockFace.EAST);
    assertThat(SkillKinetics.pistonMovementDirection(BlockFace.EAST, true)).isEqualTo(BlockFace.WEST);
    assertThat(SkillKinetics.pistonMovementDirection(BlockFace.UP, false)).isEqualTo(BlockFace.UP);
    assertThat(SkillKinetics.pistonMovementDirection(BlockFace.UP, true)).isEqualTo(BlockFace.DOWN);
  }

  @Test
  void anvilDeathRequiresTheExactFallingBlockAndCause() {
    UUID expected = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    assertThat(SkillKinetics.isCorrelatedAnvilDeath(DamageCause.FALLING_BLOCK, expected, expected)).isTrue();
    assertThat(SkillKinetics.isCorrelatedAnvilDeath(DamageCause.FALLING_BLOCK, other, expected)).isFalse();
    assertThat(SkillKinetics.isCorrelatedAnvilDeath(DamageCause.FALL, expected, expected)).isFalse();
    assertThat(SkillKinetics.isCorrelatedAnvilDeath(null, expected, expected)).isFalse();
    assertThat(SkillKinetics.isCorrelatedAnvilDeath(DamageCause.FALLING_BLOCK, null, expected)).isFalse();
    assertThat(SkillKinetics.isCorrelatedAnvilDeath(DamageCause.FALLING_BLOCK, expected, null)).isFalse();
  }

  @Test
  void sweetRangeJabAcceptsTheFullInclusiveBand() {
    assertThat(SkillKinetics.isSweetRangeJab(4.5D, 3.0D, 6.0D)).isTrue();
    assertThat(SkillKinetics.isSweetRangeJab(3.0D, 3.0D, 6.0D)).isTrue();
    assertThat(SkillKinetics.isSweetRangeJab(6.0D, 3.0D, 6.0D)).isTrue();
  }

  @Test
  void sweetRangeJabRejectsOutsideTheBand() {
    assertThat(SkillKinetics.isSweetRangeJab(2.99D, 3.0D, 6.0D)).isFalse();
    assertThat(SkillKinetics.isSweetRangeJab(6.01D, 3.0D, 6.0D)).isFalse();
    assertThat(SkillKinetics.isSweetRangeJab(0.0D, 3.0D, 6.0D)).isFalse();
  }

  @Test
  void sweetRangeJabRejectsNonFiniteInputs() {
    assertThat(SkillKinetics.isSweetRangeJab(Double.NaN, 3.0D, 6.0D)).isFalse();
    assertThat(SkillKinetics.isSweetRangeJab(4.5D, Double.NaN, 6.0D)).isFalse();
    assertThat(SkillKinetics.isSweetRangeJab(4.5D, 3.0D, Double.NaN)).isFalse();
  }

  @Test
  void chargeHitQualifiesInsideTheLungeWindow() {
    assertThat(SkillKinetics.isChargeHit(2000L, 1000L, 1200L, 0.0D, 0.18D)).isTrue();
    assertThat(SkillKinetics.isChargeHit(2200L, 1000L, 1200L, 0.0D, 0.18D)).isTrue();
  }

  @Test
  void chargeHitRejectsAnExpiredLungeWindow() {
    assertThat(SkillKinetics.isChargeHit(2201L, 1000L, 1200L, 0.0D, 0.18D)).isFalse();
    assertThat(SkillKinetics.isChargeHit(10000L, 1000L, 1200L, 0.0D, 0.18D)).isFalse();
  }

  @Test
  void chargeHitRejectsMissingOrFutureLungeStamps() {
    assertThat(SkillKinetics.isChargeHit(2000L, 0L, 1200L, 0.0D, 0.18D)).isFalse();
    assertThat(SkillKinetics.isChargeHit(2000L, 3000L, 1200L, 0.0D, 0.18D)).isFalse();
  }

  @Test
  void chargeHitQualifiesAtSprintSpeedWithoutALunge() {
    assertThat(SkillKinetics.isChargeHit(2000L, 0L, 1200L, 0.18D, 0.18D)).isTrue();
    assertThat(SkillKinetics.isChargeHit(2000L, 0L, 1200L, 0.5D, 0.18D)).isTrue();
    assertThat(SkillKinetics.isChargeHit(2000L, 0L, 1200L, 0.179D, 0.18D)).isFalse();
  }

  @Test
  void chargeHitRejectsNonFiniteSpeed() {
    assertThat(SkillKinetics.isChargeHit(2000L, 0L, 1200L, Double.NaN, 0.18D)).isFalse();
    assertThat(SkillKinetics.isChargeHit(2000L, 0L, 1200L, Double.POSITIVE_INFINITY, 0.18D)).isFalse();
  }

  @Test
  void motionRewardsRequireTimeAndMeaningfulTravel() {
    assertThat(SkillKinetics.motionRewardReady(2_000L, 1_000L, 2.25D, 1_000L, 1.5D)).isTrue();
    assertThat(SkillKinetics.motionRewardReady(1_999L, 1_000L, 2.25D, 1_000L, 1.5D)).isFalse();
    assertThat(SkillKinetics.motionRewardReady(2_000L, 1_000L, 2.24D, 1_000L, 1.5D)).isFalse();
    assertThat(SkillKinetics.motionRewardReady(2_000L, 1_000L, Double.NaN, 1_000L, 1.5D)).isFalse();
  }

  @Test
  void configDefaultsMatchTheKineticsTuningTable() {
    SkillKinetics.Config config = new SkillKinetics.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.cooldownDelay).isEqualTo(1000L);
    assertThat(config.smashHitXp).isEqualTo(12D);
    assertThat(config.plainMaceHitXp).isEqualTo(3D);
    assertThat(config.spearJabXp).isEqualTo(6D);
    assertThat(config.spearChargeXp).isEqualTo(12D);
    assertThat(config.mountedChargeXp).isEqualTo(14D);
    assertThat(config.sweetRangeMin).isEqualTo(3.0D);
    assertThat(config.sweetRangeMax).isEqualTo(6.0D);
    assertThat(config.chargeMinSpeed).isEqualTo(0.18D);
    assertThat(config.lungeChargeWindowMs).isEqualTo(1200L);
    assertThat(config.motionRewardCooldownMs).isEqualTo(1000L);
    assertThat(config.motionRewardMinDistance).isEqualTo(1.5D);
    assertThat(config.kbCooldownMs).isEqualTo(750L);
    assertThat(config.selfKnockbackFactor).isEqualTo(0.35D);
    assertThat(config.levitationCooldownMs).isEqualTo(1500L);
    assertThat(config.anvilCooldownMs).isEqualTo(4000L);
    assertThat(config.anvilLocationCooldownMs).isEqualTo(8000L);
    assertThat(config.anvilLedgerTtlMs).isEqualTo(120000L);
    assertThat(config.anvilAdvancementMinFall).isEqualTo(8D);
    assertThat(config.anvilDropReward).isEqualTo(500D);
  }

  @Test
  void configCapsAndRatesArePositive() {
    SkillKinetics.Config config = new SkillKinetics.Config();
    assertThat(config.breakFallCap).isGreaterThan(0D);
    assertThat(config.bounceCap).isGreaterThan(0D);
    assertThat(config.kbXpCap).isGreaterThan(0D);
    assertThat(config.levitationXpCap).isGreaterThan(0D);
    assertThat(config.anvilPerEventCap).isGreaterThan(0D);
    assertThat(config.anvilShareRadius).isGreaterThan(0D);
    assertThat(config.anvilShareFactor).isGreaterThan(0D);
    assertThat(config.launchMinDeltaY).isGreaterThan(0D);
    assertThat(config.kbMinMagnitude).isGreaterThan(0D);
  }

  @Test
  void meleeDamageHandlerObservesAtMonitorAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = SkillKinetics.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
  }

  @Test
  void breakFallHandlerObservesAtMonitorAndReceivesCancelledEvents() throws ReflectiveOperationException {
    Method handler = SkillKinetics.class.getDeclaredMethod("on", EntityDamageEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isFalse();
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isTrue();
  }

  @Test
  void lungeMarkerObservesOnlySuccessfulLunges() throws ReflectiveOperationException {
    Method handler = SkillKinetics.class.getDeclaredMethod("on", EntityLungeEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
  }
}
