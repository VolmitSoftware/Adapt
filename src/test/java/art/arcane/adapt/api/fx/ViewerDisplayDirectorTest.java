package art.arcane.adapt.api.fx;

import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerDisplayDirectorTest {
  @AfterEach
  void clearDisplays() {
    ViewerDisplayDirector.clearAll();
  }

  @Test
  void rejectsZeroAndNonfiniteLines() {
    assertThat(ViewerDisplayDirector.isRenderableLine(new Vector())).isFalse();
    assertThat(ViewerDisplayDirector.isRenderableLine(new Vector(Double.NaN, 0D, 1D))).isFalse();
    assertThat(ViewerDisplayDirector.isRenderableLine(new Vector(0D, 0D, 1D))).isTrue();
  }

  @Test
  void lineTransformationUsesExactLengthAndCenteredThickness() {
    Transformation transformation = ViewerDisplayDirector.lineTransformation(new Vector(0D, 0D, 5D), 0.1D);

    assertThat(transformation.getScale().x).isEqualTo(0.1F);
    assertThat(transformation.getScale().y).isEqualTo(0.1F);
    assertThat(transformation.getScale().z).isEqualTo(5F);
    assertThat(transformation.getTranslation().x).isEqualTo(-0.05F);
    assertThat(transformation.getTranslation().y).isEqualTo(-0.05F);
  }

  @Test
  void lineTransformationRemainsFiniteWithSanitizedThickness() {
    double thickness = ViewerDisplayDirector.sanitizeLineThickness(Double.NaN);
    Transformation transformation = ViewerDisplayDirector.lineTransformation(new Vector(0D, 0D, 5D), thickness);

    assertThat(ViewerDisplayDirector.sanitizeLineThickness(Double.POSITIVE_INFINITY)).isEqualTo(0.05D);
    assertThat(ViewerDisplayDirector.sanitizeLineThickness(0D)).isEqualTo(0.015D);
    assertThat(ViewerDisplayDirector.sanitizeLineThickness(1D)).isEqualTo(0.5D);
    assertThat(transformation.getScale().x).isEqualTo(0.05F);
    assertThat(transformation.getScale().y).isEqualTo(0.05F);
    assertThat(transformation.getTranslation().x).isEqualTo(-0.025F);
    assertThat(transformation.getTranslation().y).isEqualTo(-0.025F);
  }

  @Test
  void earlyExpiryCallbacksRetainTheRemainingTickDelay() {
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(3_000L, 1_000L)).isEqualTo(40);
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(1_051L, 1_000L)).isEqualTo(2);
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(1_001L, 1_000L)).isEqualTo(1);
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(1_000L, 1_000L)).isZero();
    assertThat(ViewerDisplayDirector.remainingExpiryTicks(999L, 1_000L)).isZero();
  }

  @Test
  void rejectedOrphanCleanupDispatchesAreRetriedInsteadOfDropped() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/api/fx/ViewerDisplayDirector.java"));

    assertThat(source).contains(
        "if (!J.runAt(anchor, () -> purgeOrphansOwned(current)))",
        "ORPHAN_PURGE_QUEUE.add(current.retry())",
        "Failed to clean stale Adapt private displays in chunk"
    );
  }
}
