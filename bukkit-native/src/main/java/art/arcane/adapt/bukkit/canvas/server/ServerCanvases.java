package art.arcane.adapt.bukkit.canvas.server;

import art.arcane.adapt.bukkit.canvas.CanvasClicks;
import art.arcane.adapt.bukkit.canvas.CanvasInput;
import art.arcane.adapt.bukkit.canvas.CanvasItem;
import art.arcane.adapt.bukkit.canvas.CanvasWindow;
import art.arcane.adapt.bukkit.canvas.Canvases;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Canvas windows on Paper, Folia and Spigot 26.x, which share these Mojang names. */
public final class ServerCanvases implements Canvases {
  @Override
  public void verify(Player sample) {
    for (ContainerInput input : ContainerInput.values()) {
      CanvasInput.valueOf(input.name());
    }
    CanvasMenu.type(6);
    for (Class<?> needed : new Class<?>[] {ClientboundSetPlayerInventoryPacket.class, ClientboundOpenScreenPacket.class,
        ItemLore.class, CanvasView.class}) {
      needed.getDeclaredMethods();
    }
    try {
      CanvasGuard.channel(((CraftPlayer) sample).getHandle());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("no player channel", failure);
    }
  }

  @Override
  public CanvasWindow open(Player viewer, int rows, String titleJson, CanvasItem[] items, CanvasClicks clicks, Runnable closed) {
    ServerPlayer player = ((CraftPlayer) viewer).getHandle();
    CanvasGuard guard;
    try {
      guard = CanvasGuard.of(player);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("no player channel", failure);
    }
    Component title = CanvasText.component(player, titleJson);
    CanvasMenu[] created = new CanvasMenu[1];
    OptionalInt opened = player.openMenu(new SimpleMenuProvider((int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                                 net.minecraft.world.entity.player.Player who) -> {
      CanvasMenu menu = new CanvasMenu(containerId, rows, player, clicks, guard, closed);
      for (int slot = 0; slot < items.length; slot++) {
        if (items[slot] != null) {
          menu.put(slot, stack(player, items[slot]));
        }
      }
      created[0] = menu;
      return menu;
    }, title));
    if (opened.isEmpty() || created[0] == null || player.containerMenu != created[0]) {
      return null;
    }
    guard.active(true);
    return new Window(created[0]);
  }

  static ItemStack stack(ServerPlayer player, CanvasItem item) {
    ItemStack stack = CraftItemStack.asNMSCopy(item.stack());
    if (item.nameJson() != null) {
      stack.set(DataComponents.CUSTOM_NAME, CanvasText.component(player, item.nameJson()));
    }
    if (!item.loreJson().isEmpty()) {
      List<Component> lines = new ArrayList<>(item.loreJson().size());
      for (String line : item.loreJson()) {
        lines.add(CanvasText.component(player, line));
      }
      stack.set(DataComponents.LORE, new ItemLore(lines));
    }
    return stack;
  }

  private static final class Window implements CanvasWindow {
    private final CanvasMenu menu;

    private Window(CanvasMenu menu) {
      this.menu = menu;
    }

    @Override
    public int rows() {
      return menu.rows();
    }

    @Override
    public InventoryView view() {
      return menu.getBukkitView();
    }

    @Override
    public boolean isOpen() {
      return !menu.retired() && menu.owner().containerMenu == menu;
    }

    @Override
    public void set(int windowSlot, CanvasItem item) {
      menu.put(windowSlot, item == null ? ItemStack.EMPTY : stack(menu.owner(), item));
    }

    @Override
    public void flush() {
      if (isOpen()) {
        menu.broadcastChanges();
      }
    }

    @Override
    public void retitle(String titleJson) {
      if (!isOpen()) {
        return;
      }
      ServerPlayer player = menu.owner();
      player.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), CanvasText.component(player, titleJson)));
      menu.sendAllDataToRemote();
    }

    @Override
    public void retire(boolean restoreInventory) {
      menu.retire(restoreInventory);
    }

    @Override
    public void close() {
      if (menu.owner().containerMenu == menu) {
        menu.owner().getBukkitEntity().closeInventory();
      }
    }
  }
}
