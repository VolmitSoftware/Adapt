package art.arcane.adapt.bukkit.canvas.server;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanvasGuardTest {
  @BeforeAll
  static void bootstrap() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  @Test
  void dropsRealInventorySlotsOnly() {
    assertThat(CanvasGuard.drops(slot(0, 9))).isTrue();
    assertThat(CanvasGuard.drops(slot(0, 44))).isTrue();
    assertThat(CanvasGuard.drops(slot(0, 8))).isFalse();
    assertThat(CanvasGuard.drops(slot(0, 45))).isFalse();
    assertThat(CanvasGuard.drops(slot(3, 20))).isFalse();
    assertThat(CanvasGuard.drops(new ClientboundSetPlayerInventoryPacket(0, ItemStack.EMPTY))).isTrue();
    assertThat(CanvasGuard.drops(new ClientboundSetPlayerInventoryPacket(35, ItemStack.EMPTY))).isTrue();
    assertThat(CanvasGuard.drops(new ClientboundSetPlayerInventoryPacket(40, ItemStack.EMPTY))).isFalse();
    assertThat(CanvasGuard.drops(content(0))).isTrue();
    assertThat(CanvasGuard.drops(content(5))).isFalse();
    assertThat(CanvasGuard.drops("not a packet")).isFalse();
  }

  @Test
  void activeGuardSwallowsLeaksAndPassesTheRest() {
    CanvasGuard guard = new CanvasGuard();
    EmbeddedChannel channel = new EmbeddedChannel(guard);
    guard.active(true);
    channel.writeOutbound(slot(0, 20), slot(4, 20), slot(0, 45), content(0));
    assertThat(drain(channel)).containsExactly(List.of(4, 20), List.of(0, 45));
    guard.active(false);
    channel.writeOutbound(slot(0, 20));
    assertThat(drain(channel)).containsExactly(List.of(0, 20));
  }

  private static List<List<Integer>> drain(EmbeddedChannel channel) {
    List<List<Integer>> slots = new java.util.ArrayList<>();
    Object message;
    while ((message = channel.readOutbound()) != null) {
      ClientboundContainerSetSlotPacket packet = (ClientboundContainerSetSlotPacket) message;
      slots.add(List.of(packet.getContainerId(), packet.getSlot()));
    }
    return slots;
  }

  private static Packet<?> slot(int containerId, int slot) {
    return new ClientboundContainerSetSlotPacket(containerId, 1, slot, ItemStack.EMPTY);
  }

  private static Packet<?> content(int containerId) {
    return new ClientboundContainerSetContentPacket(containerId, 1, NonNullList.withSize(46, ItemStack.EMPTY), ItemStack.EMPTY);
  }
}
