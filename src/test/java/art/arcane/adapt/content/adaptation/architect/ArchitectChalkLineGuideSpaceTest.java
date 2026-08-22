package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectChalkLineGuideSpaceTest {
  @Test
  void guidesDrawThroughReplaceableVegetation() {
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.SHORT_GRASS, true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.TALL_GRASS, true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.FERN, true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.SNOW, true))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.WATER, true))).isTrue();
  }

  @Test
  void guidesDrawInEmptySpaceWithoutConsultingTheReplaceableProbe() {
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.AIR, false))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.CAVE_AIR, false))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.VOID_AIR, false))).isTrue();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(null)).isTrue();
  }

  @Test
  void guidesStillYieldToBlocksAPlacementWouldNotReplace() {
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.STONE, false))).isFalse();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.OAK_FENCE, false))).isFalse();
    assertThat(ArchitectChalkLine.isGuideSpaceDrawable(block(Material.DANDELION, false))).isFalse();
  }

  private static Block block(Material type, boolean replaceable) {
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(type);
    when(block.isReplaceable()).thenReturn(replaceable);
    return block;
  }
}
