package art.arcane.adapt.api.adaptation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptationOwnerPulseTest {
  private static final List<Path> PARTICIPANT_SOURCES = List.of(
      source("kinetics/KineticsHeavyFrame.java"),
      source("kinetics/KineticsMoonJump.java"),
      source("kinetics/KineticsSurfaceSkate.java"),
      source("kinetics/KineticsRubberSoul.java"),
      source("agility/AgilityVault.java"),
      source("unarmed/UnarmedMeditation.java"),
      source("kinetics/KineticsPhalanxReach.java")
  );

  @Test
  void oneThousandOwnersNeverExceedThePerTickTaskBudget() {
    assertThat(AdaptationOwnerPulse.boundedBatchSize(0)).isZero();
    assertThat(AdaptationOwnerPulse.boundedBatchSize(12)).isEqualTo(12);
    assertThat(AdaptationOwnerPulse.boundedBatchSize(1_000))
        .isEqualTo(AdaptationOwnerPulse.MAX_OWNER_TASKS_PER_TICK);
    assertThat(AdaptationOwnerPulse.boundedExaminationSize(0)).isZero();
    assertThat(AdaptationOwnerPulse.boundedExaminationSize(12)).isEqualTo(12);
    assertThat(AdaptationOwnerPulse.boundedExaminationSize(1_000))
        .isEqualTo(AdaptationOwnerPulse.MAX_OWNER_EXAMINATIONS_PER_TICK);
  }

  @Test
  void nonDueOwnersHaveABoundedExaminationCost() {
    int playerCount = 1_000;
    int cursor = 0;
    Set<Integer> visited = new HashSet<>();

    for (int tick = 0; tick < 5; tick++) {
      int start = cursor;
      int examined = 0;
      int attemptedTasks = 0;
      int examinationBudget = AdaptationOwnerPulse.boundedExaminationSize(playerCount);
      while (examined < examinationBudget
          && attemptedTasks < AdaptationOwnerPulse.MAX_OWNER_TASKS_PER_TICK) {
        visited.add((start + examined) % playerCount);
        examined++;
      }
      assertThat(examined).isEqualTo(AdaptationOwnerPulse.MAX_OWNER_EXAMINATIONS_PER_TICK);
      assertThat(attemptedTasks).isZero();
      cursor = AdaptationOwnerPulse.advanceCursor(start, examined, playerCount);
    }

    assertThat(visited).hasSize(playerCount);
    assertThat(cursor).isZero();
  }

  @Test
  void roundRobinCursorVisitsAllOneThousandOwnersFairly() {
    int playerCount = 1_000;
    int cursor = 0;
    int coordinatorTicks = 0;
    Set<Integer> visited = new HashSet<>();

    while (visited.size() < playerCount) {
      int batchSize = AdaptationOwnerPulse.boundedBatchSize(playerCount);
      for (int offset = 0; offset < batchSize; offset++) {
        visited.add((cursor + offset) % playerCount);
      }
      cursor = AdaptationOwnerPulse.advanceCursor(cursor, batchSize, playerCount);
      coordinatorTicks++;
    }

    assertThat(visited).hasSize(playerCount);
    assertThat(coordinatorTicks).isEqualTo(16);
    assertThat(cursor).isEqualTo(24);
  }

  @Test
  void participantsRetainTheirIndependentCadences() {
    assertThat(AdaptationOwnerPulse.cadenceDue(null, 1_000L, 1_000L)).isTrue();
    assertThat(AdaptationOwnerPulse.cadenceDue(1_000L, 1_999L, 1_000L)).isFalse();
    assertThat(AdaptationOwnerPulse.cadenceDue(1_000L, 2_000L, 1_000L)).isTrue();
    assertThat(AdaptationOwnerPulse.cadenceDue(1_000L, 2_999L, 2_000L)).isFalse();
    assertThat(AdaptationOwnerPulse.cadenceDue(1_000L, 3_000L, 2_000L)).isTrue();
  }

  @Test
  void oneThousandOwnersSettleIntoTheRequestedCadenceWithinTheTaskBudget() {
    assertCadenceSchedule(1_000L, 5_000L, 5);
    assertCadenceSchedule(2_000L, 6_000L, 3);
  }

  @Test
  void replacementAndUnregisterRemoveExactlyTheirOwnRegistration() {
    int initialRegistrations = AdaptationOwnerPulse.registrationCount();
    SimpleAdaptation<?> first = adaptation("pulse-registration-contract");
    SimpleAdaptation<?> replacement = adaptation("pulse-registration-contract");

    AdaptationOwnerPulse.Registration firstRegistration = AdaptationOwnerPulse.register(
        first,
        () -> 1_000L,
        player -> {
        }
    );
    assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);

    AdaptationOwnerPulse.Registration replacementRegistration = AdaptationOwnerPulse.register(
        replacement,
        () -> 2_000L,
        player -> {
        }
    );
    assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);

    firstRegistration.unregister();
    assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);
    replacementRegistration.unregister();
    assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations);
  }

  @Test
  void failedReplacementRetainsThePreviousRegistration() {
    int initialRegistrations = AdaptationOwnerPulse.registrationCount();
    SimpleAdaptation<?> previous = adaptation("pulse-failed-replacement");
    SimpleAdaptation<?> failedReplacement = adaptation("pulse-failed-replacement");
    AdaptationOwnerPulse.Registration previousRegistration = AdaptationOwnerPulse.register(
        previous,
        () -> 1_000L,
        player -> {
        }
    );
    AdaptationOwnerPulse.RegistrationBatch batch = AdaptationOwnerPulse.beginRegistrationBatch();
    AdaptationOwnerPulse.Registration failedRegistration = null;

    try {
      failedRegistration = AdaptationOwnerPulse.register(
          failedReplacement,
          () -> 2_000L,
          player -> {
          }
      );
      batch.endCapture();
      assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);

      batch.rollback();
      failedRegistration.unregister();
      assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);
    } finally {
      batch.rollback();
      if (failedRegistration != null) {
        failedRegistration.unregister();
      }
      previousRegistration.unregister();
    }

    assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations);
  }

  @Test
  void committedReplacementTakesOverWhenThePreviousRegistrationRetires() {
    int initialRegistrations = AdaptationOwnerPulse.registrationCount();
    SimpleAdaptation<?> previous = adaptation("pulse-committed-replacement");
    SimpleAdaptation<?> replacement = adaptation("pulse-committed-replacement");
    AdaptationOwnerPulse.Registration previousRegistration = AdaptationOwnerPulse.register(
        previous,
        () -> 1_000L,
        player -> {
        }
    );
    AdaptationOwnerPulse.RegistrationBatch batch = AdaptationOwnerPulse.beginRegistrationBatch();
    AdaptationOwnerPulse.Registration replacementRegistration = null;

    try {
      replacementRegistration = AdaptationOwnerPulse.register(
          replacement,
          () -> 2_000L,
          player -> {
          }
      );
      batch.endCapture();
      batch.commit();
      assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);

      previousRegistration.unregister();
      assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations + 1);
    } finally {
      batch.rollback();
      previousRegistration.unregister();
      if (replacementRegistration != null) {
        replacementRegistration.unregister();
      }
    }

    assertThat(AdaptationOwnerPulse.registrationCount()).isEqualTo(initialRegistrations);
  }

  @Test
  void allSevenMaintenanceParticipantsUseAndReleaseTheSharedPulse() throws IOException {
    for (Path source : PARTICIPANT_SOURCES) {
      String java = Files.readString(source);
      assertThat(java).contains("AdaptationOwnerPulse.register(");
      assertThat(java).contains("ownerMaintenance.unregister();");
      assertThat(java).doesNotContain("public void onTick()");
    }
    assertThat(Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/api/skill/SkillRegistry.java"
    ))).contains("AdaptationOwnerPulse.startRuntime();");
  }

  private static void assertCadenceSchedule(long cadenceMillis, long durationMillis, int expectedMinimumPulses) {
    int playerCount = 1_000;
    int cursor = 0;
    long[] lastPulse = new long[playerCount];
    int[] pulseCount = new int[playerCount];
    long maximumGap = 0L;
    Arrays.fill(lastPulse, -1L);

    for (long now = 0L; now <= durationMillis; now += 50L) {
      int start = cursor;
      int examined = 0;
      int attemptedTasks = 0;
      int examinationBudget = AdaptationOwnerPulse.boundedExaminationSize(playerCount);
      while (examined < examinationBudget
          && attemptedTasks < AdaptationOwnerPulse.MAX_OWNER_TASKS_PER_TICK) {
        int playerIndex = (start + examined) % playerCount;
        long previousPulse = lastPulse[playerIndex];
        if (previousPulse < 0L
            || AdaptationOwnerPulse.cadenceDue(previousPulse, now, cadenceMillis)) {
          if (previousPulse >= 0L) {
            maximumGap = Math.max(maximumGap, now - previousPulse);
          }
          lastPulse[playerIndex] = now;
          pulseCount[playerIndex]++;
          attemptedTasks++;
        }
        examined++;
      }
      assertThat(attemptedTasks).isLessThanOrEqualTo(AdaptationOwnerPulse.MAX_OWNER_TASKS_PER_TICK);
      assertThat(examined).isLessThanOrEqualTo(AdaptationOwnerPulse.MAX_OWNER_EXAMINATIONS_PER_TICK);
      cursor = AdaptationOwnerPulse.advanceCursor(start, examined, playerCount);
    }

    for (int count : pulseCount) {
      assertThat(count).isGreaterThanOrEqualTo(expectedMinimumPulses);
    }
    assertThat(maximumGap).isEqualTo(cadenceMillis);
  }

  private static SimpleAdaptation<?> adaptation(String name) {
    SimpleAdaptation<?> adaptation = mock(SimpleAdaptation.class);
    when(adaptation.getName()).thenReturn(name);
    return adaptation;
  }

  private static Path source(String relativePath) {
    return Path.of("src/main/java/art/arcane/adapt/content/adaptation").resolve(relativePath);
  }
}
