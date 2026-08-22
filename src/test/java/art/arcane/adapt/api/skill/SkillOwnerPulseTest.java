package art.arcane.adapt.api.skill;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillOwnerPulseTest {
  @Test
  void ownerTaskBatchHasAHardLimit() {
    assertThat(SkillOwnerPulse.boundedBatchSize(0)).isZero();
    assertThat(SkillOwnerPulse.boundedBatchSize(12)).isEqualTo(12);
    assertThat(SkillOwnerPulse.boundedBatchSize(1_000)).isEqualTo(SkillOwnerPulse.MAX_OWNER_TASKS_PER_TICK);
  }

  @Test
  void roundRobinCursorVisitsEveryPlayerWithoutStarvation() {
    int playerCount = 1_000;
    int cursor = 0;
    int invocations = 0;
    Set<Integer> visited = new HashSet<>();

    while (visited.size() < playerCount) {
      int batchSize = SkillOwnerPulse.boundedBatchSize(playerCount);
      for (int offset = 0; offset < batchSize; offset++) {
        visited.add((cursor + offset) % playerCount);
      }
      cursor = SkillOwnerPulse.advanceCursor(cursor, batchSize, playerCount);
      invocations++;
    }

    assertThat(visited).hasSize(playerCount);
    assertThat(invocations).isEqualTo(16);
    assertThat(cursor).isEqualTo(24);
  }

  @Test
  void elapsedRewardsCatchUpWithoutUnboundedBursts() {
    assertThat(SkillOwnerPulse.boundedElapsed(1_412L, 1_412L)).isEqualTo(1_412L);
    assertThat(SkillOwnerPulse.boundedElapsed(2_824L, 1_412L)).isEqualTo(2_824L);
    assertThat(SkillOwnerPulse.boundedElapsed(60_000L, 1_412L)).isEqualTo(5_648L);
    assertThat(SkillOwnerPulse.boundedElapsed(-1L, 1_412L)).isZero();
  }

  @Test
  void cadenceWaitsForTheConfiguredInterval() {
    assertThat(SkillOwnerPulse.cadenceDue(null, 1_000L, 975L)).isTrue();
    assertThat(SkillOwnerPulse.cadenceDue(1_000L, 1_974L, 975L)).isFalse();
    assertThat(SkillOwnerPulse.cadenceDue(1_000L, 1_975L, 975L)).isTrue();
    assertThat(SkillOwnerPulse.cadenceDue(1_000L, 900L, 975L)).isFalse();
  }
}
