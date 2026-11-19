package art.arcane.adapt.mod.event.inventory;

import art.arcane.adapt.mod.event.InventoryHooks;
import art.arcane.adapt.mod.event.ModEventMaps;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.mixin.gapblock.CompoundContainerAccess;
import art.arcane.adapt.mod.mixin.gapblock.MerchantMenuAccess;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.entity.ModInventoryRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.EnchantOffer;
import art.arcane.adapt.platform.event.inventory.InventoryDragEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryOpenEvent;
import art.arcane.adapt.platform.event.inventory.InventoryPickupItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/** Container opens, drags, hopper moves, result previews and brewing. */
public final class ContainerHooks {
    private static final ItemStack[] BREW_SNAPSHOT = new ItemStack[4];
    private static boolean brewing;

    private ContainerHooks() {
    }

    static InventoryKind kind(Container container) {
        if (container instanceof Hopper) {
            return InventoryKind.HOPPER;
        }
        if (container instanceof DispenserBlockEntity) {
            return InventoryKind.DISPENSER;
        }
        if (container instanceof ChestBlockEntity || container instanceof CompoundContainer) {
            return InventoryKind.CHEST;
        }
        if (container instanceof BarrelBlockEntity) {
            return InventoryKind.BARREL;
        }
        if (container instanceof ShulkerBoxBlockEntity) {
            return InventoryKind.SHULKER_BOX;
        }
        if (container instanceof BrewingStandBlockEntity) {
            return InventoryKind.BREWING;
        }
        if (container instanceof SmokerBlockEntity) {
            return InventoryKind.SMOKER;
        }
        if (container instanceof BlastFurnaceBlockEntity) {
            return InventoryKind.BLAST_FURNACE;
        }
        if (container instanceof AbstractFurnaceBlockEntity) {
            return InventoryKind.FURNACE;
        }
        if (container instanceof PlayerEnderChestContainer) {
            return InventoryKind.ENDER_CHEST;
        }
        if (container instanceof Inventory) {
            return InventoryKind.PLAYER;
        }
        return InventoryKind.OTHER;
    }

    static BlockRef holderBlock(Container container) {
        Container holder = container instanceof CompoundContainer compound ? ((CompoundContainerAccess) compound).adapt$first() : container;
        if (holder instanceof BlockEntity blockEntity && blockEntity.getLevel() instanceof ServerLevel level) {
            return ModRefs.block(level, blockEntity.getBlockPos());
        }
        return null;
    }

    static InventoryRef inventory(Container container) {
        return container == null ? null : new ModInventoryRef(() -> container);
    }

