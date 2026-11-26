package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.item.AdaptItemGuard;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryPickupItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Golemancy's own item rules on top of {@link AdaptItemGuard}: Golemancy recipes take exactly the golem items their
 * book asks for, a trade may take a golem item only when it asks for that golem item, hoppers never pick up
 * owner-bound items, and a dormant golem placement nobody handled is cancelled.
 */
public final class GolemItemGuard {
  public static final String RECIPE_NAMESPACE = "adapt";
  public static final String RECIPE_PREFIX = "golemancy-";
  private final ExactIngredientBook recipes;

  public GolemItemGuard(ExactIngredientBook recipes) {
    this.recipes = Objects.requireNonNull(recipes);
  }

  public static boolean isGolemancyRecipe(Id key) {
    return key != null && RECIPE_NAMESPACE.equals(key.namespace()) && key.path().startsWith(RECIPE_PREFIX);
  }

  public boolean blocksRecipe(Id recipeKey, List<ItemRef> golemItems) {
    if (!isGolemancyRecipe(recipeKey)) {
      return !golemItems.isEmpty();
    }
    return !recipes.satisfiedBy(recipeKey, golemItems);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void placed(BlockPlaceEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    if (GolemItems.kindOf(e.itemInHand()).filter((GolemItemKind kind) -> kind == GolemItemKind.DORMANT_GOLEM).isPresent()) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareItemCraftEvent e) {
    if (blocksRecipe(e.recipeKey(), golemItems(e.matrix()))) {
      e.setResult(empty());
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(CrafterCraftEvent e) {
    InventoryRef crafter = e.block().container();
    if (crafter != null && blocksRecipe(e.recipeKey(), golemItems(crafter.contents()))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(InventoryClickEvent e) {
    if (e.slotKind() == SlotKind.RESULT && e.viewKind() == InventoryKind.MERCHANT
        && tradeSwallowsGolemItem(e.topItems(), e.selectedTradeCost())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(InventoryPickupItemEvent e) {
    ItemRef item = e.item().item();
    if (!GolemItems.mayBeOwnerBound(item.type())) {
      return;
    }
    if (GolemItems.kindOf(item).filter(GolemItemGuard::ownerBound).isPresent()) {
      e.setCancelled(true);
    }
  }

  private static boolean ownerBound(GolemItemKind kind) {
    return kind == GolemItemKind.SHATTERED_CORE || kind == GolemItemKind.DORMANT_GOLEM;
  }

  private static List<ItemRef> golemItems(List<ItemRef> items) {
    List<ItemRef> golemItems = new ArrayList<>();
    if (items == null) {
      return golemItems;
    }
    for (ItemRef item : items) {
      if (GolemItems.isGolemItem(item)) {
        golemItems.add(item);
      }
    }
    return golemItems;
  }

  private static boolean tradeSwallowsGolemItem(List<ItemRef> top, List<ItemRef> tradeIngredients) {
    List<ItemRef> offered = new ArrayList<>(2);
    for (int slot = 0; slot < Math.min(2, top.size()); slot++) {
      offered.add(top.get(slot));
    }
    return golemItems(offered).size() > golemItems(tradeIngredients).size();
  }

  private static ItemRef empty() {
    return AdaptCore.platform().host().items().empty();
  }
}
