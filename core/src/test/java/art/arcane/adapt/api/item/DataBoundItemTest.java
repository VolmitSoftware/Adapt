package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataBoundItemTest {
  private static final DataKey<String> FOREIGN = DataKey.adapt("foreign_tag", DataCodecs.STRING);

  @Test
  void setDataReplacesEverythingOnTheStackLikeTheBukkitDataItem() {
    FakeItem stack = new FakeItem(Id.minecraft("bundle"), 1);
    stack.setDisplayName("Renamed");
    stack.setEnchantment(Id.minecraft("unbreaking"), 2);
    stack.data().set(FOREIGN, "kept from another plugin");

    Tagged.IO.setData(stack, new Tagged.Data("first"));

    assertThat(stack.displayName()).isEqualTo("Tagged");
    assertThat(stack.lore()).containsExactly("first");
    assertThat(stack.enchantmentLevel(Id.minecraft("unbreaking"))).isZero();
    assertThat(stack.data().has(FOREIGN)).isFalse();
    assertThat(Tagged.IO.getData(stack).label()).isEqualTo("first");
    assertThat(stack.amount()).isEqualTo(1);
  }

  private static final class Tagged implements DataBoundItem<Tagged.Data> {
    static final Tagged IO = new Tagged();

    record Data(String label) {
    }

    @Override
    public Id getMaterial() {
      return Id.minecraft("bundle");
    }

    @Override
    public Class<Data> getType() {
      return Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
      lore.add(data.label());
    }

    @Override
    public void applyMeta(Data data, ItemRef item) {
      item.setDisplayName("Tagged");
    }
  }
}
