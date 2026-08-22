package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeaborneNewAdaptationsScalingTest {
  private static final Path TIDECALLER_SOURCE =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/seaborrne/SeaborneTidecaller.java");

  @Test
  void coralGrowthChanceClampsToUnitIntervalAndScalesUp() {
    assertThat(SeaborneCoralGardener.growthChance(0.35D, 0.5D, 0D)).isCloseTo(0.35D, offset(1e-9));
    assertThat(SeaborneCoralGardener.growthChance(0.35D, 0.5D, 1D)).isEqualTo(0.85D);
    assertThat(SeaborneCoralGardener.growthChance(0.9D, 0.9D, 1D)).isEqualTo(1D);
    assertThat(SeaborneCoralGardener.growthChance(0.1D, -1D, 1D)).isZero();
  }

  @Test
  void coralSurvivalMillisGrowsWithLevelPercent() {
    assertThat(SeaborneCoralGardener.survivalMillis(60D, 240D, 0D)).isEqualTo(60_000L);
    assertThat(SeaborneCoralGardener.survivalMillis(60D, 240D, 1D)).isEqualTo(300_000L);
  }

  @Test
  void salvagerBonusRollsRoundAndNeverDropBelowOne() {
    assertThat(SeaborneDeepSalvager.bonusRolls(1, 3D, 0D)).isEqualTo(1);
    assertThat(SeaborneDeepSalvager.bonusRolls(1, 3D, 1D)).isEqualTo(4);
    assertThat(SeaborneDeepSalvager.bonusRolls(1, 3D, 0.5D)).isEqualTo(3);
    assertThat(SeaborneDeepSalvager.bonusRolls(0, 0D, 0D)).isEqualTo(1);
    assertThat(SeaborneDeepSalvager.detectionRange(4D, 5D, 1D)).isEqualTo(9D);
  }

  @Test
  void inkVeilCooldownShrinksWithLevelButHoldsAThreeSecondFloor() {
    assertThat(SeaborneInkVeil.cooldownMillis(12_000L, 8_000L, 0D)).isEqualTo(12_000L);
    assertThat(SeaborneInkVeil.cooldownMillis(12_000L, 8_000L, 1D)).isEqualTo(4_000L);
    assertThat(SeaborneInkVeil.cooldownMillis(6_000L, 8_000L, 1D)).isEqualTo(3_000L);
    assertThat(SeaborneInkVeil.cloudSize(4D, 4D, 1D)).isEqualTo(8D);
    assertThat(SeaborneInkVeil.cloudSize(-4D, 0D, 0D)).isEqualTo(0.5D);
    assertThat(SeaborneInkVeil.cloudSize(100D, 100D, 1D)).isEqualTo(16D);
  }

  @Test
  void inkVeilConcealmentScalesAndExpiresAtItsLeaseBoundary() {
    assertThat(SeaborneInkVeil.scaledTicks(40D, 40D, 0D)).isEqualTo(40);
    assertThat(SeaborneInkVeil.scaledTicks(40D, 40D, 1D)).isEqualTo(80);
    assertThat(SeaborneInkVeil.scaledTicks(-10D, 0D, 0D)).isEqualTo(1);
    assertThat(SeaborneInkVeil.scaledTicks(Double.MAX_VALUE, Double.MAX_VALUE, 1D)).isEqualTo(1);
    assertThat(SeaborneInkVeil.isConcealed(2000L, 1999L)).isTrue();
    assertThat(SeaborneInkVeil.isConcealed(2000L, 2000L)).isFalse();
  }

  @Test
  void tridentDamageAndRecallScaleWithLevel() {
    assertThat(SeaborneTridentMastery.damageBonus(0.15D, 0.45D, 0D)).isCloseTo(0.15D, offset(1e-9));
    assertThat(SeaborneTridentMastery.damageBonus(0.15D, 0.45D, 1D)).isCloseTo(0.6D, offset(1e-9));
    assertThat(SeaborneTridentMastery.recallSpeed(0.8D, 1.2D, 0D)).isEqualTo(0.8D);
    assertThat(SeaborneTridentMastery.recallSpeed(0.8D, 1.2D, 1D)).isEqualTo(2.0D);
  }

  @Test
  void fishWhispererLuckAmplifierStaysWithinTierBounds() {
    assertThat(SeaborneFishWhisperer.luckAmplifier(1, 5)).isZero();
    assertThat(SeaborneFishWhisperer.luckAmplifier(5, 5)).isEqualTo(4);
    assertThat(SeaborneFishWhisperer.luckAmplifier(9, 5)).isEqualTo(4);
    assertThat(SeaborneFishWhisperer.luckAmplifier(0, 5)).isZero();
    assertThat(SeaborneFishWhisperer.schoolRange(6D, 8D, 1D)).isEqualTo(14D);
  }

  @Test
  void hydroJetChargesAccumulateAndCap() {
    assertThat(SeaborneHydroJet.refillCharges(0D, 5000L, 2500L, 5D)).isEqualTo(2D);
    assertThat(SeaborneHydroJet.refillCharges(4D, 10_000L, 2500L, 5D)).isEqualTo(5D);
    assertThat(SeaborneHydroJet.refillCharges(1D, 0L, 2500L, 5D)).isEqualTo(1D);
    assertThat(SeaborneHydroJet.refillCharges(1D, 5000L, 0L, 5D)).isEqualTo(5D);
    assertThat(SeaborneHydroJet.maxCharges(2, 3D, 0D)).isEqualTo(2);
    assertThat(SeaborneHydroJet.maxCharges(2, 3D, 1D)).isEqualTo(5);
  }

  @Test
  void brineSkinRegenTiersAndReductionCap() {
    assertThat(SeaborneBrineSkin.regenAmplifier(0.2D)).isZero();
    assertThat(SeaborneBrineSkin.regenAmplifier(0.6D)).isEqualTo(1);
    assertThat(SeaborneBrineSkin.regenAmplifier(1D)).isEqualTo(2);
    assertThat(SeaborneBrineSkin.damageReduction(0.06D, 0.14D, 0.25D, 0D)).isCloseTo(0.06D, offset(1e-9));
    assertThat(SeaborneBrineSkin.damageReduction(0.06D, 0.14D, 0.25D, 1D)).isCloseTo(0.2D, offset(1e-9));
    assertThat(SeaborneBrineSkin.damageReduction(0.2D, 0.5D, 0.25D, 1D)).isEqualTo(0.25D);
  }

  @Test
  void tidecallerCommitsTeleportModeOnlyAfterConfirmedCompletion() {
    RuntimeException failure = new RuntimeException("failed");

    assertThat(SeaborneTidecaller.successfulTeleportDash(true, null)).isTrue();
    assertThat(SeaborneTidecaller.successfulTeleportDash(false, null)).isFalse();
    assertThat(SeaborneTidecaller.successfulTeleportDash(null, null)).isFalse();
    assertThat(SeaborneTidecaller.successfulTeleportDash(true, failure)).isFalse();
  }

  @Test
  void tidecallerClearsPendingTeleportsAcrossPlayerAndRuntimeLifecycle() throws IOException {
    String source = Files.readString(TIDECALLER_SOURCE);

    assertThat(source)
        .contains("public void on(PlayerQuitEvent e)", "teleportDashes.remove(e.getPlayer().getUniqueId())")
        .contains("public void unregister()", "teleportDashes.clear()");
  }
}
