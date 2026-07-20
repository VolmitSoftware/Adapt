package art.arcane.adapt.content.adaptation.kinetics;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsMoonJumpTest {
  @Test
  void configDefaultsAreSane() {
    KineticsMoonJump.Config config = new KineticsMoonJump.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.baseCost).isEqualTo(4);
    assertThat(config.initialCost).isEqualTo(2);
    assertThat(config.costFactor).isCloseTo(0.5D, offset(1.0E-9D));
    assertThat(config.jumpBonusBase).isCloseTo(0.06D, offset(1.0E-9D));
    assertThat(config.jumpBonusFactor).isCloseTo(0.10D, offset(1.0E-9D));
    assertThat(config.gravityReductionBase).isCloseTo(0.15D, offset(1.0E-9D));
    assertThat(config.gravityReductionFactor).isCloseTo(0.30D, offset(1.0E-9D));
    assertThat(config.floatWindowTicksBase).isCloseTo(20D, offset(1.0E-9D));
    assertThat(config.floatWindowTicksFactor).isCloseTo(20D, offset(1.0E-9D));
  }

  @Test
  void jumpBonusGrowsWithLevel() {
    KineticsMoonJump.Config config = new KineticsMoonJump.Config();
    double atLevelOne = KineticsMoonJump.jumpBonus(config.jumpBonusBase, config.jumpBonusFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsMoonJump.jumpBonus(config.jumpBonusBase, config.jumpBonusFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.08D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.16D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void everyLevelAddsHalfABlockToBaseJumpHeight() {
    for (int level = 1; level <= 5; level++) {
      assertThat(KineticsMoonJump.baseJumpHeight(level))
          .isCloseTo(KineticsJumpPhysics.VANILLA_JUMP_HEIGHT + (level * 0.5D), offset(1.0E-12D));
    }
  }

  @Test
  void baseJumpBonusConvertsTargetBlockHeightToJumpStrength() {
    for (int level = 1; level <= 5; level++) {
      double effectiveStrength = KineticsJumpPhysics.VANILLA_JUMP_STRENGTH
          + KineticsMoonJump.baseJumpStrengthBonus(level);
      assertThat(KineticsJumpPhysics.heightForStrength(effectiveStrength))
          .isCloseTo(KineticsMoonJump.baseJumpHeight(level), offset(1.0E-9D));
    }
  }

  @Test
  void jumpPhysicsRejectsInvalidInputs() {
    assertThat(KineticsJumpPhysics.heightForStrength(Double.NaN)).isZero();
    assertThat(KineticsJumpPhysics.strengthForHeight(Double.POSITIVE_INFINITY)).isZero();
    assertThat(KineticsJumpPhysics.bonusForHeight(-1D)).isZero();
  }

  @Test
  void gravityReductionGrowsWithLevel() {
    KineticsMoonJump.Config config = new KineticsMoonJump.Config();
    double atLevelOne = KineticsMoonJump.gravityReduction(config.gravityReductionBase, config.gravityReductionFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsMoonJump.gravityReduction(config.gravityReductionBase, config.gravityReductionFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.21D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.45D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void gravityReductionClampsToUnitRange() {
    assertThat(KineticsMoonJump.gravityReduction(2.0D, 3.0D, 1.0D)).isCloseTo(1.0D, offset(1.0E-9D));
    assertThat(KineticsMoonJump.gravityReduction(-2.0D, 0D, 0D)).isCloseTo(0D, offset(1.0E-9D));
  }

  @Test
  void gravityReductionRejectsNaN() {
    assertThat(KineticsMoonJump.gravityReduction(Double.NaN, 0.3D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsMoonJump.gravityReduction(0.15D, Double.NaN, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
  }

  @Test
  void floatWindowTicksGrowsWithLevelAndFloorsAtOne() {
    KineticsMoonJump.Config config = new KineticsMoonJump.Config();
    long atLevelOne = KineticsMoonJump.floatWindowTicks(config.floatWindowTicksBase, config.floatWindowTicksFactor, levelPercent(1, config.maxLevel));
    long atMaxLevel = KineticsMoonJump.floatWindowTicks(config.floatWindowTicksBase, config.floatWindowTicksFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isEqualTo(24L);
    assertThat(atMaxLevel).isEqualTo(40L);
    assertThat(KineticsMoonJump.floatWindowTicks(0D, 0D, 0D)).isEqualTo(1L);
    assertThat(KineticsMoonJump.floatWindowTicks(-40D, 0D, 1D)).isEqualTo(1L);
  }

  @Test
  void jumpHandlerObservesFinalCancellationState() throws ReflectiveOperationException {
    Method handler = KineticsMoonJump.class.getDeclaredMethod("on", PlayerJumpEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
