package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectChalkLineGuideSpaceTest {
  @Test
  void guidesDrawThroughReplaceableVegetation() {
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("short_grass", true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("tall_grass", true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("fern", true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("snow", true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("water", true))).isTrue();
  }

  @Test
  void guidesDrawInEmptySpaceWithoutConsultingTheReplaceableProbe() {
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("air", false))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("cave_air", false))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("void_air", false))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(null)).isTrue();
  }

  @Test
  void guidesStillYieldToBlocksAPlacementWouldNotReplace() {
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("stone", false))).isFalse();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("oak_fence", false))).isFalse();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block("dandelion", false))).isFalse();
  }

  private static BlockRef block(String type, boolean replaceable) {
    BlockRef block = mock(BlockRef.class);
    when(block.type()).thenReturn(Id.minecraft(type));
    when(block.isReplaceable()).thenReturn(replaceable);
    return block;
  }
}
