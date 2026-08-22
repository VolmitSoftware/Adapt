package art.arcane.adapt.content.adaptation.kinetics;

import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsQuakeGuardTest {
  @Test
  void configDefaultsAreSane() {
    KineticsQuakeGuard.Config config = new KineticsQuakeGuard.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsQuakeGuard.Config config = new KineticsQuakeGuard.Config();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.kbResistBase).isEqualTo(0.3D);
    assertThat(config.kbResistFactor).isEqualTo(0.5D);
    assertThat(config.toughnessBase).isEqualTo(2D);
    assertThat(config.toughnessFactor).isEqualTo(4D);
    assertThat(config.safeFallBase).isEqualTo(2D);
    assertThat(config.safeFallFactor).isEqualTo(4D);
    assertThat(config.braceTicksBase).isEqualTo(40D);
    assertThat(config.braceTicksFactor).isEqualTo(40D);
  }

  @Test
  void kbResistGrowsWithLevel() {
    KineticsQuakeGuard.Config config = new KineticsQuakeGuard.Config();
    double atLevelOne = config.kbResistBase + (levelPercent(1, config.maxLevel) * config.kbResistFactor);
    double atMaxLevel = config.kbResistBase + (levelPercent(config.maxLevel, config.maxLevel) * config.kbResistFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void toughnessGrowsWithLevel() {
    KineticsQuakeGuard.Config config = new KineticsQuakeGuard.Config();
    double atLevelOne = config.toughnessBase + (levelPercent(1, config.maxLevel) * config.toughnessFactor);
    double atMaxLevel = config.toughnessBase + (levelPercent(config.maxLevel, config.maxLevel) * config.toughnessFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void safeFallGrowsWithLevel() {
    KineticsQuakeGuard.Config config = new KineticsQuakeGuard.Config();
    double atLevelOne = config.safeFallBase + (levelPercent(1, config.maxLevel) * config.safeFallFactor);
    double atMaxLevel = config.safeFallBase + (levelPercent(config.maxLevel, config.maxLevel) * config.safeFallFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void braceTicksGrowWithLevel() {
    KineticsQuakeGuard.Config config = new KineticsQuakeGuard.Config();
    long atLevelOne = Math.round(config.braceTicksBase + (levelPercent(1, config.maxLevel) * config.braceTicksFactor));
    long atMaxLevel = Math.round(config.braceTicksBase + (levelPercent(config.maxLevel, config.maxLevel) * config.braceTicksFactor));
    assertThat(atLevelOne).isGreaterThan(0L);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void braceTicksNeverCreatePermanentTimedAttributes() {
    assertThat(KineticsQuakeGuard.braceTicks(0D, 0D, 0D)).isEqualTo(1L);
    assertThat(KineticsQuakeGuard.braceTicks(-20D, 5D, 1D)).isEqualTo(1L);
    assertThat(KineticsQuakeGuard.braceTicks(40D, 40D, 0.5D)).isEqualTo(60L);
  }

  @Test
  void smashHandlerObservesAtMonitor() throws ReflectiveOperationException {
    Method handler = KineticsQuakeGuard.class.getDeclaredMethod("on", EntityAttemptSmashAttackEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }

  @Test
  void smashLandsHonorsExplicitResults() {
    assertThat(KineticsQuakeGuard.smashLands(false, Event.Result.ALLOW)).isTrue();
    assertThat(KineticsQuakeGuard.smashLands(true, Event.Result.ALLOW)).isTrue();
    assertThat(KineticsQuakeGuard.smashLands(true, Event.Result.DENY)).isFalse();
    assertThat(KineticsQuakeGuard.smashLands(false, Event.Result.DENY)).isFalse();
  }

  @Test
  void smashLandsFallsBackToOriginalResultOnDefault() {
    assertThat(KineticsQuakeGuard.smashLands(true, Event.Result.DEFAULT)).isTrue();
    assertThat(KineticsQuakeGuard.smashLands(false, Event.Result.DEFAULT)).isFalse();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
