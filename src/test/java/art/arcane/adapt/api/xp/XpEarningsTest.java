package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.notification.Notifier;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class XpEarningsTest extends AdaptTestBase {
  private static final long NOW = 1_000_000L;

  @Test
  void aBurstCannotCreditMoreThanTheSkillPlayerOrAbsoluteAllowance() {
    AdaptConfig.XpEarningLimits limits = limits();
    XpEarnings earnings = new XpEarnings();
    double available = Math.min(baseBurst(), limits.getAbsoluteBurstXp());
    double credited = earnings.claim("mining", Double.MAX_VALUE, 1D, NOW);
    assertThat(credited).isEqualTo(available);
    assertThat(earnings.claim("mining", Double.MAX_VALUE, 1D, NOW)).isZero();
    assertThat(earnings.budget("mining").skillUsed()).isEqualTo(credited);
    assertThat(earnings.budget("mining").playerUsed()).isEqualTo(credited);
    assertThat(earnings.budget("mining").absoluteUsed()).isEqualTo(credited);
  }

  @Test
  void exhaustedBurstRecoversAtConfiguredRatesAndCannotBankBeyondCapacity() {
    AdaptConfig.XpEarningLimits limits = limits();
    XpEarnings earnings = new XpEarnings();
    double first = earnings.claim("mining", Double.MAX_VALUE, 1D, NOW);
    double skillAvailable = Math.min(limits.getSkillBurstXp(), limits.getSkillBurstXp() - first + limits.getSkillXpPerMinute());
    double playerAvailable = Math.min(limits.getPlayerBurstXp(), limits.getPlayerBurstXp() - first + limits.getPlayerXpPerMinute());
    double absoluteAvailable = Math.min(limits.getAbsoluteBurstXp(), limits.getAbsoluteBurstXp() - first + limits.getAbsoluteXpPerMinute());
    double recovered = earnings.claim("mining", Double.MAX_VALUE, 1D, NOW + 60_000L);
    assertThat(recovered).isCloseTo(Math.min(skillAvailable, Math.min(playerAvailable, absoluteAvailable)), within(0.000001D));
    double recoveredFully = earnings.claim("mining", Double.MAX_VALUE, 1D, NOW + 86_400_000L);
    assertThat(recoveredFully).isEqualTo(Math.min(baseBurst(), limits.getAbsoluteBurstXp()));
  }

  @Test
  void sustainedClaimsAreSoftenedBeforeTheBurstIsExhausted() {
    XpEarnings earnings = new XpEarnings();
    double capacity = baseBurst();
    earnings.claim("mining", capacity * 0.75D, 1D, NOW);
    double requested = capacity * 0.01D;
    double credited = earnings.claim("mining", requested, 1D, NOW);
    assertThat(credited).isGreaterThan(0D).isLessThan(requested);
    assertThat(earnings.budget("mining").skillUsed()).isLessThan(limits().getSkillBurstXp());
  }

  @Test
  void approvedBoostScalesAllowanceButCannotExceedItsConfiguredMaximumOrAbsoluteCeiling() {
    AdaptConfig.XpEarningLimits limits = limits();
    double boost = Math.min(2D, limits.getMaximumAllowanceBoost());
    XpEarnings earnings = new XpEarnings();
    double credited = earnings.claim("mining", Double.MAX_VALUE, boost, NOW);
    assertThat(credited).isEqualTo(Math.min(baseBurst() * boost, limits.getAbsoluteBurstXp()));
    assertThat(earnings.budget("mining").skillUsed()).isCloseTo(credited / boost, within(0.000001D));
    assertThat(earnings.budget("mining").absoluteUsed()).isEqualTo(credited);

    XpEarnings maximum = new XpEarnings();
    double total = 0D;
    int skills = (int) Math.ceil(limits.getPlayerBurstXp() / limits.getSkillBurstXp()) + 2;
    for (int index = 0; index < skills; index++) {
      total += maximum.claim("skill-" + index, Double.MAX_VALUE, limits.getMaximumAllowanceBoost() * 10D, NOW);
    }
    assertThat(total).isCloseTo(Math.min(limits.getPlayerBurstXp() * limits.getMaximumAllowanceBoost(), limits.getAbsoluteBurstXp()), within(0.000001D));
    assertThat(maximum.budget("skill-0").absoluteUsed()).isLessThanOrEqualTo(limits.getAbsoluteBurstXp());
    assertThat(maximum.claim("another-skill", Double.MAX_VALUE, limits.getMaximumAllowanceBoost(), NOW)).isZero();
  }

  @Test
  void switchingSkillsCannotRefreshTheSharedPlayerAllowance() {
    AdaptConfig.XpEarningLimits limits = limits();
    XpEarnings earnings = new XpEarnings();
    double total = 0D;
    int skills = (int) Math.ceil(limits.getPlayerBurstXp() / limits.getSkillBurstXp()) + 2;
    for (int index = 0; index < skills; index++) {
      total += earnings.claim("skill-" + index, Double.MAX_VALUE, 1D, NOW);
    }
    assertThat(total).isCloseTo(Math.min(limits.getPlayerBurstXp(), limits.getAbsoluteBurstXp()), within(0.000001D));
    assertThat(earnings.claim("fresh-skill", 1D, 1D, NOW)).isZero();
  }

  @Test
  void clockRollbackDoesNotRestoreSpentAllowance() {
    XpEarnings earnings = new XpEarnings();
    earnings.claim("mining", Double.MAX_VALUE, 1D, NOW);
    assertThat(earnings.claim("mining", Double.MAX_VALUE, 1D, NOW - 60_000L)).isZero();
    assertThat(earnings.claim("mining", Double.MAX_VALUE, 1D, NOW)).isZero();
    assertThat(earnings.budget("mining").updatedAt()).isEqualTo(NOW);
    assertThat(earnings.claim("mining", Double.MAX_VALUE, 1D, NOW + 60_000L)).isGreaterThan(0D);
  }

  @Test
  void invalidNumbersCannotPoisonTheAllowance() {
    double[] invalidRequests = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0D, -1D};
    for (double requested : invalidRequests) {
      XpEarnings earnings = new XpEarnings();
      assertThat(earnings.claim("mining", requested, 1D, NOW)).isZero();
      assertThat(earnings.budget("mining").absoluteUsed()).isZero();
      assertThat(earnings.claim("mining", 1D, 1D, NOW)).isEqualTo(1D);
    }
    double[] invalidBoosts = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
    for (double boost : invalidBoosts) {
      XpEarnings earnings = new XpEarnings();
      assertThat(earnings.claim("mining", 1D, boost, NOW)).isFinite().isBetween(0D, 1D);
      assertThat(earnings.budget("mining").absoluteUsed()).isFinite();
      assertThat(earnings.claim("mining", 1D, 1D, NOW)).isEqualTo(1D);
    }
  }

  @Test
  void playerProfileRoundTripPreservesSpentAllowanceAndSourceAccounting() {
    PlayerData profile = new PlayerData();
    XpEarnings original = profile.getXpEarnings();
    double credited = original.claim("mining", Double.MAX_VALUE, 1D, NOW);
    original.record(new XpReceipt("mining", "stone", XpAward.Kind.GAMEPLAY, credited, credited, credited, credited, credited, NOW));
    PlayerData restored = PlayerData.fromJson(profile.toJson(true));
    assertThat(restored.getXpEarnings().budget("mining")).isEqualTo(original.budget("mining"));
    assertThat(restored.getXpEarnings().snapshot()).isEqualTo(original.snapshot());
    assertThat(restored.getXpEarnings().claim("mining", Double.MAX_VALUE, 1D, NOW)).isZero();
  }

  @Test
  void flushingAndSerializingPooledXpCreditsEachAcceptedAwardOnlyOnce() {
    PlayerData profile = new PlayerData();
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("mining");
    profile.getSkillLines().put("mining", line);
    XpEarnings earnings = profile.getXpEarnings();
    double requested = Math.min(baseBurst(), limits().getAbsoluteBurstXp()) * 0.01D;
    XpAward award = new XpAward("stone", requested, null, false, XpAward.Kind.GAMEPLAY);
    XpReceipt receipt = line.award(null, award, requested, earnings);
    XpEarnings.BudgetSnapshot spent = earnings.budget("mining");
    assertThat(receipt.credited()).isGreaterThan(0D).isLessThanOrEqualTo(requested);
    if (AdaptConfig.get().getXpIntegrity().isPooledPayoutEnabled()) {
      assertThat(line.getXp()).isZero();
    }
    line.flushXpPool(null);
    line.flushXpPool(null);
    PlayerData restored = PlayerData.fromJson(profile.toJson(true));
    assertThat(line.getXp()).isEqualTo(receipt.credited());
    assertThat(restored.getSkillLines().get("mining").getXp()).isEqualTo(receipt.credited());
    assertThat(earnings.budget("mining")).isEqualTo(spent);
    assertThat(restored.getXpEarnings().budget("mining")).isEqualTo(spent);
    assertThat(earnings.snapshot().getFirst().credited()).isEqualTo(receipt.credited());
    assertThat(earnings.snapshot().getFirst().awards()).isEqualTo(1L);
  }

  @Test
  void persistenceFlushDefersNotificationAndLaterDeliversItExactlyOnce() {
    assertThat(AdaptConfig.get().getXpIntegrity().isPooledPayoutEnabled()).isTrue();
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("mining");
    XpEarnings earnings = new XpEarnings();
    Notifier notifier = mock(Notifier.class);
    double requested = Math.min(baseBurst(), limits().getAbsoluteBurstXp()) * 0.01D;
    XpReceipt receipt = line.award(notifier, new XpAward("stone", requested, null, true, XpAward.Kind.GAMEPLAY), requested, earnings);
    XpEarnings.BudgetSnapshot spent = earnings.budget("mining");
    line.flushXpPool(null);
    line.flushXpPool(null);
    assertThat(line.getXp()).isEqualTo(receipt.credited());
    assertThat(line.getPooledNotifyXp()).isEqualTo(receipt.credited());
    verify(notifier, never()).notifyXP(anyString(), anyDouble());
    line.flushXpPool(notifier);
    line.flushXpPool(notifier);
    assertThat(line.getPooledNotifyXp()).isZero();
    assertThat(line.getXp()).isEqualTo(receipt.credited());
    assertThat(earnings.budget("mining")).isEqualTo(spent);
    verify(notifier, times(AdaptConfig.get().isActionbarNotifyXp() ? 1 : 0)).notifyXP("mining", receipt.credited());
  }

  private AdaptConfig.XpEarningLimits limits() {
    return AdaptConfig.get().getXpEarningLimits();
  }

  private double baseBurst() {
    return Math.min(limits().getSkillBurstXp(), limits().getPlayerBurstXp());
  }
}
