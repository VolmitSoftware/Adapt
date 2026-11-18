package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.value.BlockFace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BukkitBlockFaceTest {
  @Test
  void everyBukkitFaceHasACoreTwinWithTheSameOffsetAndOpposite() {
    for (org.bukkit.block.BlockFace bukkit : org.bukkit.block.BlockFace.values()) {
      BlockFace core = BukkitConversions.face(bukkit);

      assertThat(core.name()).isEqualTo(bukkit.name());
      assertThat(new int[]{core.dx(), core.dy(), core.dz()}).describedAs(bukkit.name())
          .containsExactly(bukkit.getModX(), bukkit.getModY(), bukkit.getModZ());
      assertThat(core.opposite().name()).describedAs(bukkit.name()).isEqualTo(bukkit.getOppositeFace().name());
      assertThat(BukkitConversions.face(core)).isEqualTo(bukkit);
    }
    assertThat(BlockFace.values()).hasSameSizeAs(org.bukkit.block.BlockFace.values());
  }
}
