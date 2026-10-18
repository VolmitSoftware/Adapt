package art.arcane.adapt.content.skill;

import art.arcane.adapt.content.skill.golemancy.GolemXpRules;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SkillGolemancyServantShareTest {
  private static final SkillGolemancy.Config DEFAULTS = new SkillGolemancy.Config();

  @ParameterizedTest
  @CsvSource({
      "0.35, 20, false, false, 12.25",
      "1.0, 20, false, false, 35",
      "0.35, 24, true, false, 20.475",
      "1.0, 20, false, true, 10.5",
      "0.0, 20, false, false, 0"
  })
  void aServantKillPaysTheShareOfTheOwnedGolemKill(double share, double victimMaxHealth, boolean raid, boolean spawner, double expected) {
    double killValue = GolemXpRules.killValue(DEFAULTS.golemKillXpBase, victimMaxHealth, DEFAULTS.golemKillMaxHealthMultiplier);
    double owned = GolemXpRules.ownedKillXp(killValue, raid, DEFAULTS.raidXpMultiplier, spawner, DEFAULTS.spawnerMobXpMultiplier);

    assertThat(GolemXpRules.servantShareXp(share, owned)).isCloseTo(expected, offset(1e-9));
  }

  @Test
  void brokenSharesOrValuesPayNothing() {
    assertThat(GolemXpRules.servantShareXp(Double.NaN, 35D)).isZero();
    assertThat(GolemXpRules.servantShareXp(-0.35D, 35D)).isZero();
    assertThat(GolemXpRules.servantShareXp(0.35D, Double.POSITIVE_INFINITY)).isZero();
    assertThat(GolemXpRules.servantShareXp(0.35D, -10D)).isZero();
  }

  @Test
  void theRewardKeyNamesTheVictimType() {
    assertThat(GolemXpRules.servantRewardKey(EntityType.ZOMBIE)).isEqualTo("golemancy:servant:zombie");
  }
}
