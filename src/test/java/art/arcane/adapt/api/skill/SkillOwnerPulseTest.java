package art.arcane.adapt.api.skill;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

  @Test
  void failedReplacementRetainsThePreviousRegistration() {
    int initialRegistrations = SkillOwnerPulse.registrationCount();
    SimpleSkill<?> previous = skill("failed-skill-pulse-replacement");
    SimpleSkill<?> failedReplacement = skill("failed-skill-pulse-replacement");
    SkillOwnerPulse.Registration previousRegistration = SkillOwnerPulse.register(
        previous,
        () -> 1_000L,
        (adaptPlayer, player, elapsed, cadence) -> {
        }
    );
    SkillOwnerPulse.RegistrationBatch batch = SkillOwnerPulse.beginRegistrationBatch();
    SkillOwnerPulse.Registration failedRegistration = null;

    try {
      failedRegistration = SkillOwnerPulse.register(
          failedReplacement,
          () -> 2_000L,
          (adaptPlayer, player, elapsed, cadence) -> {
          }
      );
      batch.endCapture();
      assertThat(SkillOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);

      batch.rollback();
      failedRegistration.unregister();
      assertThat(SkillOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);
    } finally {
      batch.rollback();
      if (failedRegistration != null) {
        failedRegistration.unregister();
      }
      previousRegistration.unregister();
    }

    assertThat(SkillOwnerPulse.registrationCount()).isEqualTo(initialRegistrations);
  }

  @Test
  void committedReplacementOwnsTheRegistrationAfterThePreviousSkillRetires() {
    int initialRegistrations = SkillOwnerPulse.registrationCount();
    SimpleSkill<?> previous = skill("committed-skill-pulse-replacement");
    SimpleSkill<?> replacement = skill("committed-skill-pulse-replacement");
    SkillOwnerPulse.Registration previousRegistration = SkillOwnerPulse.register(
        previous,
        () -> 1_000L,
        (adaptPlayer, player, elapsed, cadence) -> {
        }
    );
    SkillOwnerPulse.RegistrationBatch batch = SkillOwnerPulse.beginRegistrationBatch();
    SkillOwnerPulse.Registration replacementRegistration = null;

    try {
      replacementRegistration = SkillOwnerPulse.register(
          replacement,
          () -> 2_000L,
          (adaptPlayer, player, elapsed, cadence) -> {
          }
      );
      batch.endCapture();
      batch.commit();
      assertThat(SkillOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);

      previousRegistration.unregister();
      assertThat(SkillOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);
    } finally {
      batch.rollback();
      previousRegistration.unregister();
      if (replacementRegistration != null) {
        replacementRegistration.unregister();
      }
    }

    assertThat(SkillOwnerPulse.registrationCount()).isEqualTo(initialRegistrations);
  }

  private static SimpleSkill<?> skill(String name) {
    SimpleSkill<?> skill = mock(SimpleSkill.class);
    when(skill.getName()).thenReturn(name);
    return skill;
  }
}
