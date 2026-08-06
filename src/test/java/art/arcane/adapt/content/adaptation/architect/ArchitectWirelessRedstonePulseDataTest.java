package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.DaylightDetector;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Switch;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectWirelessRedstonePulseDataTest {
  @Test
  void aBoundLeverIsDrivenThroughItsOwnPoweredState() {
    Switch lever = mock(Switch.class);
    Switch copy = powerable(mock(Switch.class));
    when(lever.getMaterial()).thenReturn(Material.LEVER);
    when(lever.clone()).thenReturn(copy);

    BlockData powered = ArchitectWirelessRedstone.poweredCopy(lever, true);

    assertThat(powered).isSameAs(copy);
    assertThat(((Powerable) powered).isPowered()).isTrue();
    assertThat(((Powerable) ArchitectWirelessRedstone.poweredCopy(lever, false)).isPowered())
        .isFalse();
  }

  @Test
  void aBoundRedstoneLampIsLitWithoutTouchingOtherLightableBlocks() {
    Lightable lamp = mock(Lightable.class);
    Lightable lampCopy = lightable(mock(Lightable.class));
    when(lamp.getMaterial()).thenReturn(Material.REDSTONE_LAMP);
    when(lamp.clone()).thenReturn(lampCopy);
    Furnace furnace = mock(Furnace.class);
    when(furnace.getMaterial()).thenReturn(Material.FURNACE);

    BlockData powered = ArchitectWirelessRedstone.poweredCopy(lamp, true);

    assertThat(powered).isSameAs(lampCopy);
    assertThat(((Lightable) powered).isLit()).isTrue();
    assertThat(ArchitectWirelessRedstone.isDriveable(furnace)).isFalse();
    assertThat(ArchitectWirelessRedstone.poweredCopy(furnace, true)).isNull();
  }

  @Test
  void analogueComponentsAreDrivenToFullPower() {
    RedstoneWire wire = mock(RedstoneWire.class);
    RedstoneWire copy = analogue(mock(RedstoneWire.class), 15);
    when(wire.getMaterial()).thenReturn(Material.REDSTONE_WIRE);
    when(wire.clone()).thenReturn(copy);

    RedstoneWire powered = (RedstoneWire) ArchitectWirelessRedstone.poweredCopy(wire, true);

    assertThat(powered.getPower()).isEqualTo(15);
    assertThat(((RedstoneWire) ArchitectWirelessRedstone.poweredCopy(wire, false)).getPower())
        .isZero();
    assertThat(ArchitectWirelessRedstone.isDriveable(mock(DaylightDetector.class))).isTrue();
  }

  @Test
  void doorsOpenWithoutFlippingThePoweredStateVanillaWouldReset() {
    Door door = mock(Door.class);
    Door copy = powerable(openable(mock(Door.class)));
    when(door.getMaterial()).thenReturn(Material.OAK_DOOR);
    when(door.clone()).thenReturn(copy);

    Door powered = (Door) ArchitectWirelessRedstone.poweredCopy(door, true);

    assertThat(powered.isOpen()).isTrue();
    assertThat(powered.isPowered()).isFalse();
    assertThat(((Door) ArchitectWirelessRedstone.poweredCopy(door, false)).isOpen()).isFalse();
  }

  @Test
  void blocksWithoutARedstoneStateAreNeverDriven() {
    Piston piston = mock(Piston.class);
    when(piston.getMaterial()).thenReturn(Material.PISTON);
    BlockData stone = mock(BlockData.class);
    when(stone.getMaterial()).thenReturn(Material.STONE);

    assertThat(ArchitectWirelessRedstone.isDriveable(piston)).isFalse();
    assertThat(ArchitectWirelessRedstone.isDriveable(stone)).isFalse();
    assertThat(ArchitectWirelessRedstone.isDriveable(null)).isFalse();
    assertThat(ArchitectWirelessRedstone.poweredCopy(stone, true)).isNull();
    assertThat(ArchitectWirelessRedstone.poweredCopy(null, true)).isNull();
  }

  @Test
  void onlyRedstoneFixturesCountAsLightableTargets() {
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Material.REDSTONE_LAMP)).isTrue();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Material.REDSTONE_TORCH)).isTrue();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Material.REDSTONE_WALL_TORCH)).isTrue();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Material.CAMPFIRE)).isFalse();
    assertThat(ArchitectWirelessRedstone.isRedstoneLightable(Material.REDSTONE_ORE)).isFalse();
  }

  private static <T extends Powerable> T powerable(T data) {
    AtomicBoolean state = new AtomicBoolean();
    doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setPowered(anyBoolean());
    when(data.isPowered()).thenAnswer(invocation -> state.get());
    return data;
  }

  private static <T extends Openable> T openable(T data) {
    AtomicBoolean state = new AtomicBoolean();
    doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setOpen(anyBoolean());
    when(data.isOpen()).thenAnswer(invocation -> state.get());
    return data;
  }

  private static <T extends org.bukkit.block.data.AnaloguePowerable> T analogue(T data, int maximum) {
    AtomicInteger state = new AtomicInteger();
    when(data.getMaximumPower()).thenReturn(maximum);
    doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setPower(anyInt());
    when(data.getPower()).thenAnswer(invocation -> state.get());
    return data;
  }

  private static <T extends Lightable> T lightable(T data) {
    AtomicBoolean state = new AtomicBoolean();
    doAnswer(invocation -> {
      state.set(invocation.getArgument(0));
      return null;
    }).when(data).setLit(anyBoolean());
    when(data.isLit()).thenAnswer(invocation -> state.get());
    return data;
  }
}
