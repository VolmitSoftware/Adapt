package art.arcane.adapt.content.item;

import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoundPositionStorageTest {
  @TempDir
  Path dataDirectory;

  private TestAdapt adapt;
  private FakeWorld world;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(dataDirectory).build();
    adapt = TestAdapt.start(platform, List.of());
    world = platform.world();
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void redstoneTorchLocationIsStoredRoundedToOneDecimalLikeEveryBoundLocation() {
    Loc target = new Loc(world, 1.26D, 64D, -3.44D, 90.7F, -10.2F);

    String json = Json.write(BoundRedstoneTorch.encode(new BoundRedstoneTorch.Data(target, BlockFace.EAST)));

    assertThat(json).isEqualTo("{\"location\":[\"minecraft:world\",1.3,64.0,-3.4,90,-10],\"face\":\"EAST\"}");
  }

  @Test
  void anEyeBoundToAWorldThatIsNotLoadedStaysBound() {
    ItemRef eye = FakeItem.of("ender_eye");
    eye.data().set(DataKey.of(BoundEyeOfEnder.ITEM.dataKey(), DataCodecs.STRING), "{\"location\":[\"minecraft:elsewhere\",10.0,64.0,10.0,0,0]}");

    assertThat(BoundEyeOfEnder.getLocation(eye)).isNull();
    assertThat(BoundEyeOfEnder.isBound(eye)).isTrue();

    ItemRef unbound = BoundEyeOfEnder.create(new BoundEyeOfEnder.Data(null));
    assertThat(BoundEyeOfEnder.isBound(unbound)).isFalse();
  }
}
