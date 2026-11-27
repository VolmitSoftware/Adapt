package art.arcane.adapt.content.item;

import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.value.BlockFace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundRedstoneTorchDataTest {
  @Test
  void clickedFaceRoundTripsThroughItemDataJson() {
    BoundRedstoneTorch.Data binding = new BoundRedstoneTorch.Data(null, BlockFace.EAST);

    String json = ((DataValue.StringValue) BoundRedstoneTorch.ITEM.codec().encode(binding)).value();
    BoundRedstoneTorch.Data decoded = BoundRedstoneTorch.ITEM.codec().decode(new DataValue.StringValue(json));

    assertThat(decoded.getFace()).isEqualTo(BlockFace.EAST);
  }

  @Test
  void dataWithoutAFaceRemainsUnbound() {
    BoundRedstoneTorch.Data decoded = BoundRedstoneTorch.ITEM.codec().decode(new DataValue.StringValue("{\"location\":null}"));

    assertThat(decoded.getFace()).isNull();
  }
}