    static Container top(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                return slot.container;
            }
        }
        return null;
    }

    static int topSize(AbstractContainerMenu menu) {
        int size = 0;
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                size++;
            }
        }
        return size;
    }

    /** The player whose open menu this is, or null while the menu is still being built. */
    static ServerPlayer viewer(AbstractContainerMenu menu) {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu == menu) {
                return player;
            }
        }
        return null;
    }

    /** A menu was built for a player and is about to be shown. False when a handler cancelled. */
    public static boolean opening(ServerPlayer player, AbstractContainerMenu menu) {
        if (menu == null || !ModEvents.listening(InventoryOpenEvent.class)) {
            return true;
        }
        Container container = top(menu);
        EntityRef holderEntity = null;
        if (menu instanceof MerchantMenu && ((MerchantMenuAccess) menu).adapt$trader() instanceof Entity trader) {
            holderEntity = ModRefs.entity(trader);
        } else if (container instanceof Entity entity) {
            holderEntity = ModRefs.entity(entity);
        }
        InventoryOpenEvent event = ModEvents.post(new InventoryOpenEvent(ModRefs.player(player), ModEventMaps.kind(menu), inventory(container),
            container == null ? null : holderBlock(container), holderEntity));
        return !event.isCancelled();
    }

    public static boolean openingHorse(ServerPlayer player, AbstractHorse horse, Container container) {
        if (!ModEvents.listening(InventoryOpenEvent.class)) {
            return true;
        }
        InventoryOpenEvent event = ModEvents.post(new InventoryOpenEvent(ModRefs.player(player), InventoryKind.OTHER, inventory(container), null,
            ModRefs.entity(horse)));
        return !event.isCancelled();
    }

    /** The final step of a drag across several slots, before vanilla spreads the stack. False when cancelled. */
    public static boolean dragging(AbstractContainerMenu menu, Player player, Set<Slot> slots, int type) {
        if (!(player instanceof ServerPlayer serverPlayer) || InventoryHooks.adaptMenu(menu) || !ModEvents.listening(InventoryDragEvent.class)) {
            return true;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return true;
        }
        Set<Integer> rawSlots = new LinkedHashSet<>();
        int remaining = carried.getCount();
        for (Slot slot : slots) {
            if (slot != null && AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && slot.mayPlace(carried)
                && (type == 2 || carried.getCount() >= slots.size()) && menu.canDragTo(slot)) {
                int carry = slot.hasItem() ? slot.getItem().getCount() : 0;
                int maxSize = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                int newCount = Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(slots, type, carried) + carry, maxSize);
                remaining -= newCount - carry;
                rawSlots.add(slot.index);
            }
        }
        ItemRef newCursor = remaining > 0 ? ModRefs.item(carried.copyWithCount(remaining)) : null;
        InventoryDragEvent event = ModEvents.post(new InventoryDragEvent(ModRefs.player(serverPlayer), ModEventMaps.kind(menu), topSize(menu),
            rawSlots, ModRefs.item(carried.copy()), newCursor));
        return !event.isCancelled();
    }

    /** A hopper or dropper moving one item between containers. False when cancelled. */
    public static boolean moving(Container source, Container destination, ItemStack item, boolean sourceInitiated) {
        if (!ModEvents.listening(InventoryMoveItemEvent.class) || item.isEmpty()) {
            return true;
        }
        InventoryMoveItemEvent event = ModEvents.post(new InventoryMoveItemEvent(inventory(source), kind(source), holderBlock(source),
            inventory(destination), kind(destination), holderBlock(destination), ModRefs.item(item.copy()), sourceInitiated));
        return !event.isCancelled();
    }

    /** A hopper or hopper minecart taking an item entity. False when cancelled. */
    public static boolean pickingUp(Container container, ItemEntity item) {
        if (!(item.level() instanceof ServerLevel) || !ModEvents.listening(InventoryPickupItemEvent.class)) {
            return true;
        }
        InventoryPickupItemEvent event = ModEvents.post(new InventoryPickupItemEvent(inventory(container), kind(container), holderBlock(container),
            (ItemEntityRef) ModRefs.entity(item)));
        return !event.isCancelled();
    }

    /** The crafting grid result about to be shown. Returns the result to show. */
    public static ItemStack preparingCraft(Player player, CraftingContainer grid, ResultContainer results, ItemStack result) {
        if (!(player instanceof ServerPlayer serverPlayer) || !ModEvents.listening(PrepareItemCraftEvent.class)) {
            return result;
        }
        Recipe<?> recipe = result.isEmpty() ? null : ContainerCompat.recipe(results);
        List<ItemRef> matrix = new ArrayList<>(grid.getContainerSize());
        for (ItemStack stack : grid.getItems()) {
            matrix.add(ModRefs.item(stack.copy()));
        }
        Id key = recipe == null ? null : ContainerCompat.recipeId(results);
        CraftItemEvent.RecipeKind kind = recipe == null ? CraftItemEvent.RecipeKind.OTHER
            : recipe instanceof ShapedRecipe ? CraftItemEvent.RecipeKind.SHAPED
            : recipe instanceof ShapelessRecipe ? CraftItemEvent.RecipeKind.SHAPELESS
            : CraftItemEvent.RecipeKind.OTHER;
        ItemRef before = result.isEmpty() ? null : ModRefs.item(result);
        PrepareItemCraftEvent event = ModEvents.post(new PrepareItemCraftEvent(ModRefs.player(serverPlayer), matrix, key, kind,
            recipe instanceof RepairItemRecipe, before));
        if (event.result() == before) {
            return result;
        }
        return event.result() == null ? ItemStack.EMPTY : ModRefs.item(event.result()).copy();
    }

    /** RETURN of AnvilMenu#createResult. */
    public static void preparedAnvil(AbstractContainerMenu menu, String renameText, int cost, IntConsumer costWriter) {
        if (!ModEvents.listening(PrepareAnvilEvent.class)) {
            return;
        }
        ServerPlayer player = viewer(menu);
        if (player == null) {
            return;
        }
        Slot resultSlot = menu.getSlot(2);
        ItemRef before = resultSlot.getItem().isEmpty() ? null : ModRefs.item(resultSlot.getItem());
        PrepareAnvilEvent event = ModEvents.post(new PrepareAnvilEvent(ModRefs.player(player), InventoryKind.ANVIL, inputs(menu, 2), before,
            renameText, cost));
        boolean changed = false;
        if (event.repairCost() != cost) {
            costWriter.accept(event.repairCost());
            changed = true;
        }
        if (event.result() != before) {
            resultSlot.set(event.result() == null ? ItemStack.EMPTY : ModRefs.item(event.result()).copy());
            changed = true;
        }
        if (changed) {
            menu.broadcastChanges();
        }
    }

    /** RETURN of the smithing, grindstone, cartography, loom and stonecutter result setup. */
    public static void preparedResult(AbstractContainerMenu menu) {
        if (!ModEvents.listening(PrepareResultEvent.class)) {
            return;
        }
        ServerPlayer player = viewer(menu);
        if (player == null) {
            return;
        }
        InventoryKind kind = ModEventMaps.kind(menu);
        int top = topSize(menu);
        if (top < 2) {
            return;
        }
        int inputCount = switch (kind) {
            case GRINDSTONE -> 2;
            case SMITHING -> 3;
            default -> top;
        };
        Slot resultSlot = menu.getSlot(top - 1);
        ItemRef before = resultSlot.getItem().isEmpty() ? null : ModRefs.item(resultSlot.getItem());
        PrepareResultEvent event = ModEvents.post(new PrepareResultEvent(ModRefs.player(player), kind, inputs(menu, inputCount), before));
        if (event.result() != before) {
            resultSlot.set(event.result() == null ? ItemStack.EMPTY : ModRefs.item(event.result()).copy());
            menu.broadcastChanges();
        }
    }

    private static List<ItemRef> inputs(AbstractContainerMenu menu, int count) {
        List<ItemRef> inputs = new ArrayList<>(count);
        for (int index = 0; index < count && index < menu.slots.size(); index++) {
            inputs.add(ModRefs.item(menu.getSlot(index).getItem().copy()));
        }
        return inputs;
    }

    /** After the enchanting table computed its three offers. Writes changed offers back and resends them. */
    public static void preparedEnchant(AbstractContainerMenu menu, Level level, BlockPos table, int[] costs, int[] enchantClue, int[] levelClue) {
        if (!(level instanceof ServerLevel serverLevel) || !ModEvents.listening(PrepareItemEnchantEvent.class)) {
            return;
        }
        ItemStack item = menu.getSlot(0).getItem();
        if (item.isEmpty()) {
            return;
        }
        ServerPlayer player = viewer(menu);
        if (player == null) {
            return;
        }
        int bonus = ContainerCompat.bookshelves(serverLevel, table);
        IdMap<Holder<Enchantment>> holders = ContainerCompat.enchantmentIds(serverLevel);
        List<EnchantOffer> offers = new ArrayList<>(3);
        List<EnchantOffer> original = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            Holder<Enchantment> enchantment = enchantClue[index] < 0 ? null : holders.byId(enchantClue[index]);
            Id id = enchantment == null ? null : ModConversions.enchantmentId(enchantment);
            if (costs[index] <= 0 || id == null) {
                offers.add(null);
                original.add(null);
            } else {
                offers.add(new EnchantOffer(id, levelClue[index], costs[index]));
                original.add(new EnchantOffer(id, levelClue[index], costs[index]));
            }
        }
        PrepareItemEnchantEvent event = ModEvents.post(new PrepareItemEnchantEvent(ModRefs.player(player), ModRefs.block(serverLevel, table),
            ModRefs.item(item.copy()), bonus, offers));
        if (event.isCancelled()) {
            for (int index = 0; index < 3; index++) {
                costs[index] = 0;
                enchantClue[index] = -1;
                levelClue[index] = -1;
            }
            menu.broadcastChanges();
            return;
        }
        boolean changed = false;
        for (int index = 0; index < 3 && index < event.offers().size(); index++) {
            EnchantOffer offer = event.offers().get(index);
            EnchantOffer before = original.get(index);
            if (offer == null || (before != null && offer.enchantment().equals(before.enchantment()) && offer.level() == before.level()
                && offer.cost() == before.cost())) {
                continue;
            }
            Holder<Enchantment> enchantment = ModConversions.enchantment(offer.enchantment());
            if (enchantment == null) {
                continue;
            }
            enchantClue[index] = holders.getId(enchantment);
            levelClue[index] = offer.level();
            costs[index] = offer.cost();
            changed = true;
        }
        if (changed) {
            menu.broadcastChanges();
        }
    }

    /** Before BrewingStandBlockEntity#doBrew: remembers the bottles and the ingredient. */
    public static void brewing(BrewingStandBlockEntity stand) {
        brewing = stand.getLevel() instanceof ServerLevel && ModEvents.listening(BrewEvent.class);
        if (!brewing) {
            return;
        }
        for (int slot = 0; slot < 4; slot++) {
            BREW_SNAPSHOT[slot] = stand.getItem(slot).copy();
        }
    }

    /** After BrewingStandBlockEntity#doBrew: posts the results, writes changes back or undoes a cancelled brew. */
    public static void brewed(BrewingStandBlockEntity stand, BlockPos pos, int fuel, int brewTime) {
        if (!brewing) {
            return;
        }
        brewing = false;
        ServerLevel level = (ServerLevel) stand.getLevel();
        boolean changed = false;
        for (int slot = 0; slot < 4; slot++) {
            if (!ItemStack.matches(BREW_SNAPSHOT[slot], stand.getItem(slot))) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return;
        }
        List<ItemRef> potions = new ArrayList<>(3);
        List<ItemRef> results = new ArrayList<>(3);
        for (int slot = 0; slot < 3; slot++) {
            potions.add(ModRefs.item(BREW_SNAPSHOT[slot]));
            results.add(ModRefs.item(stand.getItem(slot)));
        }
        List<ItemRef> before = List.copyOf(results);
        BrewEvent event = ModEvents.post(new BrewEvent(
            ModRefs.block(level, pos), ModRefs.item(BREW_SNAPSHOT[3]), potions, results, fuel, brewTime));
        if (event.isCancelled()) {
            for (int slot = 0; slot < 4; slot++) {
                stand.setItem(slot, BREW_SNAPSHOT[slot]);
            }
            return;
        }
        List<ItemRef> after = event.results();
        for (int slot = 0; slot < 3; slot++) {
            ItemRef ref = slot < after.size() ? after.get(slot) : null;
            if (slot < before.size() && ref == before.get(slot)) {
                continue;
            }
            stand.setItem(slot, ref == null ? ItemStack.EMPTY : ModRefs.item(ref).copy());
        }
    }
}
