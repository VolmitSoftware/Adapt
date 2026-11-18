package art.arcane.adapt.content.adaptation.architect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectPlacementBudgetTest {
  @Test
  void ownerBatchAndPreviewSizeHaveHardCeilings() {
    assertThat(ArchitectPlacement.previewBatchEnd(0, 1_000)).isEqualTo(64);
    assertThat(ArchitectPlacement.previewBatchEnd(960, 1_000)).isEqualTo(1_000);
    assertThat(ArchitectPlacement.clampPreviewBlocks(100)).isEqualTo(9);
    assertThat(ArchitectPlacement.clampDisplayViewRange(100D)).isEqualTo(2D);
  }

  @Test
  void displayWorkBudgetResetsOnlyOnTheNextServerTick() {
    ArchitectPlacement.PreviewWorkBudget budget = new ArchitectPlacement.PreviewWorkBudget(3);

    assertThat(budget.tryReserve(1_000L)).isTrue();
    assertThat(budget.tryReserve(1_000L)).isTrue();
    assertThat(budget.tryReserve(1_000L)).isTrue();
    assertThat(budget.tryReserve(1_000L)).isFalse();
    assertThat(budget.tryReserve(1_050L)).isTrue();
  }

  @Test
  void activeDisplayCapacityCannotBeOversubscribed() {
    ArchitectPlacement.PreviewCapacity capacity = new ArchitectPlacement.PreviewCapacity(2);

    assertThat(capacity.tryAcquire()).isTrue();
    assertThat(capacity.tryAcquire()).isTrue();
    assertThat(capacity.tryAcquire()).isFalse();
    capacity.release();
    assertThat(capacity.tryAcquire()).isTrue();
    assertThat(capacity.active()).isEqualTo(2);
  }
}
