package art.arcane.adapt.bukkit.canvas.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import java.lang.reflect.Field;

/**
 * Drops updates of the viewer's real inventory while a canvas is open, so a plugin writing to it (Bukkit
 * {@code setItem}, {@code addItem}) cannot draw real items into the canvas. The resend on close carries the truth.
 */
final class CanvasGuard extends ChannelOutboundHandlerAdapter {
  static final String NAME = "adapt_canvas_guard";
  private static final int FIRST_STORAGE_SLOT = 9;
  private static final int LAST_HOTBAR_SLOT = 44;
  private static final int LAST_INVENTORY_INDEX = 35;
  private static volatile Field connectionField;

  private volatile boolean active;

  /** True for a packet that would show a real inventory item in the player rows of the open window. */
  static boolean drops(Object packet) {
    if (packet instanceof ClientboundContainerSetSlotPacket slot) {
      return slot.getContainerId() == 0 && slot.getSlot() >= FIRST_STORAGE_SLOT && slot.getSlot() <= LAST_HOTBAR_SLOT;
    }
    if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
      return inventory.slot() >= 0 && inventory.slot() <= LAST_INVENTORY_INDEX;
    }
    return packet instanceof ClientboundContainerSetContentPacket content && content.containerId() == 0;
  }

  /** The viewer's guard, installed on first use and gone with the connection. */
  static CanvasGuard of(ServerPlayer player) throws ReflectiveOperationException {
    Channel channel = channel(player);
    ChannelHandler existing = channel.pipeline().get(NAME);
    if (existing instanceof CanvasGuard guard) {
      return guard;
    }
    CanvasGuard guard = new CanvasGuard();
    channel.pipeline().addBefore("packet_handler", NAME, guard);
    return guard;
  }

  static Channel channel(ServerPlayer player) throws ReflectiveOperationException {
    Field field = connectionField;
    if (field == null) {
      // Spigot keeps this field protected; Paper makes it public.
      field = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
      field.setAccessible(true);
      connectionField = field;
    }
    return ((Connection) field.get(player.connection)).channel;
  }

  void active(boolean value) {
    active = value;
  }

  boolean active() {
    return active;
  }

  @Override
  public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
    if (active && drops(message)) {
      promise.setSuccess();
      return;
    }
    super.write(context, message, promise);
  }
}
