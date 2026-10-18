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
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.ACTIVE)).isEqualTo(KEEP);
  }

  @Test
  void missingEntriesAreRestoredWhenTheGolemTurnsUp() {
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.MISSING)).isEqualTo(RESTORE);
  }

  @Test
  void idsNotActiveInTheRosterRevert() {
    assertThat(GolemValidation.verdict(false, true, null)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.PACKED)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(false, true, GolemRoster.Status.SHATTERED)).isEqualTo(REVERT);
  }

  @Test
  void unknownOwnerDataWaitsWhateverTheRosterWouldSay() {
    for (GolemRoster.Status status : GolemRoster.Status.values()) {
      assertThat(GolemValidation.verdict(false, false, status)).isEqualTo(WAIT);
    }
    assertThat(GolemValidation.verdict(false, false, null)).isEqualTo(WAIT);
  }

  @Test
  void aSecondLoadedCopyOfTheSameGolemAlwaysReverts() {
    assertThat(GolemValidation.verdict(true, true, GolemRoster.Status.ACTIVE)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(true, false, GolemRoster.Status.ACTIVE)).isEqualTo(REVERT);
    assertThat(GolemValidation.verdict(true, true, null)).isEqualTo(REVERT);
  }
}
