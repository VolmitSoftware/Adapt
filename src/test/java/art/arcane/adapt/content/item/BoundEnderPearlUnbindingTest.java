package art.arcane.adapt.content.item;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoundEnderPearlUnbindingTest extends AdaptTestBase {
  @Test
  void unbindingOnlyUpdatesPersistentBindingAndPreservesPresentation() {
    when(plugin.namespace()).thenReturn("adapt");
    ItemStack item = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    PersistentDataContainer container = mock(PersistentDataContainer.class);
    when(item.getItemMeta()).thenReturn(meta);
    when(meta.getPersistentDataContainer()).thenReturn(container);
    BoundEnderPearl original = BoundEnderPearl.io;
    BoundEnderPearl replacement = spy(new BoundEnderPearl());
    BoundEnderPearl.Data data = new BoundEnderPearl.Data(mock(Block.class));
    doReturn(data).when(replacement).getData(item);
    BoundEnderPearl.io = replacement;
    try {
      assertThat(BoundEnderPearl.clearBinding(item)).isTrue();
      assertThat(data.getBlock()).isNull();
      verify(container).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
      verify(meta, never()).setDisplayName(anyString());
      verify(meta, never()).setLore(any());
      verify(item).setItemMeta(meta);
      assertThat(BoundEnderPearl.clearBinding(item)).isFalse();
    } finally {
      BoundEnderPearl.io = original;
    }
  }
}
