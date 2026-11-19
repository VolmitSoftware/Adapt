package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Player inventory with Bukkit slot numbers, which match the vanilla inventory: 0-8 hotbar, 9-35
 * main, 36 feet, 37 legs, 38 chest, 39 head, 40 off hand.
 */
public final class ModPlayerInventoryRef extends ModInventoryRef implements PlayerInventoryRef {
    private static final int SIZE = 41;
    private final ModPlayerRef player;

    public ModPlayerInventoryRef(ModPlayerRef player) {
        super(() -> player.handle().getInventory());
        this.player = player;
    }

    private Inventory inventory() {
        return player.handle().getInventory();
    }

    @Override
    protected int storageSize() {
        return Inventory.INVENTORY_SIZE;
    }

    @Override
    public int size() {
        return SIZE;
    }

    /** Sends the change to the client at once, as Bukkit's player inventory does, instead of at the next player tick. */
    @Override
    protected void changed(Container target) {
        super.changed(target);
        player.handle().inventoryMenu.broadcastChanges();
    }

    @Override
    public ItemRef mainHand() {
        return ModRefs.item(inventory().getSelected());
    }

    @Override
    public void setMainHand(ItemRef item) {
        Inventory inventory = inventory();
        inventory.setItem(inventory.selected, copy(item));
        changed(inventory);
    }

    @Override
    public ItemRef offHand() {
        return ModRefs.item(player.handle().getOffhandItem());
    }

    @Override
    public void setOffHand(ItemRef item) {
        player.handle().setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, copy(item));
        changed(inventory());
    }

    @Override
    public ItemRef armor(EquipmentSlot slot) {
        net.minecraft.world.entity.EquipmentSlot vanilla = ModConversions.slot(slot);
        return ModRefs.item(vanilla == null ? ItemStack.EMPTY : player.handle().getItemBySlot(vanilla));
    }

    @Override
    public void setArmor(EquipmentSlot slot, ItemRef item) {
        net.minecraft.world.entity.EquipmentSlot vanilla = ModConversions.slot(slot);
        if (vanilla == null) {
            return;
        }
        player.handle().setItemSlot(vanilla, copy(item));
        changed(inventory());
    }

    @Override
    public int heldSlot() {
        return inventory().selected;
    }

    @Override
    public void setHeldSlot(int slot) {
        if (!Inventory.isHotbarSlot(slot)) {
            return;
        }
        ServerPlayer handle = player.handle();
        handle.getInventory().selected = slot;
        handle.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }

    @Override
    public ItemRef cursor() {
        return ModRefs.item(player.handle().containerMenu.getCarried());
    }

    @Override
    public void setCursor(ItemRef item) {
        ServerPlayer handle = player.handle();
        handle.containerMenu.setCarried(copy(item));
        handle.containerMenu.broadcastChanges();
    }

    private static ItemStack copy(ItemRef item) {
        return item == null ? ItemStack.EMPTY : ModRefs.item(item).copy();
    }
}
