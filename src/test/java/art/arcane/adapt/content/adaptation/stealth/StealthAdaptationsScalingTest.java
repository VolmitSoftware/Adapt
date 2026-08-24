package art.arcane.adapt.content.adaptation.stealth;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

class StealthAdaptationsScalingTest {
  @Test
  void shadowmeldMeldDelayUsesExactLevelEndpoints() {
    assertThat(StealthShadowmeld.computeMeldDelay(3000L, 250L, 1, 4)).isEqualTo(3000L);
    assertThat(StealthShadowmeld.computeMeldDelay(3000L, 250L, 2, 4)).isEqualTo(2083L);
    assertThat(StealthShadowmeld.computeMeldDelay(3000L, 250L, 3, 4)).isEqualTo(1167L);
    assertThat(StealthShadowmeld.computeMeldDelay(3000L, 250L, 4, 4)).isEqualTo(250L);
    assertThat(StealthShadowmeld.computeMeldDelay(3000L, 250L, 0, 4)).isEqualTo(3000L);
    assertThat(StealthShadowmeld.computeMeldDelay(3000L, 250L, 5, 4)).isEqualTo(250L);
  }

  @Test
  void shadowmeldRequiresSneakingAndAnUndetectedCoreSample() {
    assertThat(StealthShadowmeld.canRemainEligible(true, true)).isTrue();
    assertThat(StealthShadowmeld.canRemainEligible(true, false)).isFalse();
    assertThat(StealthShadowmeld.canRemainEligible(false, true)).isFalse();
  }

  @Test
  void shadowmeldCandidateBatchesBoundAndCoverAThousandPlayerRoster() {
    int[] visits = new int[1_000];
    int cursor = 0;
    int pulses = 0;
    do {
      StealthShadowmeld.CandidateBatch batch = StealthShadowmeld.candidateBatch(visits.length, cursor);
      assertThat(batch.size()).isLessThanOrEqualTo(StealthShadowmeld.MAX_CANDIDATE_VISITS_PER_TICK);
      for (int index = batch.start(); index < batch.end(); index++) {
        visits[index]++;
      }
      cursor = batch.nextCursor();
      pulses++;
    } while (cursor != 0);

    assertThat(visits).containsOnly(1);
    assertThat(pulses).isEqualTo(4);
    assertThat(pulses * StealthShadowmeld.CANDIDATE_SCAN_INTERVAL_MILLIS).isEqualTo(1_000L);
  }

  @Test
  void shadowmeldCandidateBatchCadenceRepeatsWithoutSkippingPlayers() {
    int[] visits = new int[1_000];
    int cursor = 0;
    for (int pulse = 0; pulse < 8; pulse++) {
      StealthShadowmeld.CandidateBatch batch = StealthShadowmeld.candidateBatch(visits.length, cursor);
      for (int index = batch.start(); index < batch.end(); index++) {
        visits[index]++;
      }
      cursor = batch.nextCursor();
    }

    assertThat(visits).containsOnly(2);
    assertThat(cursor).isZero();
    StealthShadowmeld.CandidateBatch reset = StealthShadowmeld.candidateBatch(100, 900);
    assertThat(reset.start()).isZero();
    assertThat(reset.end()).isEqualTo(100);
    assertThat(reset.nextCursor()).isZero();
  }

