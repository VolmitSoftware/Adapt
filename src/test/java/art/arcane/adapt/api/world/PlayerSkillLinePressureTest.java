package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.api.xp.XpEarnings;
import art.arcane.adapt.api.xp.XpReceipt;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlayerSkillLinePressureTest extends AdaptTestBase {
  private static final long NOW = 1_000_000L;

  @Test
  void firstSuccessfulAwardKeepsItsAcceptedValueBeforeRecordingPressure() {
    AdaptConfig.XpEarningLimits limits = AdaptConfig.get().getXpEarningLimits();
    double smaller = Math.min(limits.getSkillBurstXp(), Math.min(limits.getPlayerBurstXp(), limits.getAbsoluteBurstXp())) / 100D;
    double larger = smaller * 10D;
    XpReceipt firstSmall = awardFresh(smaller);
    XpReceipt firstLarge = awardFresh(larger);
    assertThat(firstSmall.eligible()).isEqualTo(smaller);
    assertThat(firstLarge.eligible()).isEqualTo(larger);
    assertThat(firstSmall.credited()).isEqualTo(smaller);
    assertThat(firstLarge.credited()).isEqualTo(firstSmall.credited() * 10D);
  }

  @Test
  void eachSuccessfulActionDampensLaterActionsWithoutDiscountingItself() throws Exception {
    PlayerSkillLine line = new PlayerSkillLine();
    assertThat(price(line, 10D, "mining:stone", NOW)).isEqualTo(1D);
    double firstPressure = line.getSkillStaleness().getPressure();
    assertThat(firstPressure).isPositive();
    double second = price(line, 10D, "mining:stone", NOW);
    double third = price(line, 10D, "mining:stone", NOW);
    assertThat(second).isGreaterThan(0D).isLessThan(1D);
    assertThat(third).isGreaterThan(0D).isLessThan(second);
    assertThat(line.getSkillStaleness().getPressure()).isCloseTo(firstPressure * 3D, within(0.000001D));
  }

  @Test
  void rapidlySwitchingSkillsCannotRecoverPressureWithoutElapsedTime() throws Exception {
    PlayerData profile = new PlayerData();
    FixedClockSkillLine mining = new FixedClockSkillLine("mining");
    FixedClockSkillLine crafting = new FixedClockSkillLine("crafting");
    profile.getSkillLines().put("mining", mining);
    profile.getSkillLines().put("crafting", crafting);
    for (int index = 0; index < 20; index++) {
      price(mining, 10D, "mining:stone", NOW);
    }
    double skillPressure = mining.getSkillStaleness().getPressure();
    double activityPressure = mining.getActivityStaleness().get("mining:stone").getPressure();
    for (int index = 0; index < 100; index++) {
      profile.noteActivity("mining");
      profile.noteActivity("crafting");
    }
    assertThat(mining.getSkillStaleness().getPressure()).isEqualTo(skillPressure);
    assertThat(mining.getActivityStaleness().get("mining:stone").getPressure()).isEqualTo(activityPressure);
  }

  @Test
  void anIdleIntervalRecoversThePriceBeforeTheNextActionAddsPressure() throws Exception {
    PlayerSkillLine rested = new PlayerSkillLine();
    PlayerSkillLine active = new PlayerSkillLine();
    for (int index = 0; index < 20; index++) {
      price(rested, 10D, "mining:stone", NOW);
      price(active, 10D, "mining:stone", NOW);
    }
    AdaptConfig.FarmPrevention prevention = AdaptConfig.get().getFarmPrevention();
    long recovery = Math.max(prevention.getSkillRecoveryMillis(), prevention.getActivityRecoveryMillis());
    double uninterrupted = price(active, 10D, "mining:stone", NOW);
    double afterRecovery = price(rested, 10D, "mining:stone", NOW + recovery);
    assertThat(afterRecovery).isGreaterThan(uninterrupted).isLessThan(1D);
    assertThat(rested.getSkillStaleness().getLastAwardAt()).isEqualTo(NOW + recovery);
  }

  private XpReceipt awardFresh(double amount) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("mining");
    XpReceipt receipt = line.award(null, new XpAward("mining:stone", amount, null, false, XpAward.Kind.GAMEPLAY), amount, new XpEarnings());
    assertThat(line.getSkillStaleness().getPressure()).isPositive();
    return receipt;
  }

  private double price(PlayerSkillLine line, double amount, String source, long now) throws Exception {
    Method method = PlayerSkillLine.class.getDeclaredMethod("computeStalenessMultiplier", double.class, String.class, long.class);
    method.setAccessible(true);
    return (double) method.invoke(line, amount, source, now);
  }

  private static final class FixedClockSkillLine extends PlayerSkillLine {
    private FixedClockSkillLine(String name) {
      setLine(name);
    }

    @Override
    public double recoverStaleness(long now) {
      return super.recoverStaleness(NOW);
    }
  }
}
