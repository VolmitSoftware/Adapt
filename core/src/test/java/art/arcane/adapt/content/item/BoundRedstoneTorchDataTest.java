package art.arcane.adapt.content.item;

import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.platform.value.BlockFace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundRedstoneTorchDataTest {
  @Test
  void clickedFaceRoundTripsThroughItemDataJson() {
    BoundRedstoneTorch.Data binding = new BoundRedstoneTorch.Data(null, BlockFace.EAST);

    String json = DataBoundItem.GSON.toJson(binding);
    BoundRedstoneTorch.Data decoded = DataBoundItem.GSON.fromJson(json, BoundRedstoneTorch.Data.class);

    assertThat(decoded.getFace()).isEqualTo(BlockFace.EAST);
  }

  @Test
  void dataWithoutAFaceRemainsUnbound() {
    BoundRedstoneTorch.Data decoded = DataBoundItem.GSON.fromJson(
        "{\"location\":null}", BoundRedstoneTorch.Data.class);

    assertThat(decoded.getFace()).isNull();
  }
}
