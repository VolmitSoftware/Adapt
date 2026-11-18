package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.inventory.InventoryDragEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryGapTranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;
  private Player player;

  @BeforeEach
  void setUp() {
    refs = new GapRefs();
    player = mock(Player.class);
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void prepareCraftClearsResultWhenHandlerRemovesIt() {
    CraftingInventory grid = mock(CraftingInventory.class);
    InventoryView view = mock(InventoryView.class);
    when(view.getPlayer()).thenReturn(player);
    when(view.getTopInventory()).thenReturn(grid);
    ItemStack cell = mock(ItemStack.class);
    when(grid.getMatrix()).thenReturn(new ItemStack[] {cell, cell});
    when(grid.getResult()).thenReturn(mock(ItemStack.class));
    org.bukkit.event.inventory.PrepareItemCraftEvent event = new org.bukkit.event.inventory.PrepareItemCraftEvent(grid, view, false);
    AtomicReference<PrepareItemCraftEvent> seen = new AtomicReference<>();
    bus.register(this, PrepareItemCraftEvent.class, EventPriority.HIGHEST, core -> {
      seen.set(core);
      core.setResult(null);
    });

    assertThat(InventoryGapTranslations.PREPARE_CRAFT.target(event)).isEqualTo(PrepareItemCraftEvent.class);
    InventoryGapTranslations.PREPARE_CRAFT.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(seen.get().player()).isSameAs(refs.wrap(player, PlayerRef.class));
    assertThat(seen.get().matrix()).hasSize(2);
    verify(grid).setResult(null);
  }

  @Test
  void anvilRepairCostAndResultAreWrittenBack() {
    AnvilView view = mock(AnvilView.class);
    AnvilInventory anvil = mock(AnvilInventory.class);
    when(view.getPlayer()).thenReturn(player);
    when(view.getTopInventory()).thenReturn(anvil);
    when(view.getRepairCost()).thenReturn(9);
    when(view.getRenameText()).thenReturn("Heirloom");
    ItemStack result = mock(ItemStack.class);
    ItemStack marked = mock(ItemStack.class);
    org.bukkit.event.inventory.PrepareAnvilEvent event = new org.bukkit.event.inventory.PrepareAnvilEvent(view, result);
    AtomicReference<PrepareResultEvent> seen = new AtomicReference<>();
    bus.register(this, PrepareResultEvent.class, EventPriority.HIGH, core -> {
      seen.set(core);
      PrepareAnvilEvent anvilEvent = (PrepareAnvilEvent) core;
      anvilEvent.setRepairCost(4);
      anvilEvent.setResult(refs.wrap(marked, ItemRef.class));
    });

    assertThat(InventoryGapTranslations.PREPARE_RESULT.target(event)).isEqualTo(PrepareAnvilEvent.class);
    InventoryGapTranslations.PREPARE_RESULT.bridge(event, core -> bus.post(core, EventPriority.HIGH));

    assertThat(((PrepareAnvilEvent) seen.get()).renameText()).isEqualTo("Heirloom");
    verify(view).setRepairCost(4);
    assertThat(event.getResult()).isSameAs(marked);
  }

  @Test
  void dragReportsRawSlotsAndWritesBackCancel() {
    InventoryView view = mock(InventoryView.class);
    org.bukkit.inventory.Inventory top = mock(org.bukkit.inventory.Inventory.class);
    when(view.getPlayer()).thenReturn(player);
    when(view.getTopInventory()).thenReturn(top);
    when(top.getSize()).thenReturn(27);
    ItemStack cursor = mock(ItemStack.class);
    org.bukkit.event.inventory.InventoryDragEvent event = mock(org.bukkit.event.inventory.InventoryDragEvent.class);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getView()).thenReturn(view);
    when(event.getRawSlots()).thenReturn(Set.of(3, 30));
    when(event.getOldCursor()).thenReturn(cursor);
    AtomicReference<InventoryDragEvent> seen = new AtomicReference<>();
    bus.register(this, InventoryDragEvent.class, EventPriority.HIGH, core -> {
      seen.set(core);
      core.setCancelled(true);
    });

    InventoryGapTranslations.DRAG.bridge(event, core -> bus.post(core, EventPriority.HIGH));

    assertThat(seen.get().topSize()).isEqualTo(27);
    assertThat(seen.get().rawSlots()).containsExactlyInAnyOrder(3, 30);
    assertThat(seen.get().oldCursor()).isSameAs(refs.wrap(cursor, ItemRef.class));
    verify(event).setCancelled(true);
  }

  @Test
  void untouchedPrepareCraftLeavesResultAlone() {
    CraftingInventory grid = mock(CraftingInventory.class);
    InventoryView view = mock(InventoryView.class);
    when(view.getPlayer()).thenReturn(player);
    when(view.getTopInventory()).thenReturn(grid);
    when(grid.getMatrix()).thenReturn(new ItemStack[0]);
    org.bukkit.event.inventory.PrepareItemCraftEvent event = new org.bukkit.event.inventory.PrepareItemCraftEvent(grid, view, false);
    bus.register(this, PrepareItemCraftEvent.class, EventPriority.MONITOR, core -> {
    });

    InventoryGapTranslations.PREPARE_CRAFT.bridge(event, core -> bus.post(core, EventPriority.MONITOR));

    verify(grid, never()).setResult(null);
  }
}
