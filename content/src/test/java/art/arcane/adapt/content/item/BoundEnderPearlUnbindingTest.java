package art.arcane.adapt.content.item;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoundEnderPearlUnbindingTest {
  @Test
  void unbindingOnlyUpdatesPersistentBindingAndPreservesPresentation() {
    ItemRef item = mock(ItemRef.class);
    DataHolder container = mock(DataHolder.class);
    when(item.data()).thenReturn(container);
    when(item.type()).thenReturn(Id.minecraft("ender_pearl"));
    BoundEnderPearl.Data data = new BoundEnderPearl.Data(mock(BlockRef.class));
    when(container.has(any())).thenReturn(true);
    when(container.get(any())).thenReturn(data);

    assertThat(BoundEnderPearl.clearBinding(item)).isTrue();
    assertThat(data.getBlock()).isNull();
    verify(container).set(any(DataKey.class), eq(data));
    verify(item, never()).setDisplayName(any());
    verify(item, never()).setLore(any());
    assertThat(BoundEnderPearl.clearBinding(item)).isFalse();
  }
}
