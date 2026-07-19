package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwordNewAdaptationsTest {
  @Test
  void lungeStrikeIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsLungeStrike.class);
  }

  @Test
  void bladeFlowIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsBladeFlow.class);
  }

  @Test
  void duelistsFocusIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsDuelistsFocus.class);
  }

  @Test
  void hamstringIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsHamstring.class);
  }

  @Test
  void configDefaultsAreSane() {
    List<AdaptationConfig> configs = List.of(
        new SwordsLungeStrike.Config(),
        new SwordsBladeFlow.Config(),
        new SwordsDuelistsFocus.Config(),
        new SwordsWhetstoneRitual.Config(),
        new SwordsCrescentGuard.Config(),
        new SwordsHamstring.Config(),
        new SwordsHeirloomEdge.Config());

    for (AdaptationConfig config : configs) {
      assertThat(config.enabled).isTrue();
      assertThat(config.maxLevel).isGreaterThan(1);
      assertThat(config.baseCost).isGreaterThan(0);
      assertThat(config.initialCost).isGreaterThan(0);
      assertThat(config.costFactor).isGreaterThan(0D);
    }
  }

  @Test
  void bladeFlowStackCapGrowsWithLevel() {
    SwordsBladeFlow.Config config = new SwordsBladeFlow.Config();
    int capAtLevelOne = stackCap(config, 1);
    int capAtMaxLevel = stackCap(config, config.maxLevel);
    assertThat(capAtLevelOne).isGreaterThanOrEqualTo(1);
    assertThat(capAtMaxLevel).isGreaterThan(capAtLevelOne);
  }

  @Test
  void heirloomBonusCapGrowsWithLevel() {
    SwordsHeirloomEdge.Config config = new SwordsHeirloomEdge.Config();
    double capAtLevelOne = config.capBase + (levelPercent(1, config.maxLevel) * config.capFactor);
    double capAtMaxLevel = config.capBase + (levelPercent(config.maxLevel, config.maxLevel) * config.capFactor);
    assertThat(capAtLevelOne).isGreaterThan(0D);
    assertThat(capAtMaxLevel).isGreaterThan(capAtLevelOne);
  }

  private static int stackCap(SwordsBladeFlow.Config config, int level) {
    return Math.max(1, (int) Math.round(config.stackCapBase + (levelPercent(level, config.maxLevel) * config.stackCapFactor)));
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }

  private static void assertIgnoresCancelledDamage(Class<?> adaptationType) throws ReflectiveOperationException {
    Method handler = adaptationType.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }
}
