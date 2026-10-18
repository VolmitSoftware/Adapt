package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SorterVariantsTest {
  @Test
  void storedEnchantmentsBecomeASortedKeyList() {
    assertThat(SorterVariants.keys(List.of(keyed("sharpness"), keyed("mending")))).isEqualTo("minecraft:mending,minecraft:sharpness");
    assertThat(SorterVariants.keys(List.of())).isEmpty();

    EnchantmentStorageMeta stored = mock(EnchantmentStorageMeta.class);
    lenient().when(stored.getStoredEnchants()).thenReturn(Map.of());
    assertThat(SorterVariants.of(item(Material.ENCHANTED_BOOK, stored))).isEmpty();
    verify(stored).getStoredEnchants();
  }

  @Test
  void potionsAndTippedArrowsUseTheirBasePotion() {
    PotionMeta swiftness = mock(PotionMeta.class);
    lenient().when(swiftness.getBasePotionType()).thenReturn(PotionType.SWIFTNESS);
    PotionMeta plain = mock(PotionMeta.class);

    assertThat(SorterVariants.of(item(Material.POTION, swiftness))).isEqualTo("minecraft:swiftness");
    assertThat(SorterVariants.of(item(Material.TIPPED_ARROW, swiftness))).isEqualTo("minecraft:swiftness");
    assertThat(SorterVariants.of(item(Material.SPLASH_POTION, plain))).isEmpty();
  }

  @Test
  void otherItemsAndItemsWithoutMetaHaveNoVariant() {
    ItemMeta meta = mock(ItemMeta.class);
    ItemStack sword = item(Material.DIAMOND_SWORD, meta);

    assertThat(SorterVariants.of(sword)).isEmpty();
    verify(sword, never()).getItemMeta();
    ItemStack bare = mock(ItemStack.class);
    lenient().when(bare.getType()).thenReturn(Material.POTION);
    assertThat(SorterVariants.of(bare)).isEmpty();
  }

  private static ItemStack item(Material type, ItemMeta meta) {
    ItemStack item = mock(ItemStack.class);
    lenient().when(item.getType()).thenReturn(type);
    lenient().when(item.hasItemMeta()).thenReturn(true);
    lenient().when(item.getItemMeta()).thenReturn(meta);
    return item;
  }

  private static Keyed keyed(String key) {
    Keyed keyed = mock(Keyed.class);
    lenient().when(keyed.getKey()).thenReturn(NamespacedKey.minecraft(key));
    return keyed;
  }
}
