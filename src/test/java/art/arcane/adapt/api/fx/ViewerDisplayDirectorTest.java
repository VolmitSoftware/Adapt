package art.arcane.adapt.api.fx;

import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
}
