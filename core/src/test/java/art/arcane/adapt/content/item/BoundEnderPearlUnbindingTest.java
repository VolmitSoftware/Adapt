package art.arcane.adapt.content.item;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
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

class BoundEnderPearlUnbindingTest {
  @Test
  void unbindingOnlyUpdatesPersistentBindingAndPreservesPresentation() {
    ItemRef item = mock(ItemRef.class);
    DataHolder container = mock(DataHolder.class);
    when(item.data()).thenReturn(container);
    BoundEnderPearl original = BoundEnderPearl.io;
    BoundEnderPearl replacement = spy(new BoundEnderPearl());
    BoundEnderPearl.Data data = new BoundEnderPearl.Data(mock(BlockRef.class));
    doReturn(data).when(replacement).getData(item);
    BoundEnderPearl.io = replacement;
    try {
      assertThat(BoundEnderPearl.clearBinding(item)).isTrue();
      assertThat(data.getBlock()).isNull();
      verify(container).set(eq(replacement.dataKey()), anyString());
      verify(item, never()).setDisplayName(anyString());
      verify(item, never()).setLore(any());
      assertThat(BoundEnderPearl.clearBinding(item)).isFalse();
    } finally {
      BoundEnderPearl.io = original;
    }
  }
}
