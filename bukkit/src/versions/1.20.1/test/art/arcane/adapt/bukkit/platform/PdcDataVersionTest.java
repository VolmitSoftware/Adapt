package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.platform.data.PdcDataHolder;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Paper 1.20.1 containers have no list type, and item stacks expose their data only through the item meta. */
class PdcDataVersionTest {
  private static final DataKey<Integer> LEVEL = DataKey.adapt("level", DataCodecs.INT);

  @Test
  void stringListsAreStoredAsOneJsonArrayString() {
    FakeDataContainer container = new FakeDataContainer();
    DataHolder data = PdcDataHolder.live(container);

    data.set(DataKey.adapt("lore", DataCodecs.STRING_LIST), List.of("first", "second"));

    assertThat(data.get(DataKey.adapt("lore", DataCodecs.STRING_LIST))).containsExactly("first", "second");
    assertThat(container.get(new NamespacedKey("adapt", "lore"), PersistentDataType.STRING)).isEqualTo("[\"first\",\"second\"]");
  }

  @Test
  void itemDataWritesBackThroughItemMeta() {
    FakeDataContainer container = new FakeDataContainer();
    ItemMeta meta = mock(ItemMeta.class);
    when(meta.getPersistentDataContainer()).thenReturn(container);
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(Material.DIAMOND_SWORD);
    when(stack.getItemMeta()).thenReturn(meta);

    DataHolder data = BukkitRefs.item(stack).data();
    data.set(LEVEL, 4);

    verify(stack).setItemMeta(meta);
    assertThat(data.get(LEVEL)).isEqualTo(4);
  }
}
