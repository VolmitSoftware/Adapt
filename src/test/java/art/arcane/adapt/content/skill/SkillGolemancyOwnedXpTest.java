package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.content.skill.golemancy.GolemXpCooldowns;
import art.arcane.adapt.content.skill.golemancy.GolemXpRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SkillGolemancyOwnedXpTest {
  private static final SkillGolemancy.Config DEFAULTS = new SkillGolemancy.Config();

  @Test
  void ownedGolemXpDefaultsMatchTheEconomy() {
    assertThat(DEFAULTS.bindSnowXp).isEqualTo(100D);
    assertThat(DEFAULTS.bindCopperXp).isEqualTo(220D);
    assertThat(DEFAULTS.bindIronXp).isEqualTo(450D);
    assertThat(DEFAULTS.firstBindXp).isEqualTo(600D);
    assertThat(DEFAULTS.assistDamageXpMultiplier).isEqualTo(3.0D);
    assertThat(DEFAULTS.assistBodyMultiplierFrost).isEqualTo(2.0D);
    assertThat(DEFAULTS.assistBodyMultiplierCopper).isEqualTo(1.0D);
    assertThat(DEFAULTS.assistBodyMultiplierIron).isEqualTo(1.0D);
    assertThat(DEFAULTS.assistBodyMultiplierBone).isEqualTo(0.35D);
    assertThat(DEFAULTS.assistCooldownMillis).isEqualTo(1500L);
    assertThat(DEFAULTS.raidXpMultiplier).isEqualTo(1.5D);
    assertThat(DEFAULTS.ownerProximityRadius).isEqualTo(48D);
    assertThat(DEFAULTS.distantOwnerXpMultiplier).isEqualTo(0.25D);
    assertThat(DEFAULTS.tankXpMultiplier).isEqualTo(1.5D);
    assertThat(DEFAULTS.tankCooldownMillis).isEqualTo(2000L);
    assertThat(DEFAULTS.challengeBoundReward).isEqualTo(500D);
    assertThat(DEFAULTS.challengeGolemKillsReward).isEqualTo(500D);
  }

  @ParameterizedTest
  @CsvSource({
      "FROST, 100, 2.0",
      "COPPER, 220, 1.0",
      "IRON, 450, 1.0",
      "BONE, 0, 0.35"
  })
  void bodiesMapToTheirBindXpAndAssistMultiplier(GolemBody body, double bindXp, double assistMultiplier) {
    assertThat(SkillGolemancy.bindXp(DEFAULTS, body)).isEqualTo(bindXp);
    assertThat(SkillGolemancy.assistBodyMultiplier(DEFAULTS, body)).isEqualTo(assistMultiplier);
  }

  @Test
  void bodyMappingsFollowTheConfig() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.bindSnowXp = 11D;
    config.bindCopperXp = 22D;
    config.bindIronXp = 33D;
    config.assistBodyMultiplierFrost = 0.1D;
    config.assistBodyMultiplierCopper = 0.2D;
    config.assistBodyMultiplierIron = 0.3D;
    config.assistBodyMultiplierBone = 0.4D;

    assertThat(SkillGolemancy.bindXp(config, GolemBody.FROST)).isEqualTo(11D);
    assertThat(SkillGolemancy.bindXp(config, GolemBody.COPPER)).isEqualTo(22D);
    assertThat(SkillGolemancy.bindXp(config, GolemBody.IRON)).isEqualTo(33D);
    assertThat(SkillGolemancy.assistBodyMultiplier(config, GolemBody.FROST)).isEqualTo(0.1D);
    assertThat(SkillGolemancy.assistBodyMultiplier(config, GolemBody.COPPER)).isEqualTo(0.2D);
    assertThat(SkillGolemancy.assistBodyMultiplier(config, GolemBody.IRON)).isEqualTo(0.3D);
    assertThat(SkillGolemancy.assistBodyMultiplier(config, GolemBody.BONE)).isEqualTo(0.4D);
  }

  @ParameterizedTest
  @CsvSource({
      "FROST, 10, 20, false, 60",
      "COPPER, 10, 20, false, 30",
      "IRON, 10, 20, false, 30",
      "BONE, 10, 20, false, 10.5",
      "IRON, 30, 12, false, 36",
      "FROST, 30, 4, false, 24",
      "IRON, 10, 20, true, 9",
      "FROST, 10, 0, false, 0"
  })
  void assistXpTableUsesTheDefaults(GolemBody body, double damage, double victimHealth, boolean spawner, double expected) {
    double dealt = GolemXpRules.effectiveDamage(damage, victimHealth);
    double xp = GolemXpRules.assistXp(dealt, DEFAULTS.assistDamageXpMultiplier, SkillGolemancy.assistBodyMultiplier(DEFAULTS, body),
        spawner, DEFAULTS.spawnerMobXpMultiplier);

    assertThat(xp).isCloseTo(expected, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "20, false, false, 35",
      "24, true, false, 58.5",
      "20, false, true, 10.5",
      "24, true, true, 17.55",
      "100, false, false, 115"
  })
  void killXpTableUsesTheDefaults(double victimMaxHealth, boolean raid, boolean spawner, double expected) {
    double killValue = GolemXpRules.killValue(DEFAULTS.golemKillXpBase, victimMaxHealth, DEFAULTS.golemKillMaxHealthMultiplier);
    double xp = GolemXpRules.ownedKillXp(killValue, raid, DEFAULTS.raidXpMultiplier, spawner, DEFAULTS.spawnerMobXpMultiplier);

    assertThat(xp).isCloseTo(expected, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "8, 100, false, 12",
      "8, 5, false, 7.5",
      "8, 100, true, 3.6"
  })
  void tankXpTableUsesTheDefaults(double damage, double golemHealth, boolean spawner, double expected) {
    double taken = GolemXpRules.effectiveDamage(damage, golemHealth);
    double xp = GolemXpRules.tankXp(taken, DEFAULTS.tankXpMultiplier, spawner, DEFAULTS.spawnerMobXpMultiplier);

    assertThat(xp).isCloseTo(expected, offset(1e-9));
  }

  @Test
  void distantOwnersEarnAQuarterByDefault() {
    double radius = DEFAULTS.ownerProximityRadius;
    double multiplier = DEFAULTS.distantOwnerXpMultiplier;

    assertThat(GolemXpRules.ownerDistanceFactor(48D * 48D, radius, multiplier)).isEqualTo(1D);
    assertThat(GolemXpRules.ownerDistanceFactor(49D * 49D, radius, multiplier)).isEqualTo(0.25D);
    assertThat(GolemXpRules.ownerDistanceFactor(Double.POSITIVE_INFINITY, radius, multiplier)).isEqualTo(0.25D);
  }

  @Test
  void assistCooldownIsPerOwner() {
    GolemXpCooldowns<UUID> cooldowns = new GolemXpCooldowns<>();
    UUID owner = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    long cooldown = DEFAULTS.assistCooldownMillis;

    assertThat(cooldowns.tryClaim(owner, 10_000L, cooldown)).isTrue();
    assertThat(cooldowns.tryClaim(other, 10_000L, cooldown)).isTrue();
    assertThat(cooldowns.tryClaim(owner, 11_499L, cooldown)).isFalse();
    assertThat(cooldowns.tryClaim(owner, 11_500L, cooldown)).isTrue();
    assertThat(cooldowns.tryClaim(other, 11_499L, cooldown)).isFalse();
  }

  @Test
  void tankCooldownIsTwoSecondsPerOwner() {
    GolemXpCooldowns<UUID> cooldowns = new GolemXpCooldowns<>();
    UUID owner = UUID.randomUUID();
    long cooldown = DEFAULTS.tankCooldownMillis;

    assertThat(cooldowns.tryClaim(owner, 0L, cooldown)).isTrue();
    assertThat(cooldowns.tryClaim(owner, 1_999L, cooldown)).isFalse();
    assertThat(cooldowns.tryClaim(owner, 2_000L, cooldown)).isTrue();
    assertThat(cooldowns.tryClaim(UUID.randomUUID(), 2_001L, cooldown)).isTrue();
  }
}
