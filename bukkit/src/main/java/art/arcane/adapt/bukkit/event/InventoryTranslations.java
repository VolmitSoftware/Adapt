package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.item;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.stack;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class InventoryTranslations {
  private InventoryTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(CLICK, CLOSE, ENCHANT);
  }

  static final Translation<org.bukkit.event.inventory.InventoryClickEvent> CLICK = new Translation<>(
      org.bukkit.event.inventory.InventoryClickEvent.class, List.of(InventoryClickEvent.class, CraftItemEvent.class)) {
    @Override
    public Class<? extends InventoryClickEvent> target(org.bukkit.event.inventory.InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player)) {
        return null;
      }
      return event instanceof org.bukkit.event.inventory.CraftItemEvent ? CraftItemEvent.class : InventoryClickEvent.class;
    }

    @Override
    public void bridge(org.bukkit.event.inventory.InventoryClickEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      ItemRef current = item(event.getCurrentItem());
      ItemRef cursor = item(event.getCursor());
      InventoryKind viewKind = BukkitEventMaps.inventoryKind(event.getView().getTopInventory().getType());
      Inventory clicked = event.getClickedInventory();
      InventoryClickEvent core;
      if (event instanceof org.bukkit.event.inventory.CraftItemEvent craft) {
        Recipe recipe = craft.getRecipe();
        core = new CraftItemEvent(player((Player) event.getWhoClicked()), viewKind, event.getSlot(), event.getRawSlot(),
            BukkitEventMaps.clickKind(event.getClick()), BukkitEventMaps.inventoryAction(event.getAction()),
            event.getHotbarButton(), current, cursor, recipeKey(recipe), recipeKind(recipe), matrix(craft.getInventory().getMatrix()));
      } else {
        core = new InventoryClickEvent(player((Player) event.getWhoClicked()), viewKind,
            clicked == null ? null : BukkitEventMaps.inventoryKind(clicked.getType()), event.getSlot(), event.getRawSlot(),
            BukkitEventMaps.slotKind(event.getSlotType()), BukkitEventMaps.clickKind(event.getClick()),
            BukkitEventMaps.inventoryAction(event.getAction()), event.getHotbarButton(), current, cursor);
      }
      if (viewKind == InventoryKind.STONECUTTER || viewKind == InventoryKind.MERCHANT) {
        Inventory top = event.getView().getTopInventory();
        MerchantRecipe trade = top instanceof MerchantInventory merchant ? merchant.getSelectedRecipe() : null;
        core.setViewContents(matrix(top.getContents()), trade == null ? List.of() : matrix(trade.getIngredients().toArray(new ItemStack[0])));
      }
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (core.currentItem() != current) {
        event.setCurrentItem(stack(core.currentItem()));
      }
      if (core.cursor() != cursor) {
        writeCursor(event, stack(core.cursor()));
      }
    }
  };

  @SuppressWarnings("deprecation")
  private static void writeCursor(org.bukkit.event.inventory.InventoryClickEvent event, ItemStack cursor) {
    event.setCursor(cursor);
  }

  private static Id recipeKey(Recipe recipe) {
    return recipe instanceof Keyed keyed ? BukkitRefs.id(keyed.getKey()) : null;
  }

  private static CraftItemEvent.RecipeKind recipeKind(Recipe recipe) {
    if (recipe instanceof ShapedRecipe) {
      return CraftItemEvent.RecipeKind.SHAPED;
    }
    return recipe instanceof ShapelessRecipe ? CraftItemEvent.RecipeKind.SHAPELESS : CraftItemEvent.RecipeKind.OTHER;
  }

  private static List<ItemRef> matrix(ItemStack[] matrix) {
    List<ItemRef> cells = new ArrayList<>(matrix.length);
    for (ItemStack cell : matrix) {
      cells.add(BukkitRefs.item(cell == null ? PaperApi.emptyItem() : cell));
    }
    return cells;
  }

  static final Translation<org.bukkit.event.inventory.InventoryCloseEvent> CLOSE = new Translation<>(
      org.bukkit.event.inventory.InventoryCloseEvent.class, List.of(InventoryCloseEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.inventory.InventoryCloseEvent event) {
      return event.getPlayer() instanceof Player ? InventoryCloseEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.inventory.InventoryCloseEvent event, Poster poster) {
      poster.post(new InventoryCloseEvent(player((Player) event.getPlayer()),
          BukkitEventMaps.inventoryKind(event.getView().getTopInventory().getType())));
    }
  };

  @SuppressWarnings("deprecation")
  static final Translation<org.bukkit.event.enchantment.EnchantItemEvent> ENCHANT = BukkitTranslations.simple(
      org.bukkit.event.enchantment.EnchantItemEvent.class, EnchantItemEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        int cost = event.getExpLevelCost();
        Map<Id, Integer> enchants = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
          enchants.put(BukkitRefs.id(entry.getKey().getKey()), entry.getValue());
        }
        EnchantItemEvent core = new EnchantItemEvent(player(event.getEnchanter()), block(event.getEnchantBlock()),
            item(event.getItem()), event.whichButton(), enchants, cost);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.expLevelCost() != cost) {
          event.setExpLevelCost(core.expLevelCost());
        }
        if (!core.enchantsToAdd().equals(enchants)) {
          Map<Enchantment, Integer> target = event.getEnchantsToAdd();
          target.clear();
          for (Map.Entry<Id, Integer> entry : core.enchantsToAdd().entrySet()) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(BukkitRefs.key(entry.getKey()));
            if (enchantment != null && entry.getValue() != null && entry.getValue() > 0) {
              target.put(enchantment, entry.getValue());
            }
          }
        }
      });
}
