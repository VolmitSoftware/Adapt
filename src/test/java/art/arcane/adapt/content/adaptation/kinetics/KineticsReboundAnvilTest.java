package art.arcane.adapt.content.adaptation.kinetics;

import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsReboundAnvilTest {
  @Test
  void configDefaultsAreSane() {
    KineticsReboundAnvil.Config config = new KineticsReboundAnvil.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsReboundAnvil.Config config = new KineticsReboundAnvil.Config();
    assertThat(config.maxLevel).isEqualTo(3);
    assertThat(config.bouncinessBase).isEqualTo(0.5D);
    assertThat(config.bouncinessFactor).isEqualTo(0.6D);
    assertThat(config.fallReliefBase).isEqualTo(0.4D);
    assertThat(config.fallReliefFactor).isEqualTo(0.4D);
    assertThat(config.windowTicksBase).isEqualTo(40D);
    assertThat(config.windowTicksFactor).isEqualTo(30D);
  }

  @Test
  void bouncinessGrowsWithLevel() {
    KineticsReboundAnvil.Config config = new KineticsReboundAnvil.Config();
    double atLevelOne = config.bouncinessBase + (levelPercent(1, config.maxLevel) * config.bouncinessFactor);
    double atMaxLevel = config.bouncinessBase + (levelPercent(config.maxLevel, config.maxLevel) * config.bouncinessFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void fallReliefGrowsWithLevel() {
    KineticsReboundAnvil.Config config = new KineticsReboundAnvil.Config();
    double atLevelOne = config.fallReliefBase + (levelPercent(1, config.maxLevel) * config.fallReliefFactor);
    double atMaxLevel = config.fallReliefBase + (levelPercent(config.maxLevel, config.maxLevel) * config.fallReliefFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void windowTicksGrowWithLevel() {
    KineticsReboundAnvil.Config config = new KineticsReboundAnvil.Config();
    long atLevelOne = Math.round(config.windowTicksBase + (levelPercent(1, config.maxLevel) * config.windowTicksFactor));
    long atMaxLevel = Math.round(config.windowTicksBase + (levelPercent(config.maxLevel, config.maxLevel) * config.windowTicksFactor));
    assertThat(atLevelOne).isGreaterThan(0L);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void windowTicksNeverCreatePermanentTimedAttributes() {
    assertThat(KineticsReboundAnvil.windowTicks(0D, 0D, 0D)).isEqualTo(1L);
    assertThat(KineticsReboundAnvil.windowTicks(-20D, 5D, 1D)).isEqualTo(1L);
    assertThat(KineticsReboundAnvil.windowTicks(40D, 30D, 0.5D)).isEqualTo(55L);
  }

  @Test
  void smashHandlerObservesAtMonitor() throws ReflectiveOperationException {
    Method handler = KineticsReboundAnvil.class.getDeclaredMethod("on", EntityAttemptSmashAttackEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
  }

  @Test
  void smashLandsHonorsExplicitResults() {
    assertThat(KineticsReboundAnvil.smashLands(false, Event.Result.ALLOW)).isTrue();
    assertThat(KineticsReboundAnvil.smashLands(true, Event.Result.ALLOW)).isTrue();
    assertThat(KineticsReboundAnvil.smashLands(true, Event.Result.DENY)).isFalse();
    assertThat(KineticsReboundAnvil.smashLands(false, Event.Result.DENY)).isFalse();
  }

  @Test
  void smashLandsFallsBackToOriginalResultOnDefault() {
    assertThat(KineticsReboundAnvil.smashLands(true, Event.Result.DEFAULT)).isTrue();
    assertThat(KineticsReboundAnvil.smashLands(false, Event.Result.DEFAULT)).isFalse();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
