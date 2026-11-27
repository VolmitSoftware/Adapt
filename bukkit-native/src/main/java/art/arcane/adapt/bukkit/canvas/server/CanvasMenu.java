package art.arcane.adapt.bukkit.canvas.server;

import art.arcane.adapt.bukkit.canvas.CanvasClicks;
import art.arcane.adapt.bukkit.canvas.CanvasInput;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.inventory.InventoryView;

/**
 * A chest menu of {@code rows * 9} slots plus 36 player-row slots, all over private containers. Clicks are handed to
 * {@link CanvasClicks} and never move anything; the player's own inventory is not part of this menu.
 */
final class CanvasMenu extends AbstractContainerMenu {
  static final int LOWER_SLOTS = 36;
  private static final int OFFHAND_BUTTON = 40;
  private static final int COLUMNS = 9;

  private final int rows;
  private final ServerPlayer owner;
  private final SimpleContainer top;
  private final SimpleContainer lower;
  private final CanvasClicks clicks;
  private final CanvasGuard guard;
  private final Runnable closed;
  private boolean retired;
  private InventoryView view;

  CanvasMenu(int containerId, int rows, ServerPlayer owner, CanvasClicks clicks, CanvasGuard guard, Runnable closed) {
    super(type(rows), containerId);
    this.rows = rows;
    this.owner = owner;
    this.clicks = clicks;
    this.guard = guard;
    this.closed = closed;
    this.top = new SimpleContainer(rows * COLUMNS);
    this.lower = new SimpleContainer(LOWER_SLOTS);
    for (int index = 0; index < rows * COLUMNS; index++) {
      addSlot(new Locked(top, index, 8 + ((index % COLUMNS) * 18), 18 + ((index / COLUMNS) * 18)));
    }
    for (int index = 0; index < LOWER_SLOTS; index++) {
      addSlot(new Locked(lower, index, 8 + ((index % COLUMNS) * 18), 140 + ((index / COLUMNS) * 18)));
    }
  }

  static MenuType<?> type(int rows) {
    return switch (rows) {
      case 1 -> MenuType.GENERIC_9x1;
      case 2 -> MenuType.GENERIC_9x2;
      case 3 -> MenuType.GENERIC_9x3;
      case 4 -> MenuType.GENERIC_9x4;
      case 5 -> MenuType.GENERIC_9x5;
      case 6 -> MenuType.GENERIC_9x6;
      default -> throw new IllegalArgumentException("rows must be 1..6: " + rows);
    };
  }

  int rows() {
    return rows;
  }

  ServerPlayer owner() {
    return owner;
  }

  boolean retired() {
    return retired;
  }

  void put(int windowSlot, ItemStack stack) {
    int topSize = rows * COLUMNS;
    if (windowSlot < topSize) {
      top.setItem(windowSlot, stack);
    } else {
      lower.setItem(windowSlot - topSize, stack);
    }
  }

  /** Marks the menu as handed over: closing it runs no callback and leaves the client inventory alone. */
  void retire(boolean restoreInventory) {
    if (retired) {
      return;
    }
    retired = true;
    if (restoreInventory) {
      restore();
    }
  }

  @Override
  public void clicked(int slot, int button, ContainerInput input, Player player) {
    if (input == ContainerInput.SWAP && button == OFFHAND_BUTTON) {
      resendOffhand();
    }
    clicks.clicked(slot, button, CanvasInput.valueOf(input.name()));
  }

  private void resendOffhand() {
    InventoryMenu inventory = owner.inventoryMenu;
    owner.connection.send(new ClientboundContainerSetSlotPacket(inventory.containerId, inventory.incrementStateId(),
        InventoryMenu.SHIELD_SLOT, inventory.getSlot(InventoryMenu.SHIELD_SLOT).getItem().copy()));
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player player) {
    return true;
  }

  @Override
  public boolean canDragTo(Slot slot) {
    return false;
  }

  @Override
  public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
    return false;
  }

  @Override
  public void removed(Player player) {
    // A carried stand-in would otherwise be placed into the real inventory.
    setCarried(ItemStack.EMPTY);
    super.removed(player);
    if (retired) {
      return;
    }
    retired = true;
    restore();
    closed.run();
  }

  private void restore() {
    guard.active(false);
    owner.inventoryMenu.sendAllDataToRemote();
  }

  @Override
  public InventoryView getBukkitView() {
    if (view == null) {
      view = new CanvasView(owner.getBukkitEntity(), new CraftInventory(top), this, new CraftInventory(lower));
    }
    return view;
  }

  private static final class Locked extends Slot {
    private Locked(SimpleContainer container, int index, int x, int y) {
      super(container, index, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
      return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return false;
    }
  }
}
