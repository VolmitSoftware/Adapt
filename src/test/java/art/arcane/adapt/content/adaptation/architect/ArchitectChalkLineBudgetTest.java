package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectChalkLineBudgetTest {
  @Test
  void heldWandScanHasAHardPlayerCeiling() {
    assertThat(ArchitectChalkLine.scanBatchEnd(0, 1_000)).isEqualTo(32);
    assertThat(ArchitectChalkLine.scanBatchEnd(992, 1_000)).isEqualTo(1_000);
  }

  @Test
  void previewTickerParksWithoutVisibleGuides() {
    assertThat(ArchitectChalkLine.previewRuntimeInterval(false, 250L)).isEqualTo(Long.MAX_VALUE);
    assertThat(ArchitectChalkLine.previewRuntimeInterval(true, 250L)).isEqualTo(250L);
    assertThat(ArchitectChalkLine.previewRuntimeInterval(true, 1L)).isEqualTo(50L);
  }

  @Test
  void guideConfigurationIsClampedToSafeBounds() {
    assertThat(ArchitectChalkLine.clampGuideBlocks(1_000)).isEqualTo(128);
    assertThat(ArchitectChalkLine.clampGuideBlocks(-1)).isEqualTo(16);
    assertThat(ArchitectChalkLine.clampSelectionDistance(1_000D)).isEqualTo(128D);
    assertThat(ArchitectChalkLine.clampPolylineVertices(1_000)).isEqualTo(32);
    assertThat(ArchitectChalkLine.clampCircleRadius(-1D)).isEqualTo(1D);
    assertThat(ArchitectChalkLine.clampArcRadius(1_000D)).isEqualTo(128D);
    assertThat(ArchitectChalkLine.renderRangeSquared(1_000D)).isEqualTo(96D * 96D);
    assertThat(ArchitectChalkLine.clampSelectionDistance(Double.NaN)).isEqualTo(96D);
    assertThat(ArchitectChalkLine.clampCircleRadius(Double.NaN)).isEqualTo(15D);
    assertThat(ArchitectChalkLine.clampArcRadius(Double.NaN)).isEqualTo(64D);
    assertThat(ArchitectChalkLine.renderRangeSquared(Double.NaN)).isEqualTo(64D * 64D);
  }

  @Test
  void eachLevelUnlocksExactlyOneAdditionalTool() {
    assertThat(Arrays.stream(Tool.values()).map(Tool::requiredLevel))
        .containsExactly(1, 2, 3, 4);
  }

  @Test
  void completeGuideMustRemainInsideBuildHeight() {
    assertThat(ArchitectChalkLine.isWithinBuildHeight(
        List.of(new Point(0, -64, 0), new Point(0, 319, 0)), -64, 320))
        .isTrue();
    assertThat(ArchitectChalkLine.isWithinBuildHeight(
        List.of(new Point(0, -65, 0)), -64, 320))
        .isFalse();
    assertThat(ArchitectChalkLine.isWithinBuildHeight(
        List.of(new Point(0, 320, 0)), -64, 320))
        .isFalse();
  }
}
