package art.arcane.adapt.util.common.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FairReadBudgetTest {
  @Test
  void capsSingleJobWithoutSpendingTheGlobalBurst() {
    FairReadBudget.Allocation allocation = FairReadBudget.allocate(4096, 512, 1);

    assertThat(allocation.totalReads()).isEqualTo(512);
    assertThat(allocation.grant(0, 0)).isEqualTo(512);
  }

  @Test
  void splitsGlobalBudgetEvenlyAcrossEligibleJobs() {
    FairReadBudget.Allocation allocation = FairReadBudget.allocate(4096, 512, 10);
    int total = 0;
    int minimum = Integer.MAX_VALUE;
    int maximum = Integer.MIN_VALUE;
    for (int i = 0; i < 10; i++) {
      int grant = allocation.grant(i, 0);
      total += grant;
      minimum = Math.min(minimum, grant);
      maximum = Math.max(maximum, grant);
    }

    assertThat(total).isEqualTo(4096);
    assertThat(maximum - minimum).isLessThanOrEqualTo(1);
    assertThat(maximum).isLessThanOrEqualTo(512);
  }

  @Test
  void rotationSharesScarceReadsAcrossTicks() {
    FairReadBudget.Allocation allocation = FairReadBudget.allocate(3, 512, 5);

    assertThat(grants(allocation, 0)).containsExactly(1, 1, 1, 0, 0);
    assertThat(grants(allocation, 1)).containsExactly(0, 1, 1, 1, 0);
    assertThat(grants(allocation, 4)).containsExactly(1, 1, 0, 0, 1);
  }

  @Test
  void rejectsNegativeLimits() {
    assertThatThrownBy(() -> FairReadBudget.allocate(-1, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FairReadBudget.allocate(1, -1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FairReadBudget.allocate(1, 1, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private int[] grants(FairReadBudget.Allocation allocation, int offset) {
    int[] grants = new int[allocation.jobCount()];
    for (int i = 0; i < grants.length; i++) {
      grants[i] = allocation.grant(i, offset);
    }
    return grants;
  }
}
