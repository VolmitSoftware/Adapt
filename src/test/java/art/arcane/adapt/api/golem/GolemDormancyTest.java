package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class GolemDormancyTest {
  @Test
  void onlineOwnerWithLoadedDataWakesWhenThereIsRoom() {
    assertThat(GolemDormancy.reason(true, true, false, () -> true)).isEqualTo(DormancyReason.NONE);
    assertThat(GolemDormancy.reason(true, true, true, () -> true)).isEqualTo(DormancyReason.NONE);
  }

  @Test
  void serverCapKeepsAnOtherwiseAwakeGolemDormant() {
    assertThat(GolemDormancy.reason(true, true, false, () -> false)).isEqualTo(DormancyReason.SERVER_CAP);
  }

  @Test
  void onlineOwnerWithUnknownDataStaysDormant() {
    assertThat(GolemDormancy.reason(true, false, false, () -> true)).isEqualTo(DormancyReason.OWNER_UNKNOWN);
    assertThat(GolemDormancy.reason(true, false, true, () -> true)).isEqualTo(DormancyReason.OWNER_UNKNOWN);
  }

  @Test
  void offlineOwnerSleepsUnlessAnOfflineGuardHasRoom() {
    assertThat(GolemDormancy.reason(false, false, false, () -> true)).isEqualTo(DormancyReason.OWNER_OFFLINE);
    assertThat(GolemDormancy.reason(false, true, false, () -> true)).isEqualTo(DormancyReason.OWNER_OFFLINE);
    assertThat(GolemDormancy.reason(false, false, true, () -> true)).isEqualTo(DormancyReason.NONE);
    assertThat(GolemDormancy.reason(false, false, true, () -> false)).isEqualTo(DormancyReason.SERVER_CAP);
  }

  @Test
  void roomIsClaimedOnlyWhenTheGolemWouldOtherwiseWake() {
    AtomicInteger claims = new AtomicInteger();
    BooleanSupplier claim = () -> {
      claims.incrementAndGet();
      return true;
    };

    GolemDormancy.reason(false, false, false, claim);
    GolemDormancy.reason(true, false, false, claim);
    assertThat(claims).hasValue(0);

    GolemDormancy.reason(true, true, false, claim);
    GolemDormancy.reason(false, false, true, claim);
    assertThat(claims).hasValue(2);
  }

  @Test
  void transitionsOnlyActOnChanges() {
    assertThat(GolemDormancy.transition(true, DormancyReason.NONE)).isEqualTo(GolemDormancy.Transition.NONE);
    assertThat(GolemDormancy.transition(false, DormancyReason.NONE)).isEqualTo(GolemDormancy.Transition.WAKE);
    for (DormancyReason reason : DormancyReason.values()) {
      if (reason == DormancyReason.NONE) {
        continue;
      }
      assertThat(GolemDormancy.transition(true, reason)).isEqualTo(GolemDormancy.Transition.SLEEP);
      assertThat(GolemDormancy.transition(false, reason)).isEqualTo(GolemDormancy.Transition.NONE);
    }
  }

  @Test
  void offlineGuardRequiresTheFlagAndAGuardianCore() {
    assertThat(GolemDormancy.offlineGuard(true, GolemCoreType.GUARDIAN)).isTrue();
    assertThat(GolemDormancy.offlineGuard(false, GolemCoreType.GUARDIAN)).isFalse();
    assertThat(GolemDormancy.offlineGuard(true, GolemCoreType.BLANK)).isFalse();
    assertThat(GolemDormancy.offlineGuard(true, null)).isFalse();
  }
}
