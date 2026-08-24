package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsSurfaceSkateTest {
  @Test
  void configDefaultsAreSane() {
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
    assertThat(config.slidePercentBase).isCloseTo(0.15D, offset(1.0E-9D));
    assertThat(config.slidePercentFactor).isCloseTo(0.35D, offset(1.0E-9D));
    assertThat(config.sneakBrakePercent).isCloseTo(1D, offset(1.0E-9D));
  }

  @Test
  void slidePercentageGrowsWithLevel() {
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    double atLevelOne = KineticsSurfaceSkate.slidePercent(config.slidePercentBase, config.slidePercentFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsSurfaceSkate.slidePercent(config.slidePercentBase, config.slidePercentFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.22D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.5D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void percentageScalingClampsEveryInput() {
    assertThat(KineticsSurfaceSkate.slidePercent(-1D, 0D, 0D)).isZero();
    assertThat(KineticsSurfaceSkate.slidePercent(0.8D, 0.8D, 1D)).isEqualTo(1D);
    assertThat(KineticsSurfaceSkate.slidePercent(Double.NaN, 0.35D, 0.5D)).isZero();
    assertThat(KineticsSurfaceSkate.slidePercent(0.2D, Double.POSITIVE_INFINITY, 1D)).isZero();
    assertThat(KineticsSurfaceSkate.slidePercent(0.2D, 0.3D, Double.NaN)).isCloseTo(0.2D, offset(1.0E-9D));
  }

  @Test
  void configNormalizationKeepsPercentagesValid() {
    KineticsSurfaceSkate adaptation = new KineticsSurfaceSkate();
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    config.slidePercentBase = 0.8D;
    config.slidePercentFactor = 0.7D;
    config.sneakBrakePercent = 2D;

    adaptation.normalizeLoadedConfig(config);

    assertThat(config.slidePercentBase).isCloseTo(0.8D, offset(1.0E-9D));
    assertThat(config.slidePercentFactor).isCloseTo(0.2D, offset(1.0E-9D));
    assertThat(config.sneakBrakePercent).isEqualTo(1D);
    adaptation.unregister();
  }

  @Test
  void stanceDecisionsRequireCurrentPlayerStateAndActiveLevel() {
    assertThat(KineticsSurfaceSkate.shouldSlide(true, false, 1)).isTrue();
    assertThat(KineticsSurfaceSkate.shouldSlide(false, false, 1)).isFalse();
    assertThat(KineticsSurfaceSkate.shouldSlide(true, true, 1)).isFalse();
    assertThat(KineticsSurfaceSkate.shouldSlide(true, false, 0)).isFalse();
  }

  @Test
  void nativeModifierCancelsTheConfiguredFrictionPercentage() {
    assertThat(KineticsSurfaceSkate.nativeSlideModifier(0.4D)).isCloseTo(-0.4D, offset(1.0E-9D));
    assertThat(KineticsSurfaceSkate.nativeSlideModifier(2D)).isEqualTo(-1D);
    assertThat(KineticsSurfaceSkate.nativeSlideModifier(Double.NaN)).isZero();
  }

  @Test
  void everySurfaceUsesTheSamePercentageOfItsOwnFrictionLoss() {
    assertThat(KineticsSurfaceSkate.modifiedSurfaceFriction(0.6D, 0.5D)).isCloseTo(0.8D, offset(1.0E-9D));
    assertThat(KineticsSurfaceSkate.modifiedSurfaceFriction(0.98D, 0.5D)).isCloseTo(0.99D, offset(1.0E-9D));
    assertThat(KineticsSurfaceSkate.modifiedSurfaceFriction(0.4D, 0.5D)).isCloseTo(0.7D, offset(1.0E-9D));
    assertThat(KineticsSurfaceSkate.modifiedSurfaceFriction(0.1D, 1D)).isEqualTo(1D);
  }

  @Test
  void fallbackSlideMatchesNativeFrictionFormula() {
    double scale = KineticsSurfaceSkate.fallbackVelocityScale(0.3D, 0D, 0.5D, 0D, 0.6D, 0.5D);
    assertThat(scale).isCloseTo(4D / 3D, offset(1.0E-9D));
  }

  @Test
  void fallbackSeedsObservedClientMovementWhenServerVelocityIsAbsent() {
    Vector velocity = new Vector(0D, 0.25D, 0D);
    boolean changed = KineticsSurfaceSkate.applyFallbackHorizontalVelocity(
        velocity, 0.5D, 0D, 0.6D, 0.5D);

    assertThat(changed).isTrue();
    assertThat(velocity.getX()).isCloseTo(0.5D, offset(1.0E-9D));
    assertThat(velocity.getZ()).isZero();
    assertThat(velocity.getY()).isCloseTo(0.25D, offset(1.0E-9D));
  }

  @Test
  void fallbackDoesNotInjectSpeedAtTrueRest() {
    Vector velocity = new Vector(0D, 0.25D, 0D);
    boolean changed = KineticsSurfaceSkate.applyFallbackHorizontalVelocity(
        velocity, 0D, 0D, 0.6D, 0.5D);

    assertThat(changed).isFalse();
    assertThat(velocity).isEqualTo(new Vector(0D, 0.25D, 0D));
  }

  @Test
  void fallbackSlideCannotExceedObservedOrExistingKnockbackMovement() {
    double scale = KineticsSurfaceSkate.fallbackVelocityScale(0.4D, 0D, 0.42D, 0D, 0.6D, 0.5D);
    assertThat(0.4D * scale).isCloseTo(0.42D, offset(1.0E-9D));

    Vector velocity = new Vector(0.8D, 0.25D, 0D);
    boolean changed = KineticsSurfaceSkate.applyFallbackHorizontalVelocity(
        velocity, 0.42D, 0D, 0.6D, 0.5D);
    assertThat(changed).isFalse();
    assertThat(velocity.getX()).isCloseTo(0.8D, offset(1.0E-9D));
    assertThat(velocity.getY()).isCloseTo(0.25D, offset(1.0E-9D));
  }

  @Test
  void fallbackSlideStillDecaysUncontrolledMomentum() {
    double speed = 1D;
    for (int tick = 0; tick < 12; tick++) {
      double previous = speed;
      double vanillaDamped = previous * 0.6D * 0.91D;
      double scale = KineticsSurfaceSkate.fallbackVelocityScale(
          vanillaDamped, 0D, previous, 0D, 0.6D, 0.5D);
      speed = vanillaDamped * scale;
      assertThat(speed).isLessThan(previous);
    }
  }

  @Test
  void fullSneakBrakeStopsOnlyHorizontalMotion() {
    Vector velocity = new Vector(0.4D, 0.25D, -0.3D);
    boolean changed = KineticsSurfaceSkate.applyHorizontalBrake(velocity, 1D);

    assertThat(changed).isTrue();
    assertThat(velocity.getX()).isZero();
    assertThat(velocity.getY()).isCloseTo(0.25D, offset(1.0E-9D));
    assertThat(velocity.getZ()).isZero();
  }

  @Test
  void configuredPartialSneakBrakeRetainsTheRequestedPercentage() {
    Vector velocity = new Vector(0.4D, -0.2D, -0.3D);
    boolean changed = KineticsSurfaceSkate.applyHorizontalBrake(velocity, 0.25D);

    assertThat(changed).isTrue();
    assertThat(velocity.getX()).isCloseTo(0.3D, offset(1.0E-9D));
    assertThat(velocity.getY()).isCloseTo(-0.2D, offset(1.0E-9D));
    assertThat(velocity.getZ()).isCloseTo(-0.225D, offset(1.0E-9D));
  }

  @Test
  void sneakBrakeDoesNothingAtRestOrWhenDisabled() {
    assertThat(KineticsSurfaceSkate.applyHorizontalBrake(new Vector(0D, 0.3D, 0D), 1D)).isFalse();
    assertThat(KineticsSurfaceSkate.applyHorizontalBrake(new Vector(0.3D, 0.3D, 0D), 0D)).isFalse();
    assertThat(KineticsSurfaceSkate.applyHorizontalBrake(new Vector(0.3D, 0.3D, 0D), Double.NaN)).isFalse();
  }

  @Test
  void periodicReconciliationUsesTheSharedOwnerPulse() throws ReflectiveOperationException {
    KineticsSurfaceSkate adaptation = new KineticsSurfaceSkate();
    assertThat(KineticsSurfaceSkate.class.getDeclaredField("ownerMaintenance").getType())
        .isEqualTo(AdaptationOwnerPulse.Registration.class);
    assertThat(adaptation.getInterval()).isEqualTo(1000L);
    adaptation.unregister();
  }

  @Test
  void sprintHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerToggleSprintEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void sneakHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void movementFallbackRunsAtHighestAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
