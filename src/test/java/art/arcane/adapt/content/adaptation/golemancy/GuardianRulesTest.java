package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class GuardianRulesTest {
  private static final List<Integer> PERCEPTION = List.of(8, 12, 16, 20, 24);
  private static final GuardianRules.Options DEFAULTS = new GuardianRules.Options(true, false, false, true);

  @Test
  void perceptionFollowsTheLevelAndTreatsLevelZeroAsLevelOne() {
    assertThat(GuardianRules.perception(PERCEPTION, 1, 32D)).isEqualTo(8D);
    assertThat(GuardianRules.perception(PERCEPTION, 3, 32D)).isEqualTo(16D);
    assertThat(GuardianRules.perception(PERCEPTION, 5, 32D)).isEqualTo(24D);
    assertThat(GuardianRules.perception(PERCEPTION, 9, 32D)).isEqualTo(24D);
    assertThat(GuardianRules.perception(PERCEPTION, 0, 32D)).isEqualTo(8D);
  }

  @Test
  void perceptionIsClampedBetweenOneBlockAndTheLeashRadius() {
    assertThat(GuardianRules.perception(PERCEPTION, 5, 20D)).isEqualTo(20D);
    assertThat(GuardianRules.perception(List.of(-3), 1, 32D)).isEqualTo(1D);
    assertThat(GuardianRules.perception(List.of(), 2, 32D)).isEqualTo(1D);
    assertThat(GuardianRules.perception(PERCEPTION, 1, 0D)).isEqualTo(1D);
  }

  @Test
  void perksUnlockAtTheirLevel() {
    assertThat(GuardianRules.unlocked(1, 2)).isFalse();
    assertThat(GuardianRules.unlocked(2, 2)).isTrue();
    assertThat(GuardianRules.unlocked(5, 4)).isTrue();
    assertThat(GuardianRules.unlocked(0, 0)).isFalse();
  }

  @Test
  void hostileMobsAreTargetedButCreepersAndNamedMobsOnlyWhenAllowed() {
    assertThat(GuardianRules.reason(hostile(), DEFAULTS)).isEqualTo(GuardianRules.Reason.HOSTILE);
    assertThat(GuardianRules.reason(creeper(), DEFAULTS)).isEqualTo(GuardianRules.Reason.NONE);
    assertThat(GuardianRules.reason(named(), DEFAULTS)).isEqualTo(GuardianRules.Reason.NONE);
    assertThat(GuardianRules.reason(creeper(), new GuardianRules.Options(true, false, true, true))).isEqualTo(GuardianRules.Reason.HOSTILE);
    assertThat(GuardianRules.reason(named(), new GuardianRules.Options(true, false, false, false))).isEqualTo(GuardianRules.Reason.HOSTILE);
    assertThat(GuardianRules.reason(passive(), DEFAULTS)).isEqualTo(GuardianRules.Reason.NONE);
  }

  @Test
  void mobsThreateningTheOwnerOrGolemCountOnlyWhileTheOwnerIsOnline() {
    GuardianRules.Candidate threat = new GuardianRules.Candidate(false, false, false, false, false, true, false);
    GuardianRules.Candidate namedThreat = new GuardianRules.Candidate(false, false, false, false, true, true, false);

    assertThat(GuardianRules.reason(threat, DEFAULTS)).isEqualTo(GuardianRules.Reason.THREAT);
    assertThat(GuardianRules.reason(namedThreat, DEFAULTS)).isEqualTo(GuardianRules.Reason.NONE);
    assertThat(GuardianRules.reason(threat, new GuardianRules.Options(false, false, false, true))).isEqualTo(GuardianRules.Reason.NONE);
    assertThat(GuardianRules.reason(hostile(), new GuardianRules.Options(false, true, false, true))).isEqualTo(GuardianRules.Reason.HOSTILE);
  }

  @Test
  void retaliationTargetsWhateverHurtTheOwnerIncludingPlayersOnceUnlocked() {
    GuardianRules.Options retaliating = new GuardianRules.Options(true, true, false, true);
    GuardianRules.Candidate player = new GuardianRules.Candidate(false, true, false, false, false, false, true);
    GuardianRules.Candidate namedCreeper = new GuardianRules.Candidate(false, false, true, true, true, false, true);
    GuardianRules.Candidate friendly = new GuardianRules.Candidate(true, false, true, false, false, true, true);

    assertThat(GuardianRules.reason(player, retaliating)).isEqualTo(GuardianRules.Reason.RETALIATE);
    assertThat(GuardianRules.reason(namedCreeper, retaliating)).isEqualTo(GuardianRules.Reason.RETALIATE);
    assertThat(GuardianRules.reason(player, DEFAULTS)).isEqualTo(GuardianRules.Reason.NONE);
    assertThat(GuardianRules.reason(player, new GuardianRules.Options(false, true, false, true))).isEqualTo(GuardianRules.Reason.NONE);
    assertThat(GuardianRules.reason(friendly, retaliating)).isEqualTo(GuardianRules.Reason.NONE);
  }

  @Test
  void playersAreNeverTargetedWithoutRetaliation() {
    GuardianRules.Candidate hostilePlayer = new GuardianRules.Candidate(false, true, true, false, false, true, false);

    assertThat(GuardianRules.reason(hostilePlayer, new GuardianRules.Options(true, true, true, false))).isEqualTo(GuardianRules.Reason.NONE);
  }

  @Test
  void bulwarkCutsDamageExceptFromTheVoidAndKillCommands() {
    assertThat(GuardianRules.bulwarkDamage(10D, 0.15D)).isCloseTo(8.5D, offset(1e-9));
    assertThat(GuardianRules.bulwarkDamage(10D, 2D)).isZero();
    assertThat(GuardianRules.bulwarkDamage(10D, -1D)).isEqualTo(10D);
    assertThat(GuardianRules.bulwarkDamage(10D, Double.NaN)).isEqualTo(10D);
    assertThat(GuardianRules.shields(DamageCause.ENTITY_ATTACK)).isTrue();
    assertThat(GuardianRules.shields(DamageCause.PROJECTILE)).isTrue();
    assertThat(GuardianRules.shields(DamageCause.FALL)).isTrue();
    assertThat(GuardianRules.shields(DamageCause.VOID)).isFalse();
    assertThat(GuardianRules.shields(DamageCause.KILL)).isFalse();
    assertThat(GuardianRules.shields(DamageCause.WORLD_BORDER)).isFalse();
    assertThat(GuardianRules.shields(DamageCause.SUICIDE)).isFalse();
  }

  @Test
  void lastStandTriggersBelowTheThresholdOnlyWhenTheGolemSurvivesTheHit() {
    assertThat(GuardianRules.lastStand(19.9D, 100D, 0.2D)).isTrue();
    assertThat(GuardianRules.lastStand(20D, 100D, 0.2D)).isFalse();
    assertThat(GuardianRules.lastStand(0D, 100D, 0.2D)).isFalse();
    assertThat(GuardianRules.lastStand(-4D, 100D, 0.2D)).isFalse();
    assertThat(GuardianRules.lastStand(5D, 0D, 0.2D)).isFalse();
  }

  @Test
  void loadingClampsRangesAndFractions() {
    assertThat(GuardianRules.sentryRange(6D)).isEqualTo(6D);
    assertThat(GuardianRules.sentryRange(0.2D)).isEqualTo(1D);
    assertThat(GuardianRules.sentryRange(12D)).isEqualTo(10D);
    assertThat(GuardianRules.sentryRange(Double.NaN)).isEqualTo(10D);
    assertThat(GuardianRules.interceptInterval(3_000L)).isEqualTo(3_000L);
    assertThat(GuardianRules.interceptInterval(100L)).isEqualTo(500L);
    assertThat(GuardianRules.interceptInterval(-5L)).isEqualTo(500L);
    assertThat(GuardianRules.bulwarkRadius(4D)).isEqualTo(4D);
    assertThat(GuardianRules.bulwarkRadius(-2D)).isZero();
    assertThat(GuardianRules.bulwarkRadius(99D)).isEqualTo(16D);
    assertThat(GuardianRules.bulwarkRadius(Double.POSITIVE_INFINITY)).isEqualTo(4D);
    assertThat(GuardianRules.fraction(0.15D, 0.5D)).isEqualTo(0.15D);
    assertThat(GuardianRules.fraction(1.5D, 0.5D)).isEqualTo(1D);
    assertThat(GuardianRules.fraction(-0.1D, 0.5D)).isZero();
    assertThat(GuardianRules.fraction(Double.NaN, 0.5D)).isEqualTo(0.5D);
  }

  private static GuardianRules.Candidate hostile() {
    return new GuardianRules.Candidate(false, false, true, false, false, false, false);
  }

  private static GuardianRules.Candidate creeper() {
    return new GuardianRules.Candidate(false, false, true, true, false, false, false);
  }

  private static GuardianRules.Candidate named() {
    return new GuardianRules.Candidate(false, false, true, false, true, false, false);
  }

  private static GuardianRules.Candidate passive() {
    return new GuardianRules.Candidate(false, false, false, false, false, false, false);
  }
}
