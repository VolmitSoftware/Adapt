package art.arcane.adapt.content.skill.golemancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemXpCooldownsTest {
  @Test
  void claimOpensAgainOnlyAfterTheCooldown() {
    GolemXpCooldowns<String> cooldowns = new GolemXpCooldowns<>();

    assertThat(cooldowns.tryClaim("alice", 1_000L, 20_000L)).isTrue();
    assertThat(cooldowns.tryClaim("alice", 1_001L, 20_000L)).isFalse();
    assertThat(cooldowns.tryClaim("alice", 20_999L, 20_000L)).isFalse();
    assertThat(cooldowns.tryClaim("alice", 21_000L, 20_000L)).isTrue();
    assertThat(cooldowns.tryClaim("alice", 21_000L, 20_000L)).isFalse();
  }

  @Test
  void keysCoolDownIndependently() {
    GolemXpCooldowns<String> cooldowns = new GolemXpCooldowns<>();

    assertThat(cooldowns.tryClaim("alice:snow", 1_000L, 20_000L)).isTrue();
    assertThat(cooldowns.tryClaim("alice:iron", 1_000L, 20_000L)).isTrue();
    assertThat(cooldowns.tryClaim("bob:snow", 1_000L, 20_000L)).isTrue();
    assertThat(cooldowns.tryClaim("alice:snow", 2_000L, 20_000L)).isFalse();
  }

  @Test
  void zeroOrNegativeCooldownAlwaysClaims() {
    GolemXpCooldowns<String> cooldowns = new GolemXpCooldowns<>();

    assertThat(cooldowns.tryClaim("alice", 1_000L, 0L)).isTrue();
    assertThat(cooldowns.tryClaim("alice", 1_000L, 0L)).isTrue();
    assertThat(cooldowns.tryClaim("alice", 1_000L, -50L)).isTrue();
  }

  @Test
  void clockStepsBackwardsDoNotReopenTheCooldown() {
    GolemXpCooldowns<String> cooldowns = new GolemXpCooldowns<>();
    cooldowns.tryClaim("alice", 10_000L, 6_000L);

    assertThat(cooldowns.tryClaim("alice", 9_000L, 6_000L)).isFalse();
  }

  @Test
  void sweepForgetsClaimsOlderThanTheGivenCooldown() {
    GolemXpCooldowns<String> cooldowns = new GolemXpCooldowns<>();
    cooldowns.tryClaim("alice", 0L, 1_000L);
    cooldowns.tryClaim("bob", 400L, 1_000L);

    cooldowns.sweep(600L, 500L);

    assertThat(cooldowns.tryClaim("alice", 700L, 1_000L)).isTrue();
    assertThat(cooldowns.tryClaim("bob", 700L, 1_000L)).isFalse();
  }
}