  @Test
  void shadowmeldKeepsImmediateSneakEventActivation() throws ReflectiveOperationException {
    Method handler = StealthShadowmeld.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);

    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }

  @Test
  void assassinateHealthCapAndCooldownScale() {
    assertThat(StealthAssassinate.computeHealthCap(22.0, 38.0, 0.25)).isEqualTo(31.5);
    assertThat(StealthAssassinate.computeHealthCap(22.0, 38.0, 1.0)).isEqualTo(60.0);
    assertThat(StealthAssassinate.computeCooldown(40000, 20000, 0.5)).isEqualTo(30000L);
    assertThat(StealthAssassinate.computeCooldown(1000, 20000, 1.0)).isEqualTo(8000L);
    assertThat(StealthAssassinate.executionDamage(18.5D)).isEqualTo(18.5D);
    assertThat(StealthAssassinate.executionDamage(-1D)).isZero();
  }

  @Test
  void cutpurseStealChanceClampsAndLootStacksScale() {
    assertThat(StealthCutpurse.computeStealChance(0.25, 0.4, 0.9, 0.0)).isCloseTo(0.25, within(1e-9));
    assertThat(StealthCutpurse.computeStealChance(0.25, 0.4, 0.9, 1.0)).isCloseTo(0.65, within(1e-9));
    assertThat(StealthCutpurse.computeStealChance(0.5, 0.8, 0.9, 1.0)).isCloseTo(0.9, within(1e-9));
    assertThat(StealthCutpurse.computeLootStacks(1, 2, 0.0)).isEqualTo(1);
    assertThat(StealthCutpurse.computeLootStacks(1, 2, 1.0)).isEqualTo(3);
  }

  @Test
  void smokePelletRadiusClampsAndPulsesScale() {
    assertThat(StealthSmokePellet.computeRadius(2.5, 2.5, 6.0, 1.0)).isEqualTo(5.0);
    assertThat(StealthSmokePellet.computeRadius(2.5, 10.0, 6.0, 1.0)).isEqualTo(6.0);
    assertThat(StealthSmokePellet.computePulses(8, 12, 0.0)).isEqualTo(8);
    assertThat(StealthSmokePellet.computePulses(8, 10, 1.0)).isEqualTo(18);
    assertThat(StealthSmokePellet.computeRaycastRange(0D)).isEqualTo(2D);
    assertThat(StealthSmokePellet.computeRaycastRange(100D)).isEqualTo(64D);
  }

  @Test
  void smokePelletRequiresSneakingWithGunpowderInEitherHand() {
    assertThat(StealthSmokePellet.gunpowderHand(true, Material.GUNPOWDER, Material.AIR))
        .isEqualTo(EquipmentSlot.HAND);
    assertThat(StealthSmokePellet.gunpowderHand(true, Material.AIR, Material.GUNPOWDER))
        .isEqualTo(EquipmentSlot.OFF_HAND);
    assertThat(StealthSmokePellet.gunpowderHand(true, Material.GUNPOWDER, Material.GUNPOWDER))
        .isEqualTo(EquipmentSlot.HAND);
    assertThat(StealthSmokePellet.gunpowderHand(true, Material.AIR, Material.AIR)).isNull();
    assertThat(StealthSmokePellet.gunpowderHand(false, Material.GUNPOWDER, Material.AIR)).isNull();
    assertThat(StealthSmokePellet.isBlindable(mock(LivingEntity.class))).isTrue();
    assertThat(StealthSmokePellet.isBlindable(mock(Entity.class))).isFalse();
    assertThat(StealthSmokePellet.currentConcealmentLease(7L, 7L)).isTrue();
    assertThat(StealthSmokePellet.currentConcealmentLease(8L, 7L)).isFalse();
    assertThat(StealthSmokePellet.currentConcealmentLease(null, 7L)).isFalse();
  }

  @Test
  void smokePelletRetainsTheCentralInteractionValidityGate() throws ReflectiveOperationException {
    Method handler = StealthSmokePellet.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);

    assertThat(handler.getAnnotation(EventHandler.class)).isNotNull();
  }

  @Test
  void stealthVisionBlocksOnlyNewBlindnessWhileActive() {
    assertThat(StealthSight.blocksBlindness(
        true, true, EntityPotionEffectEvent.Action.ADDED)).isTrue();
    assertThat(StealthSight.blocksBlindness(
        true, true, EntityPotionEffectEvent.Action.CHANGED)).isTrue();
    assertThat(StealthSight.blocksBlindness(
        true, true, EntityPotionEffectEvent.Action.REMOVED)).isFalse();
    assertThat(StealthSight.blocksBlindness(
        false, true, EntityPotionEffectEvent.Action.ADDED)).isFalse();
    assertThat(StealthSight.blocksBlindness(
        true, false, EntityPotionEffectEvent.Action.ADDED)).isFalse();
  }

  @Test
  void trapSenseBlockClassificationIsCorrect() {
    assertThat(StealthTrapSense.isTrapBlock(Material.TRIPWIRE)).isTrue();
    assertThat(StealthTrapSense.isTrapBlock(Material.TRIPWIRE_HOOK)).isTrue();
    assertThat(StealthTrapSense.isTrapBlock(Material.TRAPPED_CHEST)).isTrue();
    assertThat(StealthTrapSense.isTrapBlock(Material.STONE_PRESSURE_PLATE)).isTrue();
    assertThat(StealthTrapSense.isTrapBlock(Material.OAK_PRESSURE_PLATE)).isTrue();
    assertThat(StealthTrapSense.isTrapBlock(Material.SCULK_SHRIEKER)).isTrue();
    assertThat(StealthTrapSense.isTrapBlock(Material.STONE)).isFalse();
    assertThat(StealthTrapSense.isSculkTrap(Material.SCULK_SENSOR)).isTrue();
    assertThat(StealthTrapSense.isSculkTrap(Material.TRIPWIRE)).isFalse();
  }

  @Test
  void trapSenseRangeAndMercyClamp() {
    assertThat(StealthTrapSense.computeRange(4.0, 4.0, 0.0)).isEqualTo(4.0);
    assertThat(StealthTrapSense.computeRange(4.0, 4.0, 1.0)).isEqualTo(8.0);
    assertThat(StealthTrapSense.computeRange(1.0, 0.0, 0.0)).isEqualTo(3.0);
    assertThat(StealthTrapSense.computeRange(4.0, 10.0, 1.0)).isEqualTo(8.0);
    assertThat(StealthTrapSense.computeMercy(0.7, 4, 4)).isEqualTo(1.0);
    assertThat(StealthTrapSense.computeMercy(0.7, 1, 4)).isCloseTo(0.175, within(1e-9));
    assertThat(StealthTrapSense.computeMercy(2.0, 3, 4)).isEqualTo(1.0);
  }

  @Test
  void trapSenseMaximumLevelAlwaysSuppressesMovement() {
    assertThat(StealthTrapSense.shouldSuppressMovement(4, 4, false, 0D, 0.99D)).isTrue();
    assertThat(StealthTrapSense.shouldSuppressMovement(3, 4, false, 1D, 0D)).isFalse();
    assertThat(StealthTrapSense.shouldSuppressMovement(3, 4, true, 0.5D, 0.49D)).isTrue();
    assertThat(StealthTrapSense.shouldSuppressMovement(3, 4, true, 0.5D, 0.5D)).isFalse();
  }

  @Test
  void ghostArmorConsumesOnlyUncancelledResolvedDamage() throws ReflectiveOperationException {
    Method handler = StealthGhostArmor.class.getDeclaredMethod("on", EntityDamageEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);

    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void trapSenseBlockKeyIsDistinctPerPosition() {
    assertThat(StealthTrapSense.blockKey(0, 0, 0)).isNotEqualTo(StealthTrapSense.blockKey(1, 0, 0));
    assertThat(StealthTrapSense.blockKey(0, 0, 0)).isNotEqualTo(StealthTrapSense.blockKey(0, 1, 0));
    assertThat(StealthTrapSense.blockKey(0, 0, 0)).isNotEqualTo(StealthTrapSense.blockKey(0, 0, 1));
    assertThat(StealthTrapSense.blockKey(-5, 64, -5)).isNotEqualTo(StealthTrapSense.blockKey(5, 64, 5));
    assertThat(StealthTrapSense.blockKey(-5, -64, 12)).isEqualTo(StealthTrapSense.blockKey(-5, -64, 12));
    assertThat(StealthTrapSense.blockKey(30000000, 320, 30000000))
        .isNotEqualTo(StealthTrapSense.blockKey(30000000, -64, 30000000));
  }

  @Test
  void decoySwapRangeScalesAndCooldownClamps() {
    assertThat(StealthDecoySwap.computeSwapRange(10.0, 20.0, 0.0)).isEqualTo(10.0);
    assertThat(StealthDecoySwap.computeSwapRange(10.0, 20.0, 1.0)).isEqualTo(30.0);
    assertThat(StealthDecoySwap.computeCooldown(12000, 8000, 1.0)).isEqualTo(4000L);
    assertThat(StealthDecoySwap.computeCooldown(1000, 8000, 1.0)).isEqualTo(2000L);
  }

  @Test
  void decoySwapAcceptsOnlyConfirmedTeleportCompletion() {
    RuntimeException failure = new RuntimeException("failed");

    assertThat(StealthDecoySwap.teleportCompleted(true, null)).isTrue();
    assertThat(StealthDecoySwap.teleportCompleted(false, null)).isFalse();
    assertThat(StealthDecoySwap.teleportCompleted(null, null)).isFalse();
    assertThat(StealthDecoySwap.teleportCompleted(true, failure)).isFalse();
  }

  @Test
  void decoySwapStartsPlayerLegOnlyForCurrentRegisteredOperations() {
    assertThat(StealthDecoySwap.canStartPlayerLeg(true, true)).isTrue();
    assertThat(StealthDecoySwap.canStartPlayerLeg(false, true)).isFalse();
    assertThat(StealthDecoySwap.canStartPlayerLeg(true, false)).isFalse();
  }

  @Test
  void umbralRecoveryRefundAndExtensionScale() {
    assertThat(StealthUmbralRecovery.computeRefund(2, 4, 0.0)).isEqualTo(2);
    assertThat(StealthUmbralRecovery.computeRefund(2, 4, 1.0)).isEqualTo(6);
    assertThat(StealthUmbralRecovery.computeRefund(0, 0, 0.0)).isEqualTo(1);
    assertThat(StealthUmbralRecovery.computeExtensionTicks(40, 120, 0.0)).isEqualTo(40);
    assertThat(StealthUmbralRecovery.computeExtensionTicks(40, 120, 1.0)).isEqualTo(160);
  }
}
