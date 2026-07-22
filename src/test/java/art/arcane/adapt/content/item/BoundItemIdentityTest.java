package art.arcane.adapt.content.item;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoundItemIdentityTest extends AdaptTestBase {
  private static final List<BoundItemCase> BOUND_ITEMS = List.of(
      new BoundItemCase(Material.ENDER_PEARL, "Reliquary Portkey", BoundEnderPearl::isBindableItem),
      new BoundItemCase(Material.ENDER_EYE, "Ocular Anchor", BoundEyeOfEnder::isBindableItem),
      new BoundItemCase(Material.SNOWBALL, "Web Snare!", BoundSnowBall::isBindableItem),
      new BoundItemCase(Material.REDSTONE_TORCH, "Redstone Remote", BoundRedstoneTorch::isBindableItem)
  );

  @BeforeEach
  void configurePluginIdentity() {
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  @Test
  void persistentIdentitySurvivesRenamedAndLocalizedPresentation() {
    for (BoundItemCase boundItem : BOUND_ITEMS) {
      StubItem item = stubItem(boundItem.material(), true, "Renamed Item", List.of("Texto traducido"));

      assertThat(boundItem.detector().test(item.stack())).isTrue();
      verify(item.meta(), never()).getDisplayName();
      verify(item.meta(), never()).getLore();
    }
  }

  @Test
  void ordinaryItemsCannotSpoofIdentityWithEnglishPresentation() {
    for (BoundItemCase boundItem : BOUND_ITEMS) {
      StubItem item = stubItem(boundItem.material(), false, boundItem.englishName(), List.of(boundItem.englishName()));

      assertThat(boundItem.detector().test(item.stack())).isFalse();
      verify(item.meta(), never()).getDisplayName();
      verify(item.meta(), never()).getLore();
    }
  }

  @Test
  void malformedOrdinaryItemsAreRejectedWithoutReadingPresentation() {
    for (BoundItemCase boundItem : BOUND_ITEMS) {
      StubItem emptyLore = stubItem(boundItem.material(), false, "", List.of());
      ItemStack missingMeta = mock(ItemStack.class);
      ItemStack wrongMaterial = stubItem(Material.STONE, true, "", List.of()).stack();
      when(missingMeta.getType()).thenReturn(boundItem.material());
      when(missingMeta.getItemMeta()).thenReturn(null);

      assertThat(boundItem.detector().test(null)).isFalse();
      assertThat(boundItem.detector().test(missingMeta)).isFalse();
      assertThat(boundItem.detector().test(wrongMaterial)).isFalse();
      assertThat(boundItem.detector().test(emptyLore.stack())).isFalse();
      verify(emptyLore.meta(), never()).getLore();
    }
  }

  private StubItem stubItem(Material material, boolean hasData, String displayName, List<String> lore) {
    ItemStack stack = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(stack.getType()).thenReturn(material);
    when(stack.getItemMeta()).thenReturn(meta);
    when(meta.getPersistentDataContainer()).thenReturn(data);
    when(meta.getDisplayName()).thenReturn(displayName);
    when(meta.getLore()).thenReturn(lore);
    when(data.has(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(hasData);
    return new StubItem(stack, meta);
  }

  private record BoundItemCase(Material material, String englishName, Predicate<ItemStack> detector) {
  }

  private record StubItem(ItemStack stack, ItemMeta meta) {
  }
}
