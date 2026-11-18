package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiftPlainPearlTest {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");

  @Test
  void metaLessPearlIsPlain() {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(ENDER_PEARL);
    when(stack.amount()).thenReturn(1);
    when(stack.isEmpty()).thenReturn(false);
    when(stack.data()).thenReturn(mock(DataHolder.class));

    assertThat(RiftPearls.isPlainPearl(stack)).isTrue();
  }

  @Test
  void renamedPearlWithoutAdaptKeysIsStillPlain() {
    StubPearl pearl = pearlWithMeta();

    assertThat(RiftPearls.isPlainPearl(pearl.stack())).isTrue();
  }

  @Test
  void conduitTaglockPearlsAreNotPlain() {
    for (String keyName : List.of(RiftConduit.TAGLOCK_LOC_KEY_NAME, RiftConduit.TAGLOCK_ID_KEY_NAME)) {
      StubPearl pearl = pearlWithMeta();
      when(pearl.data().has(eq(DataKey.adapt(keyName, DataCodecs.STRING)))).thenReturn(true);

      assertThat(RiftPearls.isPlainPearl(pearl.stack())).isFalse();
    }
  }

  @Test
  void enderTaglockTaggedPearlIsNotPlain() {
    StubPearl pearl = pearlWithMeta();
    when(pearl.data().has(eq(DataKey.adapt(RiftEnderTaglock.TARGET_KEY_NAME, DataCodecs.STRING)))).thenReturn(true);

    assertThat(RiftPearls.isPlainPearl(pearl.stack())).isFalse();
  }

  @Test
  void boundEnderPearlIsNotPlain() {
    StubPearl pearl = pearlWithMeta();
    when(pearl.data().has(any())).thenReturn(true);

    assertThat(RiftPearls.isPlainPearl(pearl.stack())).isFalse();
  }

  @Test
  void missingWrongTypedAndEmptyStacksAreNotPlain() {
    ItemRef wrongMaterial = mock(ItemRef.class);
    when(wrongMaterial.type()).thenReturn(Id.minecraft("stone"));
    ItemRef emptyStack = mock(ItemRef.class);
    when(emptyStack.type()).thenReturn(ENDER_PEARL);
    when(emptyStack.amount()).thenReturn(0);

    assertThat(RiftPearls.isPlainPearl(null)).isFalse();
    assertThat(RiftPearls.isPlainPearl(wrongMaterial)).isFalse();
    assertThat(RiftPearls.isPlainPearl(emptyStack)).isFalse();
  }

  private StubPearl pearlWithMeta() {
    ItemRef stack = mock(ItemRef.class);
    DataHolder data = mock(DataHolder.class);
    when(stack.type()).thenReturn(ENDER_PEARL);
    when(stack.amount()).thenReturn(1);
    when(stack.isEmpty()).thenReturn(false);
    when(stack.displayName()).thenReturn("Renamed");
    when(stack.data()).thenReturn(data);
    return new StubPearl(stack, data);
  }

  private record StubPearl(ItemRef stack, DataHolder data) {
  }
}
