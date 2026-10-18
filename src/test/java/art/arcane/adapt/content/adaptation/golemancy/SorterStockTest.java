package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SorterStockTest {
  private final SorterItems items = new SorterItems();

  @Test
  void aSnapshotCountsSimilarStacksSlotsAndEmptySlots() {
    Inventory inventory = items.inventory(9, items.item(Material.COBBLESTONE, 64), items.item(Material.DIRT, 5),
        items.item(Material.COBBLESTONE, 10), items.item(Material.ENCHANTED_BOOK, "mending", 1), items.item(Material.ENCHANTED_BOOK, "sharpness", 1));

    SorterStock stock = SorterStock.read(inventory, 1_000L);

    assertThat(stock.capturedAt()).isEqualTo(1_000L);
    assertThat(stock.entries()).extracting(entry -> entry.prototype().getType() + " " + entry.count() + "/" + entry.slots())
        .containsExactly("COBBLESTONE 74/2", "DIRT 5/1", "ENCHANTED_BOOK 1/1", "ENCHANTED_BOOK 1/1");
    assertThat(stock.entries()).allMatch(entry -> entry.prototype().getAmount() == 1);
    assertThat(stock.count(item -> item.getType() == Material.ENCHANTED_BOOK)).isEqualTo(2);
    assertThat(stock.count(item -> item.getType() == Material.STONE)).isZero();
  }

  @Test
  void roomCountsEmptySlotsAndThePartialStacksOfSimilarItems() {
    Inventory inventory = items.inventory(5, items.item(Material.COBBLESTONE, 64), items.item(Material.COBBLESTONE, 64),
        items.item(Material.COBBLESTONE, 10), items.item(Material.DIRT, 60));

    SorterStock stock = SorterStock.read(inventory, 0L);

    assertThat(stock.room(items.item(Material.COBBLESTONE, 1))).isEqualTo(64 + 54);
    assertThat(stock.room(items.item(Material.DIRT, 1))).isEqualTo(64 + 4);
    assertThat(stock.room(items.item(Material.STONE, 1))).isEqualTo(64);
  }

  @Test
  void aFullContainerHasNoRoomForNewItems() {
    ItemStack[] full = new ItemStack[3];
    for (int i = 0; i < full.length; i++) {
      full[i] = items.item(Material.DIRT, 64);
    }
    SorterStock stock = SorterStock.read(items.inventory(3, full), 0L);

    assertThat(stock.room(items.item(Material.DIRT, 1))).isZero();
    assertThat(stock.room(items.item(Material.STONE, 1))).isZero();
  }
}
