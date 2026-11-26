package art.arcane.adapt.api.item;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockCookEvent;
import art.arcane.adapt.platform.event.block.BlockDispenseEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one rule for every item Adapt makes ({@link AdaptItems}): no vanilla interaction places, spends or transforms it
 * unless its definition declares that interaction. Player and off-hand placement, item use, eating, dispensers,
 * furnaces and hoppers into them, crafting grids and crafters, workstations, blocks that take the held item, item
 * frames, armor stands, allays and trades are all refused; the stack stays where it was. Protection probes pass.
 */
public final class AdaptItemGuard {
  private static final Id DROPPER = Id.minecraft("dropper");
  private static final Set<Id> TAKES_HELD_ITEM = Set.of(Id.minecraft("composter"), Id.minecraft("flower_pot"),
      Id.minecraft("lectern"), Id.minecraft("chiseled_bookshelf"), Id.minecraft("decorated_pot"),
      Id.minecraft("campfire"), Id.minecraft("soul_campfire"), Id.minecraft("cauldron"),
      Id.minecraft("water_cauldron"), Id.minecraft("jukebox"));
  private static final Set<Id> TAKES_HAND_ENTITY = Set.of(Id.minecraft("item_frame"), Id.minecraft("glow_item_frame"),
      Id.minecraft("armor_stand"), Id.minecraft("allay"));
  private static final Set<InventoryKind> FURNACES = Set.of(InventoryKind.FURNACE, InventoryKind.SMOKER,
      InventoryKind.BLAST_FURNACE);
  private static final Map<InventoryKind, Integer> RESULT_ON_TAKE = Map.of(InventoryKind.STONECUTTER, 1,
      InventoryKind.CARTOGRAPHY, 2, InventoryKind.LOOM, 3);
  private static final Set<InventoryAction> PUTS_CURSOR = Set.of(InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME,
      InventoryAction.PLACE_ONE, InventoryAction.SWAP_WITH_CURSOR);
  private static final int SMITHING_INPUTS = 3;
  private static final int TWO_INPUTS = 2;

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (e.protectionProbe() || (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK)) {
      return;
    }
    ItemRef item = e.item();
    if (AdaptItems.refuses(item, VanillaUse.USE)) {
      e.setUseItem(UseResult.DENY);
    }
    BlockRef clicked = e.clickedBlock();
    if (takesHeldItem(clicked) && AdaptItems.refuses(item, VanillaUse.INSERT)) {
      e.setUseBlock(UseResult.DENY);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEntityEvent e) {
    Id clicked = e.clicked() == null ? null : e.clicked().type();
    if (e.protectionProbe() || clicked == null || !TAKES_HAND_ENTITY.contains(clicked)) {
      return;
    }
    ItemRef item = e.player().inventory().hand(e.hand() == null ? Hand.MAIN_HAND : e.hand());
    if (AdaptItems.refuses(item, VanillaUse.ENTITY)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockPlaceEvent e) {
    if (!e.protectionProbe() && AdaptItems.refuses(e.itemInHand(), VanillaUse.PLACE)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerItemConsumeEvent e) {
    if (AdaptItems.refuses(e.item(), VanillaUse.CONSUME)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockDispenseEvent e) {
    if (!DROPPER.equals(e.block().type()) && AdaptItems.refuses(e.item(), VanillaUse.DISPENSE)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockCookEvent e) {
    if (AdaptItems.refuses(e.source(), VanillaUse.FURNACE)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(InventoryMoveItemEvent e) {
    if (FURNACES.contains(e.destinationKind())) {
      if (AdaptItems.refuses(e.item(), VanillaUse.FURNACE)) {
        e.setCancelled(true);
      }
    } else if (e.destinationKind() == InventoryKind.BREWING) {
      if (AdaptItems.refuses(e.item(), VanillaUse.WORKSTATION)) {
        e.setCancelled(true);
      }
    } else if (takesHeldItem(e.destinationBlock()) && AdaptItems.refuses(e.item(), VanillaUse.INSERT)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareItemCraftEvent e) {
    if (AdaptItems.refusedBy(e.recipeKey(), e.matrix())) {
      e.setResult(empty());
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(CrafterCraftEvent e) {
    InventoryRef crafter = e.block().container();
    if (crafter != null && AdaptItems.refusedBy(e.recipeKey(), crafter.contents())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareResultEvent e) {
    if (e.kind() == InventoryKind.SMITHING) {
      clearResult(e, SMITHING_INPUTS);
    } else if (e.kind() == InventoryKind.GRINDSTONE) {
      clearResult(e, TWO_INPUTS);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareAnvilEvent e) {
    clearResult(e, TWO_INPUTS);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareItemEnchantEvent e) {
    if (AdaptItems.refuses(e.item(), VanillaUse.WORKSTATION)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EnchantItemEvent e) {
    if (AdaptItems.refuses(e.item(), VanillaUse.WORKSTATION)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BrewEvent e) {
    if (AdaptItems.refuses(e.ingredient(), VanillaUse.WORKSTATION) || AdaptItems.anyRefuses(e.potions(), VanillaUse.WORKSTATION)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(InventoryClickEvent e) {
    InventoryKind view = e.viewKind();
    List<ItemRef> top = e.topItems();
    if (e.slotKind() == SlotKind.RESULT) {
      Integer inputSlots = RESULT_ON_TAKE.get(view);
      if (inputSlots != null && AdaptItems.anyRefuses(inputs(top, inputSlots), VanillaUse.WORKSTATION)) {
        e.setCancelled(true);
      } else if (view == InventoryKind.MERCHANT && AdaptItems.anyRefuses(inputs(top, TWO_INPUTS), VanillaUse.TRADE)) {
        e.setCancelled(true);
      }
      return;
    }
    VanillaUse sink = FURNACES.contains(view) ? VanillaUse.FURNACE : view == InventoryKind.BREWING ? VanillaUse.WORKSTATION : null;
    if (sink != null && AdaptItems.refuses(entering(e), sink)) {
      e.setCancelled(true);
    }
  }

  /** The stack a click would move into the top inventory of the view, or null when it moves nothing there. */
  private static ItemRef entering(InventoryClickEvent e) {
    boolean top = e.clickedKind() != InventoryKind.PLAYER;
    InventoryAction action = e.action();
    if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
      return top ? null : e.currentItem();
    }
    if (!top) {
      return null;
    }
    if (PUTS_CURSOR.contains(action)) {
      return e.cursor();
    }
    if (action == InventoryAction.HOTBAR_SWAP && e.hotbarButton() >= 0) {
      PlayerRef player = e.player();
      return player.inventory().item(e.hotbarButton());
    }
    return null;
  }

  private static boolean takesHeldItem(BlockRef block) {
    Id type = block == null ? null : block.type();
    return type != null && TAKES_HELD_ITEM.contains(type);
  }

  private static List<ItemRef> inputs(List<ItemRef> top, int count) {
    return top.subList(0, Math.min(count, top.size()));
  }

  private static void clearResult(PrepareResultEvent e, int inputSlots) {
    if (AdaptItems.anyRefuses(inputs(e.inputs(), inputSlots), VanillaUse.WORKSTATION)) {
      e.setResult(empty());
    }
  }

  private static ItemRef empty() {
    return AdaptCore.platform().host().items().empty();
  }
}
