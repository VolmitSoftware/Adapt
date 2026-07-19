package art.arcane.adapt.content.adaptation.kinetics;

import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsWindburstTest {
  @Test
  void configDefaultsAreSane() {
    KineticsWindburst.Config config = new KineticsWindburst.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void radiusGrowsWithLevel() {
    KineticsWindburst.Config config = new KineticsWindburst.Config();
    double atLevelOne = config.radiusBase + (levelPercent(1, config.maxLevel) * config.radiusFactor);
    double atMaxLevel = config.radiusBase + (levelPercent(config.maxLevel, config.maxLevel) * config.radiusFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void forceGrowsWithLevel() {
    KineticsWindburst.Config config = new KineticsWindburst.Config();
    double atLevelOne = config.forceBase + (levelPercent(1, config.maxLevel) * config.forceFactor);
    double atMaxLevel = config.forceBase + (levelPercent(config.maxLevel, config.maxLevel) * config.forceFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void minFallDistanceShrinksWithLevel() {
    KineticsWindburst.Config config = new KineticsWindburst.Config();
    double atLevelOne = KineticsWindburst.minFallDistance(config.minFallDistanceBase, config.minFallDistanceFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsWindburst.minFallDistance(config.minFallDistanceBase, config.minFallDistanceFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atMaxLevel).isLessThan(atLevelOne);
    assertThat(atMaxLevel).isCloseTo(2D, offset(1e-9));
  }

  @Test
  void minFallDistanceNeverGoesNegative() {
    assertThat(KineticsWindburst.minFallDistance(1D, -5D, 1D)).isEqualTo(0D);
    assertThat(KineticsWindburst.minFallDistance(Double.NaN, 0D, 1D)).isEqualTo(0D);
  }

  @Test
  void burstReadyComparesFallDistanceInclusive() {
    assertThat(KineticsWindburst.burstReady(3D, 3D)).isTrue();
    assertThat(KineticsWindburst.burstReady(8D, 3D)).isTrue();
    assertThat(KineticsWindburst.burstReady(2.9D, 3D)).isFalse();
    assertThat(KineticsWindburst.burstReady(0D, 3D)).isFalse();
  }

  @Test
  void burstReadyRejectsNaN() {
    assertThat(KineticsWindburst.burstReady(Double.NaN, 3D)).isFalse();
  }

  @Test
  void smashLandsRequiresAnEffectiveSuccessfulSmash() {
    assertThat(KineticsWindburst.smashLands(false, Event.Result.ALLOW)).isTrue();
    assertThat(KineticsWindburst.smashLands(true, Event.Result.DEFAULT)).isTrue();
    assertThat(KineticsWindburst.smashLands(false, Event.Result.DEFAULT)).isFalse();
    assertThat(KineticsWindburst.smashLands(true, Event.Result.DENY)).isFalse();
    assertThat(KineticsWindburst.smashLands(true, null)).isFalse();
  }

  @Test
  void smashHandlerObservesFinalResultAtMonitorPriority() throws ReflectiveOperationException {
    Method handler = KineticsWindburst.class.getDeclaredMethod("on", EntityAttemptSmashAttackEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
