package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.util.Optional;

/** Vanilla-to-SPI mappings with Bukkit's names. */
public final class ModEventMaps {
    private static final Id UNKNOWN_DAMAGE = new Id("minecraft", "generic");

    private ModEventMaps() {
    }

    static Id damageType(DamageSource source) {
        Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
        return key.map((ResourceKey<DamageType> value) -> ModRefs.id(value.identifier())).orElse(UNKNOWN_DAMAGE);
    }

    static EntityRemoveEvent.Cause removal(Entity.RemovalReason reason) {
        return switch (reason) {
            case KILLED -> EntityRemoveEvent.Cause.DEATH;
            case DISCARDED -> EntityRemoveEvent.Cause.DISCARD;
            case UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER -> EntityRemoveEvent.Cause.UNLOAD;
            case CHANGED_DIMENSION -> EntityRemoveEvent.Cause.CHANGED_DIMENSION;
        };
    }

    public static InventoryKind kind(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu) {
            return InventoryKind.CRAFTING;
        }
        if (menu instanceof CraftingMenu) {
            return InventoryKind.WORKBENCH;
        }
        if (menu instanceof ChestMenu chest) {
            Container container = chest.getContainer();
            if (container instanceof PlayerEnderChestContainer) {
                return InventoryKind.ENDER_CHEST;
            }
            if (container instanceof BarrelBlockEntity) {
                return InventoryKind.BARREL;
            }
            return InventoryKind.CHEST;
        }
        if (menu instanceof ShulkerBoxMenu) {
            return InventoryKind.SHULKER_BOX;
        }
        if (menu instanceof HopperMenu) {
            return InventoryKind.HOPPER;
        }
        if (menu instanceof DispenserMenu) {
            return InventoryKind.DISPENSER;
        }
        if (menu instanceof SmokerMenu) {
            return InventoryKind.SMOKER;
        }
        if (menu instanceof BlastFurnaceMenu) {
            return InventoryKind.BLAST_FURNACE;
        }
        if (menu instanceof AbstractFurnaceMenu) {
            return InventoryKind.FURNACE;
        }
        if (menu instanceof BrewingStandMenu) {
            return InventoryKind.BREWING;
        }
        if (menu instanceof EnchantmentMenu) {
            return InventoryKind.ENCHANTING;
        }
        if (menu instanceof AnvilMenu) {
            return InventoryKind.ANVIL;
        }
        if (menu instanceof GrindstoneMenu) {
            return InventoryKind.GRINDSTONE;
        }
        if (menu instanceof SmithingMenu) {
            return InventoryKind.SMITHING;
        }
        if (menu instanceof StonecutterMenu) {
            return InventoryKind.STONECUTTER;
        }
        if (menu instanceof LoomMenu) {
            return InventoryKind.LOOM;
        }
        if (menu instanceof CartographyTableMenu) {
            return InventoryKind.CARTOGRAPHY;
        }
        if (menu instanceof MerchantMenu) {
            return InventoryKind.MERCHANT;
        }
        if (menu instanceof BeaconMenu) {
            return InventoryKind.BEACON;
        }
        return InventoryKind.OTHER;
    }

    static InventoryKind clickedKind(AbstractContainerMenu menu, Slot slot) {
        if (slot == null) {
            return null;
        }
        return slot.container instanceof Inventory ? InventoryKind.PLAYER : kind(menu);
    }

    static SlotKind slotKind(AbstractContainerMenu menu, Slot slot) {
        if (slot == null) {
            return SlotKind.OUTSIDE;
        }
        if (slot instanceof ResultSlot) {
            return SlotKind.RESULT;
        }
        if (slot.container instanceof Inventory) {
            int index = slot.getContainerSlot();
            if (index < 9) {
                return SlotKind.QUICKBAR;
            }
            if (index >= 36 && index < 40) {
                return SlotKind.ARMOR;
            }
            return SlotKind.CONTAINER;
        }
        if (menu instanceof InventoryMenu || menu instanceof CraftingMenu) {
            return SlotKind.CRAFTING;
        }
        if (menu instanceof AbstractFurnaceMenu && slot.index == 1) {
            return SlotKind.FUEL;
        }
        if (menu instanceof AbstractFurnaceMenu && slot.index == 2) {
            return SlotKind.RESULT;
        }
        return SlotKind.CONTAINER;
    }

    static ClickKind clickKind(String input, int button) {
        return switch (input) {
            case "PICKUP" -> button == 0 ? ClickKind.LEFT : ClickKind.RIGHT;
            case "QUICK_MOVE" -> button == 0 ? ClickKind.SHIFT_LEFT : ClickKind.SHIFT_RIGHT;
            case "SWAP" -> button == 40 ? ClickKind.SWAP_OFFHAND : ClickKind.NUMBER_KEY;
            case "CLONE" -> ClickKind.MIDDLE;
            case "THROW" -> button == 0 ? ClickKind.DROP : ClickKind.CONTROL_DROP;
            case "PICKUP_ALL" -> ClickKind.DOUBLE_CLICK;
            default -> ClickKind.OTHER;
        };
    }

    /** Bukkit's click-to-action table, computed before the click runs. */
    static InventoryAction action(String input, int button, int slotIndex, Slot slot, ItemStack cursor, Player player) {
        ItemStack current = slot == null ? ItemStack.EMPTY : slot.getItem();
        switch (input) {
            case "PICKUP": {
                if (slotIndex == -999) {
                    if (cursor.isEmpty()) {
                        return InventoryAction.NOTHING;
                    }
                    return button == 0 ? InventoryAction.DROP_ALL_CURSOR : InventoryAction.DROP_ONE_CURSOR;
                }
                if (slot == null) {
                    return InventoryAction.NOTHING;
                }
                if (current.isEmpty()) {
                    if (cursor.isEmpty() || !slot.mayPlace(cursor)) {
                        return InventoryAction.NOTHING;
                    }
                    return button == 0 || cursor.getCount() == 1 ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_ONE;
                }
                if (!slot.mayPickup(player)) {
                    return InventoryAction.NOTHING;
                }
                if (cursor.isEmpty()) {
                    return button == 0 ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_HALF;
                }
                if (slot.mayPlace(cursor)) {
                    if (ItemStack.isSameItemSameComponents(current, cursor)) {
                        int toPlace = button == 0 ? cursor.getCount() : 1;
                        int room = Math.min(current.getMaxStackSize(), slot.getMaxStackSize(current)) - current.getCount();
                        if (room <= 0) {
                            return InventoryAction.NOTHING;
                        }
                        if (toPlace == 1) {
                            return InventoryAction.PLACE_ONE;
                        }
                        return toPlace <= room ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_SOME;
                    }
                    return cursor.getCount() <= slot.getMaxStackSize(cursor) ? InventoryAction.SWAP_WITH_CURSOR : InventoryAction.NOTHING;
                }
                if (ItemStack.isSameItemSameComponents(current, cursor) && current.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                    return InventoryAction.PICKUP_ALL;
                }
                return InventoryAction.NOTHING;
            }
            case "QUICK_MOVE":
                if (slot == null || !slot.mayPickup(player) || !slot.hasItem()) {
                    return InventoryAction.NOTHING;
                }
                return InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case "SWAP":
                if (slot == null || !slot.mayPickup(player)) {
                    return InventoryAction.NOTHING;
                }
                return InventoryAction.HOTBAR_SWAP;
            case "CLONE":
                if (slot == null || !slot.hasItem() || !player.hasInfiniteMaterials() || !cursor.isEmpty()) {
                    return InventoryAction.NOTHING;
                }
                return InventoryAction.CLONE_STACK;
            case "THROW":
                if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
                    return InventoryAction.NOTHING;
                }
                return button == 0 ? InventoryAction.DROP_ONE_SLOT : InventoryAction.DROP_ALL_SLOT;
            case "PICKUP_ALL":
                return cursor.isEmpty() ? InventoryAction.NOTHING : InventoryAction.COLLECT_TO_CURSOR;
            default:
                return InventoryAction.UNKNOWN;
        }
    }
}
