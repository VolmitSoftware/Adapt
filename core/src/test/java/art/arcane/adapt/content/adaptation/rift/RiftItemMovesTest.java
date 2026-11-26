package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Anchor sends move whole stacks and hand back exactly what did not fit: no item is made or lost. */
class RiftItemMovesTest {
  private static final Id STONE = Id.minecraft("stone");

  @Test
  void takingStacksMovesWholeStacksUpToTheCount() {
    FakeInventory source = new FakeInventory();
    for (int stack = 0; stack < 5; stack++) {
      source.addItem(new FakeItem(STONE, 64));
    }

    List<ItemRef> taken = RiftItemMoves.takeStacks(source, 3);

    assertThat(taken).hasSize(3);
    assertThat(RiftItemMoves.total(taken)).isEqualTo(3 * 64);
    assertThat(RiftItemMoves.stacks(source)).isEqualTo(2);
  }

  @Test
  void whatDoesNotFitComesBackAndTheTotalNeverChanges() {
    FakeInventory source = new FakeInventory();
    FakeInventory target = new FakeInventory();
    for (int slot = 0; slot < 35; slot++) {
      target.setItem(slot, new FakeItem(Id.minecraft("dirt"), 64));
    }
    for (int stack = 0; stack < 4; stack++) {
      source.addItem(new FakeItem(STONE, 64));
    }

    List<ItemRef> moving = RiftItemMoves.takeStacks(source, 4);
    List<ItemRef> left = RiftItemMoves.put(target, moving);
    List<ItemRef> back = RiftItemMoves.put(source, left);

    int inTarget = 0;
    for (ItemRef stack : target.contents()) {
      if (stack != null && stack.type().equals(STONE)) {
        inTarget += stack.amount();
      }
    }
    int inSource = 0;
    for (ItemRef stack : source.contents()) {
      if (stack != null && stack.type().equals(STONE)) {
        inSource += stack.amount();
      }
    }
    assertThat(back).isEmpty();
    assertThat(inTarget + inSource).isEqualTo(4 * 64);
    assertThat(inTarget).isPositive();
  }
}
