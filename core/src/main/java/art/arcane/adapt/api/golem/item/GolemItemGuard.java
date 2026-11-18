package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockCookEvent;
import art.arcane.adapt.platform.event.block.BlockDispenseEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryPickupItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GolemItemGuard {
  public static final String RECIPE_NAMESPACE = "adapt";
  public static final String RECIPE_PREFIX = "golemancy-";
  private static final int SMITHING_INPUTS = 3;
  private static final int ANVIL_INPUTS = 2;
  private static final int GRINDSTONE_INPUTS = 2;
  private static final int STONECUTTER_INPUT_SLOT = 0;
  private static final Id DROPPER = Id.minecraft("dropper");
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

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (e.protectionProbe() || (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR)) {
      return;
    }
    Optional<GolemItemKind> kind = GolemItems.kindOf(e.item());
    if (kind.isPresent() && kind.get() != GolemItemKind.DORMANT_GOLEM) {
      e.setUseItem(UseResult.DENY);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockPlaceEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    Optional<GolemItemKind> kind = GolemItems.kindOf(e.itemInHand());
    if (kind.isPresent() && kind.get() != GolemItemKind.DORMANT_GOLEM) {
      e.setCancelled(true);
    }
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
  public void on(PrepareResultEvent e) {
    if (e.kind() == InventoryKind.SMITHING) {
      clearResultForGolemInputs(e, SMITHING_INPUTS);
    } else if (e.kind() == InventoryKind.GRINDSTONE) {
      clearResultForGolemInputs(e, GRINDSTONE_INPUTS);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareAnvilEvent e) {
    clearResultForGolemInputs(e, ANVIL_INPUTS);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockCookEvent e) {
    if (GolemItems.isGolemItem(e.source())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(InventoryClickEvent e) {
    if (e.slotKind() != SlotKind.RESULT) {
      return;
    }
    List<ItemRef> top = e.topItems();
    if (e.viewKind() == InventoryKind.STONECUTTER && top.size() > STONECUTTER_INPUT_SLOT
        && GolemItems.isGolemItem(top.get(STONECUTTER_INPUT_SLOT))) {
      e.setCancelled(true);
    } else if (e.viewKind() == InventoryKind.MERCHANT && tradeSwallowsGolemItem(top, e.selectedTradeCost())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockDispenseEvent e) {
    if (!DROPPER.equals(e.block().type()) && GolemItems.isGolemItem(e.item())) {
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

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareItemEnchantEvent e) {
    if (GolemItems.isGolemItem(e.item())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EnchantItemEvent e) {
    if (GolemItems.isGolemItem(e.item())) {
      e.setCancelled(true);
    }
  }

  private static boolean ownerBound(GolemItemKind kind) {
    return kind == GolemItemKind.SHATTERED_CORE || kind == GolemItemKind.DORMANT_GOLEM;
  }

  private static void clearResultForGolemInputs(PrepareResultEvent e, int inputSlots) {
    List<ItemRef> inputs = e.inputs();
    for (int slot = 0; slot < Math.min(inputSlots, inputs.size()); slot++) {
      if (GolemItems.isGolemItem(inputs.get(slot))) {
        e.setResult(empty());
        return;
      }
    }
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
