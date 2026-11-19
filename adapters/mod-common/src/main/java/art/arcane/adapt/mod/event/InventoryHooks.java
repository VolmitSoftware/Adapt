package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.feature.menu.AdaptChestMenu;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Container clicks, closes, crafting and enchanting. */
public final class InventoryHooks {
    private static final String ADAPT_PACKAGE = "art.arcane.adapt.";
    private static final Set<InventoryAction> FREE_INVENTORY_ACTIONS = EnumSet.of(InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_SOME,
        InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE, InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE,
        InventoryAction.SWAP_WITH_CURSOR);
    private static List<EnchantmentInstance> enchantOverride;
    private static boolean resultTaken;

    private InventoryHooks() {
    }

    /** Adapt's own menus, which handle clicks on their slots themselves. */
    public static boolean adaptMenu(AbstractContainerMenu menu) {
        return menu instanceof AdaptChestMenu || menu.getClass().getName().startsWith(ADAPT_PACKAGE);
    }

    /**
     * A click on the open menu, before vanilla applies it. Slot and cursor items a handler set are
     * written first; a cancel resends the whole menu. True when cancelled. On an Adapt menu the menu's own slots
     * are left to the menu.
     */
    public static boolean clicking(ServerPlayer player, int slotIndex, int button, String input) {
        AbstractContainerMenu menu = player.containerMenu;
        resultTaken = false;
        if ("QUICK_CRAFT".equals(input)) {
            return false;
        }
        Slot slot = slotIndex >= 0 && slotIndex < menu.slots.size() ? menu.getSlot(slotIndex) : null;
        boolean craft = slot instanceof ResultSlot && HookCompat.craftingMenu(menu) && slot.hasItem();
        resultTaken = craft || menu instanceof SmithingMenu smithing && slotIndex == smithing.getResultSlot() && slot != null && slot.hasItem();
        if (!ModEvents.listening(craft ? CraftItemEvent.class : InventoryClickEvent.class)) {
            return false;
        }
        InventoryKind view = ModEventMaps.kind(menu);
        ClickKind click = ModEventMaps.clickKind(input, button);
        InventoryAction action = ModEventMaps.action(input, button, slotIndex, slot, menu.getCarried(), player);
        int hotbar = "SWAP".equals(input) && button >= 0 && button < 9 ? button : -1;
        ItemRef current = slot == null ? null : ModRefs.item(slot.getItem());
        ItemRef cursor = ModRefs.item(menu.getCarried());
        int localSlot = slot == null ? slotIndex : slot.getContainerSlot();
        InventoryClickEvent event;
        if (craft) {
            Id key = HookCompat.recipeId(slot.container);
            CraftItemEvent.RecipeKind kind = HookCompat.recipeKind(slot.container);
            List<ItemRef> matrix = new ArrayList<>();
            for (Slot grid : HookCompat.craftingGrid(menu)) {
                matrix.add(ModRefs.item(grid.getItem().copy()));
            }
            event = new CraftItemEvent(ModRefs.player(player), view, localSlot, slotIndex, click, action, hotbar, current, cursor, key, kind,
                matrix);
        } else {
            event = new InventoryClickEvent(ModRefs.player(player), view, ModEventMaps.clickedKind(menu, slot), localSlot, slotIndex,
                ModEventMaps.slotKind(menu, slot), click, action, hotbar, current, cursor);
            if (view == InventoryKind.STONECUTTER || view == InventoryKind.MERCHANT) {
                viewContents(event, menu, player);
            }
        }
        boolean ownInventory = slot != null && slot.container == player.getInventory();
        if (adaptMenu(menu)) {
            postInMenu(event, menu instanceof AdaptChestMenu chest && chest.freePlayerInventory() && ownInventory);
        } else {
            ModEvents.post(event);
        }
        if (slot != null && event.currentItem() != current) {
            slot.set(ModRefs.item(event.currentItem()));
        }
        if (event.cursor() != cursor) {
            menu.setCarried(ModRefs.item(event.cursor()));
        }
        if (!event.isCancelled() || adaptMenu(menu) && !ownInventory) {
            return false;
        }
        menu.sendAllDataToRemote();
        return true;
    }

