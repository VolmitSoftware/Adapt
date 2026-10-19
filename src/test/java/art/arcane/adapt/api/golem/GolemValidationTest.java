package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import static art.arcane.adapt.api.golem.GolemValidation.Verdict.KEEP;
import static art.arcane.adapt.api.golem.GolemValidation.Verdict.RESTORE;
import static art.arcane.adapt.api.golem.GolemValidation.Verdict.REVERT;
import static art.arcane.adapt.api.golem.GolemValidation.Verdict.WAIT;
import static org.assertj.core.api.Assertions.assertThat;

class GolemValidationTest {
  @Test
  void activeEntriesAreKept() {
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.ACTIVE, false)).isEqualTo(KEEP);
  }

  @Test
  void missingEntriesAreRestoredWhenTheGolemTurnsUp() {
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.MISSING, false)).isEqualTo(RESTORE);
  }

  @Test
  void idsNotActiveInTheRosterRevert() {
    assertThat(GolemValidation.verdict(false, true, null, false)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.PACKED, false)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.SHATTERED, false)).isEqualTo(REVERT);
  }

  @Test
  void unknownOwnerDataWaitsWhateverTheRosterWouldSay() {
    for (GolemRoster.Status status : GolemRoster.Status.values()) {
      assertThat(GolemValidation.verdict(false, false, status, false)).isEqualTo(WAIT);
      assertThat(GolemValidation.verdict(false, false, status, true)).isEqualTo(WAIT);
    }
    assertThat(GolemValidation.verdict(false, false, null, false)).isEqualTo(WAIT);
  }

  @Test
  void aSecondLoadedCopyOfTheSameGolemAlwaysReverts() {
    assertThat(GolemValidation.verdict(true, true, GolemRoster.Status.ACTIVE, false)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(true, false, GolemRoster.Status.ACTIVE, false)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(true, true, null, false)).isEqualTo(REVERT);
  }

  @Test
  void aCopyOlderThanTheRostersLastSightingRevertsWhateverItsEntrySays() {
    for (GolemRoster.Status status : GolemRoster.Status.values()) {
      assertThat(GolemValidation.verdict(false, true, status, true)).isEqualTo(REVERT);
    }
  }
}
