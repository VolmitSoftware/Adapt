package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemWorkXp;
import art.arcane.adapt.content.skill.golemancy.RollingXpCap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SkillGolemancyWorkXpTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
  private static final long HOUR = RollingXpCap.WINDOW_MILLIS;

  private final SkillGolemancy.Config config = new SkillGolemancy.Config();
  private final RollingXpCap cap = new RollingXpCap();

  @Test
  void workXpStopsAtTheHourlyCapPerOwner() {
    long now = 1_000L;
    double paid = 0D;
    for (int i = 0; i < 3_000; i++) {
      paid += SkillGolemancy.grantWorkXp(cap, OWNER, 0.5D, 25D, config, now);
    }

    assertThat(paid).isEqualTo(1_200D);
    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 6D, 25D, config, now)).isZero();
    assertThat(SkillGolemancy.grantWorkXp(cap, OTHER, 6D, 25D, config, now)).isEqualTo(6D);
  }

  @Test
  void theCapRollsOverAfterAnHour() {
    SkillGolemancy.grantWorkXp(cap, OWNER, 1_200D, 0D, config, 0L);

    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 4D, 0D, config, HOUR - 1L)).isZero();
    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 4D, 0D, config, HOUR)).isEqualTo(4D);
  }

  @Test
  void aGrantThatCrossesTheCapPaysOnlyTheRemainder() {
    SkillGolemancy.grantWorkXp(cap, OWNER, 1_198D, 0D, config, 0L);

    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 6D, 0D, config, 1L)).isCloseTo(2D, offset(1e-9));
  }

  @Test
  void ownersBeyondTheWorkRadiusEarnNothing() {
    double radius = config.workXpOwnerRadius;

    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 4D, radius * radius, config, 0L)).isEqualTo(4D);
    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 4D, radius * radius + 1D, config, 0L)).isZero();
    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 4D, Double.POSITIVE_INFINITY, config, 0L)).isZero();
  }

  @Test
  void anOwnerOutOfRangeDoesNotUseUpTheCap() {
    SkillGolemancy.grantWorkXp(cap, OWNER, 1_000D, Double.POSITIVE_INFINITY, config, 0L);

    assertThat(SkillGolemancy.grantWorkXp(cap, OWNER, 1_200D, 0D, config, 1L)).isEqualTo(1_200D);
  }

  @Test
  void workRewardKeysNameTheCore() {
    assertThat(GolemWorkXp.rewardKey(GolemCoreType.HARVESTER)).isEqualTo("golemancy:work:harvester");
    assertThat(GolemWorkXp.rewardKey(GolemCoreType.SORTER)).isEqualTo("golemancy:work:sorter");
  }
}