    /**
     * Posts a click on an Adapt menu as Bukkit delivers it around the inventorygui window: the window cancels every
     * click on an inventory after the NORMAL handlers, and a menu that frees the player's inventory un-cancels plain
     * clicks there after the HIGH handlers.
     */
    private static void postInMenu(InventoryClickEvent event, boolean freeOwnInventory) {
        AdaptEventBus bus = ModEvents.bus();
        if (bus == null) {
            return;
        }
        bus.post(event, EventPriority.LOWEST);
        bus.post(event, EventPriority.LOW);
        bus.post(event, EventPriority.NORMAL);
        if (event.clickedKind() != null) {
            event.setCancelled(true);
        }
        bus.post(event, EventPriority.HIGH);
        if (freeOwnInventory && FREE_INVENTORY_ACTIONS.contains(event.action())) {
            event.setCancelled(false);
        }
        bus.post(event, EventPriority.HIGHEST);
        bus.post(event, EventPriority.MONITOR);
    }

    /**
     * After vanilla applied a click on a crafting or smithing result: the whole menu is resent, as Paper does after
     * its craft and smith events, so the client sees the recomputed result even when nothing moved.
     */
    public static void clicked(ServerPlayer player) {
        if (resultTaken) {
            resultTaken = false;
            player.containerMenu.sendAllDataToRemote();
        }
    }

    private static void viewContents(InventoryClickEvent event, AbstractContainerMenu menu, ServerPlayer player) {
        List<ItemRef> top = new ArrayList<>();
        MerchantContainer trade = null;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            top.add(ModRefs.item(slot.getItem().copy()));
            if (slot.container instanceof MerchantContainer merchant) {
                trade = merchant;
            }
        }
        List<ItemRef> cost = new ArrayList<>(2);
        MerchantOffer offer = trade == null ? null : trade.getActiveOffer();
        if (offer != null) {
            for (ItemStack stack : List.of(offer.getCostA(), offer.getCostB())) {
                if (!stack.isEmpty()) {
                    cost.add(ModRefs.item(stack.copy()));
                }
            }
        }
        event.setViewContents(top, cost);
    }

    public static void closing(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!ModEvents.listening(InventoryCloseEvent.class)) {
            return;
        }
        ModEvents.post(new InventoryCloseEvent(ModRefs.player(player), ModEventMaps.kind(menu)));
    }

    /** A crafted batch reached the player. */
    public static void crafted(Player player, Container result, ItemStack carried, int count) {
        if (count <= 0 || !(player instanceof ServerPlayer serverPlayer) || !ModEvents.listening(ItemCraftedEvent.class)) {
            return;
        }
        ModEvents.post(new ItemCraftedEvent(ModRefs.player(serverPlayer), ModRefs.item(carried.copyWithCount(count)),
            HookCompat.recipeId(result)));
    }

    /**
     * An enchanting table offer was picked, before vanilla applies it. Returns the enchantments to
     * apply (the offer itself when unchanged), or null when a handler cancelled or raised the cost
     * above the player's level.
     */
    public static List<EnchantmentInstance> enchanting(Player player, Level level, BlockPos table, ItemStack item, int button, int cost,
                                                       List<EnchantmentInstance> offered) {
        enchantOverride = null;
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)
            || !ModEvents.listening(EnchantItemEvent.class) || offered.isEmpty()) {
            return offered;
        }
        Map<Id, Integer> offer = new LinkedHashMap<>();
        for (EnchantmentInstance instance : offered) {
            offer.put(HookCompat.enchantmentId(instance), HookCompat.enchantmentLevel(instance));
        }
        EnchantItemEvent event = ModEvents.post(new EnchantItemEvent(ModRefs.player(serverPlayer), ModRefs.block(serverLevel, table),
            ModRefs.item(item), button, offer, cost));
        if (event.isCancelled()) {
            return null;
        }
        if (event.expLevelCost() > player.experienceLevel && !HookCompat.infiniteMaterials(player)) {
            return null;
        }
        if (event.enchantsToAdd().equals(offer)) {
            return offered;
        }
        List<EnchantmentInstance> chosen = new ArrayList<>(event.enchantsToAdd().size());
        for (Map.Entry<Id, Integer> entry : event.enchantsToAdd().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                EnchantmentInstance instance = HookCompat.enchantment(entry.getKey(), entry.getValue());
                if (instance != null) {
                    chosen.add(instance);
                }
            }
        }
        if (chosen.isEmpty()) {
            return null;
        }
        enchantOverride = chosen;
        return chosen;
    }

    /** The enchantments vanilla's offer lookup must return for the pick in progress, or the given list. */
    public static List<EnchantmentInstance> enchantments(List<EnchantmentInstance> vanilla) {
        List<EnchantmentInstance> override = enchantOverride;
        if (override == null) {
            return vanilla;
        }
        enchantOverride = null;
        return override;
    }

    public static void enchanted() {
        enchantOverride = null;
    }
}
