package art.arcane.adapt.content.adaptation.tragoul;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TragoulAreaWorkBudgetTest {
  @Test
  void globeCapsActivationsAndAffectedTargetsPerWindow() {
    TragoulGlobe.GlobeWorkBudget budget = new TragoulGlobe.GlobeWorkBudget(2, 3, 50L);

    assertThat(budget.tryActivation(0L)).isTrue();
    assertThat(budget.tryActivation(0L)).isTrue();
    assertThat(budget.tryActivation(0L)).isFalse();
    assertThat(budget.reserveTargets(2, 0L)).isEqualTo(2);
    assertThat(budget.reserveTargets(2, 0L)).isEqualTo(1);
    assertThat(budget.reserveTargets(1, 0L)).isZero();
    assertThat(budget.tryActivation(50L)).isTrue();
    assertThat(budget.reserveTargets(3, 50L)).isEqualTo(3);
  }

  @Test
  void lanceCapsSearchesAndRejectsConcurrentOwnerChains() {
    TragoulLance.LanceWorkBudget budget = new TragoulLance.LanceWorkBudget(2, 50L);
    TragoulLance.LanceAdmission admission = new TragoulLance.LanceAdmission(2);
    UUID firstOwner = new UUID(1L, 1L);
    UUID secondOwner = new UUID(1L, 2L);
    UUID thirdOwner = new UUID(1L, 3L);

    assertThat(budget.trySearch(0L)).isTrue();
    assertThat(budget.trySearch(0L)).isTrue();
    assertThat(budget.trySearch(0L)).isFalse();
    assertThat(budget.trySearch(50L)).isTrue();

    long firstToken = admission.admit(firstOwner, 0L, 10L);
    assertThat(firstToken).isPositive();
    assertThat(admission.admit(firstOwner, 1L, 10L)).isEqualTo(-1L);
    assertThat(admission.admit(secondOwner, 1L, 100L)).isPositive();
    assertThat(admission.admit(thirdOwner, 1L, 100L)).isEqualTo(-1L);
    long replacement = admission.admit(firstOwner, 10L, 100L);
    assertThat(replacement).isGreaterThan(firstToken);
    assertThat(admission.complete(firstOwner, firstToken)).isFalse();
    assertThat(admission.complete(firstOwner, replacement)).isTrue();
  }

  @Test
  void plagueBudgetsMarksSpreadsAndCoalescesMonsterWork() {
    TragoulPlagueBearer.PlagueWorkBudget budget = new TragoulPlagueBearer.PlagueWorkBudget(2, 1, 50L);
    TragoulPlagueBearer.PendingEntityGate gate = new TragoulPlagueBearer.PendingEntityGate(2);
    UUID firstMonster = new UUID(2L, 1L);
    UUID secondMonster = new UUID(2L, 2L);
    UUID thirdMonster = new UUID(2L, 3L);

    assertThat(budget.tryMark(0L)).isTrue();
    assertThat(budget.tryMark(0L)).isTrue();
    assertThat(budget.tryMark(0L)).isFalse();
    assertThat(budget.trySpread(0L)).isTrue();
    assertThat(budget.trySpread(0L)).isFalse();
    assertThat(budget.tryMark(50L)).isTrue();
    assertThat(budget.trySpread(50L)).isTrue();

    assertThat(gate.admit(firstMonster)).isTrue();
    assertThat(gate.admit(firstMonster)).isFalse();
    assertThat(gate.admit(secondMonster)).isTrue();
    assertThat(gate.admit(thirdMonster)).isFalse();
    gate.complete(firstMonster);
    assertThat(gate.admit(thirdMonster)).isTrue();
    assertThat(gate.size()).isEqualTo(2);
  }
}
