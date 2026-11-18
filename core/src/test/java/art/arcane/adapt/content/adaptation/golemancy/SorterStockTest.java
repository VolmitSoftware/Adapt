package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SorterStockTest {
  private static final Id COBBLESTONE = Id.minecraft("cobblestone");
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id STONE = Id.minecraft("stone");
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");

  private final SorterItems items = new SorterItems();

  @Test
  void aSnapshotCountsSimilarStacksSlotsAndEmptySlots() {
    InventoryRef inventory = items.inventory(9, items.item(COBBLESTONE, 64), items.item(DIRT, 5),
        items.item(COBBLESTONE, 10), items.item(ENCHANTED_BOOK, "mending", 1), items.item(ENCHANTED_BOOK, "sharpness", 1));

    SorterStock stock = SorterStock.read(inventory, 1_000L);

    assertThat(stock.capturedAt()).isEqualTo(1_000L);
    assertThat(stock.entries()).extracting(entry -> entry.prototype().type().legacyName() + " " + entry.count() + "/" + entry.slots())
        .containsExactly("COBBLESTONE 74/2", "DIRT 5/1", "ENCHANTED_BOOK 1/1", "ENCHANTED_BOOK 1/1");
    assertThat(stock.entries()).allMatch(entry -> entry.prototype().amount() == 1);
    assertThat(stock.count(item -> item.type().equals(ENCHANTED_BOOK))).isEqualTo(2);
    assertThat(stock.count(item -> item.type().equals(STONE))).isZero();
  }

  @Test
  void roomCountsEmptySlotsAndThePartialStacksOfSimilarItems() {
    InventoryRef inventory = items.inventory(5, items.item(COBBLESTONE, 64), items.item(COBBLESTONE, 64),
        items.item(COBBLESTONE, 10), items.item(DIRT, 60));

    SorterStock stock = SorterStock.read(inventory, 0L);

    assertThat(stock.room(items.item(COBBLESTONE, 1))).isEqualTo(64 + 54);
    assertThat(stock.room(items.item(DIRT, 1))).isEqualTo(64 + 4);
    assertThat(stock.room(items.item(STONE, 1))).isEqualTo(64);
  }

  @Test
  void aFullContainerHasNoRoomForNewItems() {
    ItemRef[] full = new ItemRef[3];
    for (int i = 0; i < full.length; i++) {
      full[i] = items.item(DIRT, 64);
    }
    SorterStock stock = SorterStock.read(items.inventory(3, full), 0L);

    assertThat(stock.room(items.item(DIRT, 1))).isZero();
    assertThat(stock.room(items.item(STONE, 1))).isZero();
  }
}
