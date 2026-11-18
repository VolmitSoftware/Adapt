package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEvent;
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
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.view.AnvilView;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.stack;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class InventoryGapTranslations {
  private InventoryGapTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(PREPARE_CRAFT, OPEN, PREPARE_RESULT, PREPARE_ENCHANT, DRAG, MOVE_ITEM, PICKUP_ITEM);
  }

  static final Translation<org.bukkit.event.inventory.PrepareItemCraftEvent> PREPARE_CRAFT = new Translation<>(
      org.bukkit.event.inventory.PrepareItemCraftEvent.class, List.of(PrepareItemCraftEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
      return event.getView().getPlayer() instanceof Player ? PrepareItemCraftEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.inventory.PrepareItemCraftEvent event, Poster poster) {
      CraftingInventory inventory = event.getInventory();
      Recipe recipe = event.getRecipe();
      ItemStack nativeResult = inventory.getResult();
      ItemRef result = nativeResult == null ? null : BukkitRefs.item(nativeResult);
      PrepareItemCraftEvent core = new PrepareItemCraftEvent(player((Player) event.getView().getPlayer()),
          GapEventMaps.items(inventory.getMatrix()), GapEventMaps.key(recipe), recipeKind(recipe), event.isRepair(), result);
      poster.post(core);
      if (core.result() != result) {
        inventory.setResult(resultStack(core.result()));
      }
    }
  };

  static final Translation<org.bukkit.event.inventory.InventoryOpenEvent> OPEN = new Translation<>(
      org.bukkit.event.inventory.InventoryOpenEvent.class, List.of(InventoryOpenEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.inventory.InventoryOpenEvent event) {
      return event.getPlayer() instanceof Player ? InventoryOpenEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.inventory.InventoryOpenEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      Inventory inventory = event.getInventory();
      InventoryOpenEvent core = new InventoryOpenEvent(player((Player) event.getPlayer()),
          BukkitEventMaps.inventoryKind(inventory.getType()), BukkitRefs.inventory(inventory),
          GapEventMaps.holderBlock(inventory), GapEventMaps.holderEntity(inventory));
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
    }
  };

  static final Translation<org.bukkit.event.inventory.PrepareInventoryResultEvent> PREPARE_RESULT = new Translation<>(
      org.bukkit.event.inventory.PrepareInventoryResultEvent.class, List.of(PrepareResultEvent.class, PrepareAnvilEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.inventory.PrepareInventoryResultEvent event) {
      if (!(event.getView().getPlayer() instanceof Player)) {
        return null;
      }
      return event instanceof org.bukkit.event.inventory.PrepareAnvilEvent ? PrepareAnvilEvent.class : PrepareResultEvent.class;
    }

    @Override
    public void bridge(org.bukkit.event.inventory.PrepareInventoryResultEvent event, Poster poster) {
      Inventory inventory = event.getInventory();
      ItemStack nativeResult = event.getResult();
      ItemRef result = nativeResult == null ? null : BukkitRefs.item(nativeResult);
      InventoryKind kind = BukkitEventMaps.inventoryKind(inventory.getType());
      List<ItemRef> inputs = inputs(inventory, kind);
      Player viewer = (Player) event.getView().getPlayer();
      if (event instanceof org.bukkit.event.inventory.PrepareAnvilEvent anvil) {
        AnvilView view = anvil.getView();
        int cost = view.getRepairCost();
        PrepareAnvilEvent core = new PrepareAnvilEvent(player(viewer), kind, inputs, result, view.getRenameText(), cost);
        poster.post(core);
        if (core.repairCost() != cost) {
          view.setRepairCost(core.repairCost());
        }
        writeResult(event, result, core);
        return;
      }
      PrepareResultEvent core = new PrepareResultEvent(player(viewer), kind, inputs, result);
      poster.post(core);
      writeResult(event, result, core);
    }
  };

  static final Translation<org.bukkit.event.enchantment.PrepareItemEnchantEvent> PREPARE_ENCHANT = BukkitTranslations.simple(
      org.bukkit.event.enchantment.PrepareItemEnchantEvent.class, PrepareItemEnchantEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EnchantmentOffer[] nativeOffers = event.getOffers();
        List<EnchantOffer> offers = new ArrayList<>(nativeOffers.length);
        for (EnchantmentOffer offer : nativeOffers) {
          offers.add(offer == null ? null
              : new EnchantOffer(BukkitRefs.id(offer.getEnchantment().getKey()), offer.getEnchantmentLevel(), offer.getCost()));
        }
        PrepareItemEnchantEvent core = new PrepareItemEnchantEvent(player(event.getEnchanter()), block(event.getEnchantBlock()),
            GapEventMaps.itemOrEmpty(event.getItem()), event.getEnchantmentBonus(), offers);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        for (int index = 0; index < nativeOffers.length && index < core.offers().size(); index++) {
          writeOffer(nativeOffers, index, core.offers().get(index));
        }
      });

  static final Translation<org.bukkit.event.inventory.InventoryDragEvent> DRAG = new Translation<>(
      org.bukkit.event.inventory.InventoryDragEvent.class, List.of(InventoryDragEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.inventory.InventoryDragEvent event) {
      return event.getWhoClicked() instanceof Player ? InventoryDragEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.inventory.InventoryDragEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      Inventory top = event.getView().getTopInventory();
      ItemStack cursor = event.getCursor();
      InventoryDragEvent core = new InventoryDragEvent(player((Player) event.getWhoClicked()),
          BukkitEventMaps.inventoryKind(top.getType()), top.getSize(), event.getRawSlots(),
          GapEventMaps.itemOrEmpty(event.getOldCursor()), cursor == null ? null : BukkitRefs.item(cursor));
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
    }
  };

  static final Translation<org.bukkit.event.inventory.InventoryMoveItemEvent> MOVE_ITEM = BukkitTranslations.simple(
      org.bukkit.event.inventory.InventoryMoveItemEvent.class, InventoryMoveItemEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        InventoryMoveItemEvent core = new InventoryMoveItemEvent(BukkitRefs.inventory(source),
            BukkitEventMaps.inventoryKind(source.getType()), GapEventMaps.holderBlock(source), BukkitRefs.inventory(destination),
            BukkitEventMaps.inventoryKind(destination.getType()), GapEventMaps.holderBlock(destination),
            GapEventMaps.itemOrEmpty(event.getItem()), event.getInitiator() == source);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.inventory.InventoryPickupItemEvent> PICKUP_ITEM = BukkitTranslations.simple(
      org.bukkit.event.inventory.InventoryPickupItemEvent.class, InventoryPickupItemEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Inventory inventory = event.getInventory();
        InventoryPickupItemEvent core = new InventoryPickupItemEvent(BukkitRefs.inventory(inventory),
            BukkitEventMaps.inventoryKind(inventory.getType()), GapEventMaps.holderBlock(inventory),
            BukkitRefs.itemEntity(event.getItem()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  private static CraftItemEvent.RecipeKind recipeKind(Recipe recipe) {
    if (recipe instanceof ShapedRecipe) {
      return CraftItemEvent.RecipeKind.SHAPED;
    }
    return recipe instanceof ShapelessRecipe ? CraftItemEvent.RecipeKind.SHAPELESS : CraftItemEvent.RecipeKind.OTHER;
  }

  private static List<ItemRef> inputs(Inventory inventory, InventoryKind kind) {
    int count = switch (kind) {
      case ANVIL, GRINDSTONE -> 2;
      case SMITHING -> 3;
      default -> inventory.getSize();
    };
    List<ItemRef> inputs = new ArrayList<>(count);
    for (int slot = 0; slot < count && slot < inventory.getSize(); slot++) {
      inputs.add(GapEventMaps.itemOrEmpty(inventory.getItem(slot)));
    }
    return inputs;
  }

  private static void writeResult(org.bukkit.event.inventory.PrepareInventoryResultEvent event, ItemRef before,
                                  PrepareResultEvent core) {
    if (core.result() != before) {
      event.setResult(resultStack(core.result()));
    }
  }

  private static ItemStack resultStack(ItemRef result) {
    return result == null || result.isEmpty() ? null : stack(result);
  }

  private static void writeOffer(EnchantmentOffer[] nativeOffers, int index, EnchantOffer offer) {
    EnchantmentOffer current = nativeOffers[index];
    if (offer == null) {
      return;
    }
    Id currentKey = current == null ? null : BukkitRefs.id(current.getEnchantment().getKey());
    if (current != null && offer.enchantment().equals(currentKey) && offer.level() == current.getEnchantmentLevel()
        && offer.cost() == current.getCost()) {
      return;
    }
    if (current == null || !offer.enchantment().equals(currentKey)) {
      Enchantment enchantment = Registry.ENCHANTMENT.get(BukkitRefs.key(offer.enchantment()));
      if (enchantment == null) {
        return;
      }
      if (current == null) {
        nativeOffers[index] = new EnchantmentOffer(enchantment, offer.level(), offer.cost());
        return;
      }
      current.setEnchantment(enchantment);
    }
    current.setEnchantmentLevel(offer.level());
    current.setCost(offer.cost());
  }
}
