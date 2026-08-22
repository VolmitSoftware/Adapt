package art.arcane.adapt.content.adaptation.axe;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class AxeGroundSmashTest {
  @Test
  void airborneSneakWithAnActiveAxeArmsTheSmash() {
    assertThat(AxeGroundSmash.shouldArm(true, false, true, 1, true)).isTrue();
    assertThat(AxeGroundSmash.shouldArm(false, false, true, 1, true)).isFalse();
    assertThat(AxeGroundSmash.shouldArm(true, true, true, 1, true)).isFalse();
    assertThat(AxeGroundSmash.shouldArm(true, false, false, 1, true)).isFalse();
    assertThat(AxeGroundSmash.shouldArm(true, false, true, 0, true)).isFalse();
    assertThat(AxeGroundSmash.shouldArm(true, false, true, 1, false)).isFalse();
  }

  @Test
  void armedSmashActivatesOnlyOnAValidLanding() {
    assertThat(AxeGroundSmash.shouldActivate(true, true, true, 1)).isTrue();
    assertThat(AxeGroundSmash.shouldActivate(false, true, true, 1)).isFalse();
    assertThat(AxeGroundSmash.shouldActivate(true, false, true, 1)).isFalse();
    assertThat(AxeGroundSmash.shouldActivate(true, true, false, 1)).isFalse();
    assertThat(AxeGroundSmash.shouldActivate(true, true, true, 0)).isFalse();
  }

  @Test
  void groundSmashUsesSneakAndLandingEvents() throws ReflectiveOperationException {
    Method sneak = AxeGroundSmash.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler sneakPolicy = sneak.getAnnotation(EventHandler.class);
    assertThat(sneakPolicy).isNotNull();
    assertThat(sneakPolicy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(sneakPolicy.ignoreCancelled()).isTrue();

    Method move = AxeGroundSmash.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    EventHandler movePolicy = move.getAnnotation(EventHandler.class);
    assertThat(movePolicy).isNotNull();
    assertThat(movePolicy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(movePolicy.ignoreCancelled()).isTrue();
  }

  @Test
  void damageAndForceFallOffFromCenterToRadius() {
    assertThat(AxeGroundSmash.falloffValue(8D, 3D, 0D, 8D))
        .isCloseTo(8D, offset(1.0E-9D));
    assertThat(AxeGroundSmash.falloffValue(8D, 3D, 4D, 8D))
        .isCloseTo(5.5D, offset(1.0E-9D));
    assertThat(AxeGroundSmash.falloffValue(8D, 3D, 8D, 8D))
        .isCloseTo(3D, offset(1.0E-9D));
    assertThat(AxeGroundSmash.falloffValue(8D, 3D, 12D, 8D))
        .isCloseTo(3D, offset(1.0E-9D));
  }

  @Test
  void falloffRejectsInvalidRuntimeValues() {
    assertThat(AxeGroundSmash.falloffValue(Double.NaN, 3D, 0D, 8D)).isZero();
    assertThat(AxeGroundSmash.falloffValue(8D, 3D, Double.NaN, 8D)).isZero();
    assertThat(AxeGroundSmash.falloffValue(8D, 3D, 0D, 0D)).isZero();
  }

  @Test
  void configDefaultsProvideDamageRadiusForceAndCooldown() {
    AxeGroundSmash.Config config = new AxeGroundSmash.Config();

    assertThat(config.damageLevelFactorMultiplier).isGreaterThan(0D);
    assertThat(config.radiusLevelFactorMultiplier).isGreaterThan(0D);
    assertThat(config.forceBase).isGreaterThan(0D);
    assertThat(config.cooldownTicksBase).isGreaterThan(0D);
    assertThat(config.cooldownTicksInverseLevelMultiplier).isGreaterThanOrEqualTo(0D);
  }
}
