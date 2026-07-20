package art.arcane.adapt.content.item;

import art.arcane.adapt.util.common.io.BukkitGson;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundRedstoneTorchDataTest {
  @Test
  void clickedFaceRoundTripsThroughItemDataJson() {
    BoundRedstoneTorch.Data binding = new BoundRedstoneTorch.Data(null, BlockFace.EAST);

    String json = BukkitGson.gson.toJson(binding);
    BoundRedstoneTorch.Data decoded = BukkitGson.gson.fromJson(json, BoundRedstoneTorch.Data.class);

    assertThat(decoded.getFace()).isEqualTo(BlockFace.EAST);
  }

  @Test
  void dataWithoutAFaceRemainsUnbound() {
    BoundRedstoneTorch.Data decoded = BukkitGson.gson.fromJson(
        "{\"location\":null}", BoundRedstoneTorch.Data.class);

    assertThat(decoded.getFace()).isNull();
  }
}
