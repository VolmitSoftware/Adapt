package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SorterVariantsTest {
  @Test
  void storedEnchantmentsBecomeASortedKeyList() {
    assertThat(SorterVariants.keys(List.of(Id.minecraft("sharpness"), Id.minecraft("mending")))).isEqualTo("minecraft:mending,minecraft:sharpness");
    assertThat(SorterVariants.keys(List.of())).isEmpty();

    ItemRef stored = item(Id.minecraft("enchanted_book"));
    lenient().when(stored.storedEnchantments()).thenReturn(Map.of());
    assertThat(SorterVariants.of(stored)).isEmpty();
    verify(stored).storedEnchantments();
  }

  @Test
  void potionsAndTippedArrowsUseTheirBasePotion() {
    PotionContents swiftness = new PotionContents(Id.minecraft("swiftness"), null, List.of(), null);
    ItemRef potion = item(Id.minecraft("potion"));
    lenient().when(potion.potionContents()).thenReturn(Optional.of(swiftness));
    ItemRef arrow = item(Id.minecraft("tipped_arrow"));
    lenient().when(arrow.potionContents()).thenReturn(Optional.of(swiftness));
    ItemRef plain = item(Id.minecraft("splash_potion"));
    lenient().when(plain.potionContents()).thenReturn(Optional.of(PotionContents.EMPTY));

    assertThat(SorterVariants.of(potion)).isEqualTo("minecraft:swiftness");
    assertThat(SorterVariants.of(arrow)).isEqualTo("minecraft:swiftness");
    assertThat(SorterVariants.of(plain)).isEmpty();
  }

  @Test
  void otherItemsAndItemsWithoutMetaHaveNoVariant() {
    ItemRef sword = item(Id.minecraft("diamond_sword"));

    assertThat(SorterVariants.of(sword)).isEmpty();
    verify(sword, never()).storedEnchantments();
    verify(sword, never()).potionContents();
    ItemRef bare = item(Id.minecraft("potion"));
    lenient().when(bare.potionContents()).thenReturn(Optional.empty());
    assertThat(SorterVariants.of(bare)).isEmpty();
  }

  private static ItemRef item(Id type) {
    ItemRef item = mock(ItemRef.class);
    lenient().when(item.type()).thenReturn(type);
    return item;
  }
}
