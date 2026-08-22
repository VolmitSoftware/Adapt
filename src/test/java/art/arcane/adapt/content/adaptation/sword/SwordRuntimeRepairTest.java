package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.Cooldowns;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwordRuntimeRepairTest {
  @Test
  void bladeFlowScalesAttackSpeedAndRoundsItsWindowUpToTicks() {
    assertThat(SwordsBladeFlow.attackSpeedBonus(0)).isZero();
    assertThat(SwordsBladeFlow.attackSpeedBonus(1)).isEqualTo(0.10D);
    assertThat(SwordsBladeFlow.attackSpeedBonus(4)).isEqualTo(0.40D);
    assertThat(SwordsBladeFlow.effectDurationTicks(4_001L)).isEqualTo(81);
  }

  @Test
  void bloodyBladeSchedulesEnoughPulsesToCoverConfiguredDuration() {
    assertThat(SwordsBloodyBlade.bleedProcCount(1_000L)).isEqualTo(4);
    assertThat(SwordsBloodyBlade.bleedProcCount(7_000L)).isEqualTo(28);
    assertThat(SwordsBloodyBlade.bleedProcCount(0L)).isEqualTo(1);
  }

  @Test
  void crescentGuardConvertsZeroBasedAmplifiersToAbsorptionPoints() {
    assertThat(SwordsCrescentGuard.absorptionPoints(0)).isEqualTo(4D);
    assertThat(SwordsCrescentGuard.absorptionPoints(1)).isEqualTo(8D);
    assertThat(SwordsCrescentGuard.absorptionPoints(2)).isEqualTo(12D);
  }

  @Test
  void dualWieldAlwaysAppliesAtLeastBaseDamageAndRejectsInvalidNumbers() {
    assertThat(SwordsDualWield.scaledDamage(7D, 1.25D)).isEqualTo(8.75D);
    assertThat(SwordsDualWield.scaledDamage(7D, 0.5D)).isEqualTo(7D);
    assertThat(SwordsDualWield.scaledDamage(Double.NaN, 1.25D)).isZero();
  }

  @Test
  void crimsonCycloneUsesTheServerCriticalHitSignalAndOwnCooldown() throws ReflectiveOperationException {
    assertThat(SwordsCrimsonCyclone.isCritTrigger(true)).isTrue();
    assertThat(SwordsCrimsonCyclone.isCritTrigger(false)).isFalse();
    Field cooldownField = SwordsCrimsonCyclone.class.getDeclaredField("cooldowns");
    assertThat(cooldownField.getType()).isEqualTo(Cooldowns.class);
  }

  @Test
  void duelistsFocusHighlightsTheProjectileShooterInsteadOfTheProjectile() {
    Projectile projectile = mock(Projectile.class);
    LivingEntity shooter = mock(LivingEntity.class);
    when(projectile.getShooter()).thenReturn(shooter);

    assertThat(SwordsDuelistsFocus.resolveThreat(projectile)).isSameAs(shooter);
    assertThat(SwordsDuelistsFocus.resolveThreat(shooter)).isSameAs(shooter);
  }
}
