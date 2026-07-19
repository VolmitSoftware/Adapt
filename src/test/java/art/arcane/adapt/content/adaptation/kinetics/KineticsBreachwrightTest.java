package art.arcane.adapt.content.adaptation.kinetics;

import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsBreachwrightTest {
  @Test
  void configDefaultsAreSane() {
    KineticsBreachwright.Config config = new KineticsBreachwright.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void armorShredGrowsWithLevel() {
    KineticsBreachwright.Config config = new KineticsBreachwright.Config();
    double atLevelOne = config.armorShredBase + (levelPercent(1, config.maxLevel) * config.armorShredFactor);
    double atMaxLevel = config.armorShredBase + (levelPercent(config.maxLevel, config.maxLevel) * config.armorShredFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void toughnessShredGrowsWithLevel() {
    KineticsBreachwright.Config config = new KineticsBreachwright.Config();
    double atLevelOne = config.toughnessShredBase + (levelPercent(1, config.maxLevel) * config.toughnessShredFactor);
    double atMaxLevel = config.toughnessShredBase + (levelPercent(config.maxLevel, config.maxLevel) * config.toughnessShredFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void shredDurationTicksGrowsWithLevelAndClampsToOne() {
    KineticsBreachwright.Config config = new KineticsBreachwright.Config();
    int atLevelOne = KineticsBreachwright.shredDurationTicks(config.shredTicksBase, config.shredTicksFactor, levelPercent(1, config.maxLevel));
    int atMaxLevel = KineticsBreachwright.shredDurationTicks(config.shredTicksBase, config.shredTicksFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isGreaterThan(0);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
    assertThat(KineticsBreachwright.shredDurationTicks(-50D, 0D, 1D)).isEqualTo(1);
    assertThat(KineticsBreachwright.shredDurationTicks(Double.NaN, 0D, 1D)).isEqualTo(1);
  }

  @Test
  void resultAllowsShredRequiresAnEffectiveSuccessfulSmash() {
    assertThat(KineticsBreachwright.resultAllowsShred(false, Event.Result.ALLOW)).isTrue();
    assertThat(KineticsBreachwright.resultAllowsShred(true, Event.Result.DEFAULT)).isTrue();
    assertThat(KineticsBreachwright.resultAllowsShred(false, Event.Result.DEFAULT)).isFalse();
    assertThat(KineticsBreachwright.resultAllowsShred(true, Event.Result.DENY)).isFalse();
    assertThat(KineticsBreachwright.resultAllowsShred(true, null)).isFalse();
  }

  @Test
  void smashHandlerObservesAtMonitor() throws ReflectiveOperationException {
    Method handler = KineticsBreachwright.class.getDeclaredMethod("on", EntityAttemptSmashAttackEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
