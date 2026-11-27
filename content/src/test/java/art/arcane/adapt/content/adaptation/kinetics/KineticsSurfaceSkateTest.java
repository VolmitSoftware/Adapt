package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.engine.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsSurfaceSkateTest extends EngineTestBase {
  @Test
  void configDefaultsAreSane() {
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
    assertThat(config.slidePercentBase).isCloseTo(0.15D, offset(1.0E-9D));
    assertThat(config.slidePercentFactor).isCloseTo(0.8D, offset(1.0E-9D));
    assertThat(config.sneakBrakePercent).isCloseTo(1D, offset(1.0E-9D));
  }

  @Test
  void slidePercentageGrowsWithLevel() {
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    double atLevelOne = KineticsSurfaceSkate.slidePercent(config.slidePercentBase, config.slidePercentFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsSurfaceSkate.slidePercent(config.slidePercentBase, config.slidePercentFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.31D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.95D, offset(1.0E-9D));
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
    KineticsSurfaceSkate adaptation = TestContent.adaptation(Id.adapt("kinetics"), KineticsSurfaceSkate::new);
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
    assertThat(KineticsSurfaceSkate.modifiedSurfaceFriction(0.1D, 1D)).isEqualTo(1D);
  }

  @Test
  void glideOnlyStartsWhenTheStepShortensLikeReleasedInput() {
    assertThat(KineticsSurfaceSkate.released(0.28D, 0D, 0.28D, 0D)).isFalse();
    assertThat(KineticsSurfaceSkate.released(0.28D, 0D, 0.30D, 0D)).isFalse();
    assertThat(KineticsSurfaceSkate.released(0.28D, 0D, 0.28D * 0.91D, 0D)).isFalse();
    assertThat(KineticsSurfaceSkate.released(0.28D, 0D, 0D, 0.28D)).isFalse();
    assertThat(KineticsSurfaceSkate.released(0.28D, 0D, 0.28D * 0.546D, 0D)).isTrue();
    assertThat(KineticsSurfaceSkate.released(0.28D, 0D, 0D, 0D)).isFalse();
  }

  @Test
  void glideKeepsTheCancelledShareOfTheLastStep() {
    Vec3 glide = KineticsSurfaceSkate.glideVelocity(new Vec3(0D, -0.078D, 0D), 0.2D, -0.1D, 0.6D, 0.5D);
    assertThat(glide).isNotNull();
    assertThat(glide.x()).isCloseTo(0.2D * 0.91D * 0.8D, offset(1.0E-9D));
    assertThat(glide.y()).isEqualTo(-0.078D);
    assertThat(glide.z()).isCloseTo(-0.1D * 0.91D * 0.8D, offset(1.0E-9D));
  }

  @Test
  void glideLeavesAPlayerAtRestOrWithoutTheSkillAlone() {
    assertThat(KineticsSurfaceSkate.glideVelocity(new Vec3(0D, 0D, 0D), 0D, 0D, 0.6D, 0.5D)).isNull();
    assertThat(KineticsSurfaceSkate.glideVelocity(new Vec3(0D, 0D, 0D), 0.2D, 0D, 0.6D, 0D)).isNull();
    assertThat(KineticsSurfaceSkate.glideVelocity(new Vec3(0D, 0D, 0D), 0.2D, 0D, Double.NaN, 0.5D)).isNull();
    assertThat(KineticsSurfaceSkate.glideVelocity(null, 0.2D, 0D, 0.6D, 0.5D)).isNull();
  }

  @Test
  void glideRunsAtHighestAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void fullSneakBrakeStopsOnlyHorizontalMotion() {
    Vec3 velocity = new Vec3(0.4D, 0.25D, -0.3D);
    Vec3 braked = KineticsSurfaceSkate.applyHorizontalBrake(velocity, 1D);

    assertThat(braked).isNotNull();
    assertThat(braked.x()).isZero();
    assertThat(braked.y()).isCloseTo(0.25D, offset(1.0E-9D));
    assertThat(braked.z()).isZero();
  }

  @Test
  void configuredPartialSneakBrakeRetainsTheRequestedPercentage() {
    Vec3 velocity = new Vec3(0.4D, -0.2D, -0.3D);
    Vec3 braked = KineticsSurfaceSkate.applyHorizontalBrake(velocity, 0.25D);

    assertThat(braked).isNotNull();
    assertThat(braked.x()).isCloseTo(0.3D, offset(1.0E-9D));
    assertThat(braked.y()).isCloseTo(-0.2D, offset(1.0E-9D));
    assertThat(braked.z()).isCloseTo(-0.225D, offset(1.0E-9D));
  }

  @Test
  void sneakBrakeDoesNothingAtRestOrWhenDisabled() {
    assertThat(KineticsSurfaceSkate.applyHorizontalBrake(new Vec3(0D, 0.3D, 0D), 1D)).isNull();
    assertThat(KineticsSurfaceSkate.applyHorizontalBrake(new Vec3(0.3D, 0.3D, 0D), 0D)).isNull();
    assertThat(KineticsSurfaceSkate.applyHorizontalBrake(new Vec3(0.3D, 0.3D, 0D), Double.NaN)).isNull();
  }

  @Test
  void periodicReconciliationUsesThePublicOwnerPulse() {
    KineticsSurfaceSkate adaptation = TestContent.adaptation(Id.adapt("kinetics"), KineticsSurfaceSkate::new);
    assertThat(KineticsSurfaceSkate.class.getDeclaredFields())
        .noneMatch(field -> field.getType() == AdaptationOwnerPulse.Registration.class);
    assertThat(adaptation.getInterval()).isEqualTo(1000L);
    adaptation.unregister();
  }

  @Test
  void sprintHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerToggleSprintEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void sneakHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
