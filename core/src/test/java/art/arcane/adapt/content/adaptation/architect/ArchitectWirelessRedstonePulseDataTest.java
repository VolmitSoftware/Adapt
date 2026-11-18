package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeBlockState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectWirelessRedstonePulseDataTest {
  @Test
  void aBoundLeverIsDrivenThroughItsOwnPoweredState() {
    BlockStateRef lever = state("lever", Map.of("powered", "false", "face", "wall", "facing", "north"));

    BlockStateRef powered = ArchitectWirelessRedstone.poweredCopy(lever, true);

    assertThat(powered).isNotNull();
    assertThat(powered.property("powered")).isEqualTo("true");
    assertThat(ArchitectWirelessRedstone.poweredCopy(lever, false).property("powered"))
        .isEqualTo("false");
  }

  @Test
  void aBoundRedstoneLampIsLitWithoutTouchingOtherLightableBlocks() {
    BlockStateRef lamp = state("redstone_lamp", Map.of("lit", "false"));
    BlockStateRef furnace = state("furnace", Map.of("lit", "false", "facing", "north"));

    BlockStateRef powered = ArchitectWirelessRedstone.poweredCopy(lamp, true);

    assertThat(powered).isNotNull();
    assertThat(powered.property("lit")).isEqualTo("true");
    assertThat(ArchitectWirelessRedstone.isDriveable(furnace)).isFalse();
    assertThat(ArchitectWirelessRedstone.poweredCopy(furnace, true)).isNull();
  }

  @Test
  void analogueComponentsAreDrivenToFullPower() {
    BlockStateRef wire = state("redstone_wire", Map.of("power", "0", "north", "none"));

    BlockStateRef powered = ArchitectWirelessRedstone.poweredCopy(wire, true);

    assertThat(powered.property("power")).isEqualTo("15");
    assertThat(ArchitectWirelessRedstone.poweredCopy(wire, false).property("power"))
        .isEqualTo("0");
    assertThat(ArchitectWirelessRedstone.isDriveable(state("daylight_detector", Map.of("power", "0", "inverted", "false"))))
        .isTrue();
  }

  @Test
  void doorsOpenWithoutFlippingThePoweredStateVanillaWouldReset() {
    BlockStateRef door = state("oak_door", Map.of("open", "false", "powered", "false", "half", "lower",
        "hinge", "left", "facing", "north"));

    BlockStateRef powered = ArchitectWirelessRedstone.poweredCopy(door, true);

    assertThat(powered.property("open")).isEqualTo("true");
    assertThat(powered.property("powered")).isEqualTo("false");
    assertThat(ArchitectWirelessRedstone.poweredCopy(door, false).property("open")).isEqualTo("false");
  }

  @Test
  void blocksWithoutARedstoneStateAreNeverDriven() {
    BlockStateRef piston = state("piston", Map.of("extended", "false", "facing", "north"));
    BlockStateRef stone = state("stone", Map.of());

    assertThat(ArchitectWirelessRedstone.isDriveable(piston)).isFalse();
    assertThat(ArchitectWirelessRedstone.isDriveable(stone)).isFalse();
    assertThat(ArchitectWirelessRedstone.isDriveable(null)).isFalse();
    assertThat(ArchitectWirelessRedstone.poweredCopy(stone, true)).isNull();
    assertThat(ArchitectWirelessRedstone.poweredCopy(null, true)).isNull();
  }

  @Test
  void onlyRedstoneFixturesCountAsLightableTargets() {
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Id.minecraft("redstone_lamp"))).isTrue();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Id.minecraft("redstone_torch"))).isTrue();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Id.minecraft("redstone_wall_torch"))).isTrue();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Id.minecraft("campfire"))).isFalse();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Id.minecraft("redstone_ore"))).isFalse();
  }

  private static BlockStateRef state(String type, Map<String, String> properties) {
    return new FakeBlockState(Id.minecraft(type), properties);
  }
}
